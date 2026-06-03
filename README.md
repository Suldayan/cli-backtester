# cli-backtester

A command-line backtesting engine built with **Java 25** and **Rust**, using **Project Panama (JEP 454)** for zero-copy native interop. Define a strategy in JSON, point it at OHLCV data, get a full performance report back. Results are persisted to SQLite so runs accumulate over time and can be queried across strategy configurations.

```bash
./mvnw spring-boot:run --args="data/AAPL.csv strategies/rsi_strategy.json"
```

```
══════════════════════════════════════════════
 Backtest Completed - AAPL
 Period : 1999-12-31 -> 2026-03-09
 Bars   : 5284 | Duration: 7ms
──────────────────────────────────────────────
 Performance
   Total Return : 64.94%
   CAGR         : 1.93%
   Net PnL      : 6494.05
──────────────────────────────────────────────
 Risk
   Max Drawdown : 4.26%  (136 days)
   Volatility   : 5.48%
   Sharpe       : 0.46
   Sortino      : 0.01
──────────────────────────────────────────────
 Trades
   Total  : 66 | Wins: 49 | Losses: 17
   Win Rate     : 74.24%
   Avg Win      : 313.33 | Avg Loss: -521.11
   Profit Factor: 1.77
──────────────────────────────────────────────
 Execution Costs
   Slippage: 167.18 | Fees: 83.59
   Avg Duration: 1376 days
══════════════════════════════════════════════
```

Results are written to `backtester.db` after each run and can be queried across strategies:

```sql
SELECT strategy_name, MAX(sharpe_ratio) AS best_sharpe, AVG(net_pnl) AS avg_pnl
FROM backtest_runs
GROUP BY strategy_name
ORDER BY best_sharpe DESC;
```

---

## Why this project exists

Built to explore three things in depth:

- **Project Panama (JEP 454)** — calling Rust from Java without JNI, using `MemorySegment`, `VarHandle`, and `MethodHandle` to operate directly on off-heap memory with zero copying
- **Java/Rust interop** — designing a clean FFI boundary where Rust owns computation and Java owns strategy logic, with `#[repr(C)]` structs mirrored exactly in Java layouts
- **Modern Java features** — `Gatherers` (JEP 461) for streaming batch processing, records with compact constructor validation, pattern matching, and sealed interfaces

---

## Architecture

```
CLI args (csv path + strategy.json)
        │
        ▼
  TradingCommands       parse strategy.json → composite condition tree
        │
        ▼
  IngestionService      read CSV → write candles to native MemorySegment slab
        │
        ▼
  Backtester            resolve indicators → invoke Rust via MethodHandle
        │
        ▼
  Rust Engine           compute SMA / EMA / ROC / RSI over contiguous f64 slices
        │
        ▼
  Backtester            evaluate condition tree → collect round-trip trades → publish event
        │
        ├──▶  BacktestPrinter         formatted ANSI output
        └──▶  SqlitePersistenceService write to backtester.db
```

Publishing a `BacktestCompletedEvent` on completion means `Backtester` has one responsibility — compute. New consumers (CSV export, webhook, chart output) can be added without touching it.

### The FFI boundary

Java allocates `MemorySegment` slabs once at startup — one for candles, one per indicator type, one for close prices. These are passed directly to Rust as raw pointers. Rust reads and writes with no copies and no allocation. `invokeExact` is used over `invoke` to eliminate varargs boxing on every call.

```java
// Pre-allocated at construction time, reused every batch — zero allocation on hot path
this.candleBuffer = arena.allocate(CandleMemory.LAYOUT, BATCH_SIZE);
this.closeBuffer = arena.allocate(ValueLayout.JAVA_DOUBLE, BATCH_SIZE);
this.indicatorBuffers = Map.of(
    "SMA", arena.allocate(ValueLayout.JAVA_DOUBLE, BATCH_SIZE),
    "RSI", arena.allocate(ValueLayout.JAVA_DOUBLE, BATCH_SIZE)
    // ...
);
```

```java
// invokeExact — no varargs boxing, no allocation
handle.invokeExact(pricesSegment, (long) size, (long) window, outSegment);
```

```rust
// Rust receives raw pointers, creates zero-copy slices
pub unsafe extern "C" fn compute_rsi(
    prices_ptr: *const f64, len: usize, period: usize, out_ptr: *mut f64,
) {
    let prices = std::slice::from_raw_parts(prices_ptr, len);
    let out = std::slice::from_raw_parts_mut(out_ptr, len);
    calc_rsi(prices, period, out);
}
```

### Memory layouts

Java structs mirror Rust `#[repr(C)]` structs field-for-field. `VarHandle` accessors provide type-safe reads and writes with no object allocation per bar.

```java
// Java layout                          // Rust struct
MemoryLayout.structLayout(              #[repr(C)]
    JAVA_LONG.withName("timestamp"),    pub struct Candle {
    JAVA_DOUBLE.withName("open"),           pub timestamp: i64,
    JAVA_DOUBLE.withName("high"),           pub open: f64,
    JAVA_DOUBLE.withName("low"),            pub high: f64,
    JAVA_DOUBLE.withName("close"),          pub low: f64,
    JAVA_DOUBLE.withName("volume")          pub close: f64,
);                                          pub volume: f64,
                                        }
```

### Composite strategy

Strategies are defined in JSON and parsed into a recursive condition tree at startup. Conditions support arbitrary nesting of `AND`, `OR`, and `NOT`.

```json
{
  "name": "SMA + RSI Strategy",
  "open": {
    "type": "composite",
    "operator": "AND",
    "conditions": [
      { "type": "simple", "indicator": "SMA", "period": 10,
        "condition": "CROSSES_ABOVE", "target": { "value": 50.0 } },
      { "type": "simple", "indicator": "RSI", "period": 14,
        "condition": "CROSSES_BELOW", "target": { "value": 40.0 } }
    ]
  }
}
```

```java
public boolean evaluate(Signal signal) {
    return switch (operator) {
        case AND -> conditions.stream().allMatch(c -> c.evaluate(signal));
        case OR -> conditions.stream().anyMatch(c -> c.evaluate(signal));
        case NOT -> !conditions.getFirst().evaluate(signal);
    };
}
```

### Signal flyweight

`Signal` is a mutable cursor allocated once before the bar loop. It repositions over the candle buffer rather than allocating a new object per bar — one allocation for the entire backtest run regardless of dataset size.

```java
public final class Signal {
    private double price;
    private long timestamp;
    private final double[] indicators = new double[8]; // fixed size, no per-bar allocation

    public void setIndicator(final int index, final double value) {
        indicators[index] = value;
    }
}
```

---

## Supported indicators

| Indicator | Rust function | Notes |
|-----------|---------------|-------|
| SMA | `compute_sma` | Rolling window, running sum update |
| EMA | `compute_ema` | SMA-seeded, standard smoothing factor |
| ROC | `compute_roc` | Rate of change over N periods |
| RSI | `compute_rsi` | Wilder smoothing, bounded 0–100 |
| MACD | `compute_macd` | Implemented in Rust, Java wiring planned for v2 |
| Stochastic | `compute_stochastic` | Implemented in Rust, Java wiring planned for v2 |

---

## Strategy JSON format

```json
{
  "name": "Strategy name",
  "symbol": "AAPL",
  "open": {
    "type": "simple | composite",
    "operator": "AND | OR | NOT",
    "indicator": "SMA | EMA | ROC | RSI",
    "period": 14,
    "condition": "CROSSES_ABOVE | CROSSES_BELOW",
    "target": { "value": 40.0 }
  },
  "close": { "..." },
  "risk": {
    "stop_loss_pct": 2.0,
    "take_profit_pct": 5.0,
    "position_size_pct": 10.0
  },
  "execution": {
    "initial_capital": 10000.0,
    "slippage_pct": 0.1,
    "fee_pct": 0.05,
    "risk_free_rate": 0.0
  }
}
```

Targets can be a fixed scalar `{ "value": 40.0 }` or a reference to another indicator `{ "indicator": "SMA", "period": 50 }`.

---

## Persistence

Every run is written to `backtester.db` (SQLite, WAL mode) via an event listener. The schema captures the full result set — performance, risk, trade quality, execution costs, and the equity curve as a JSON blob.

```sql
-- Which strategy config performed best across all runs?
SELECT strategy_name, MAX(sharpe_ratio) AS best_sharpe, AVG(net_pnl) AS avg_pnl
FROM backtest_runs
GROUP BY strategy_name
ORDER BY best_sharpe DESC;

-- All profitable RSI runs
SELECT run_at, symbol, net_pnl, win_rate_pct, max_drawdown_pct
FROM backtest_runs
WHERE strategy_name LIKE '%RSI%'
  AND net_pnl > 0
ORDER BY net_pnl DESC;
```

Open `backtester.db` in [DB Browser for SQLite](https://sqlitebrowser.org/) to browse runs visually.

---

## Getting started

**Prerequisites:** Java 25+, Rust (stable), Maven

```bash
# Clone the repo
git clone https://github.com/yourusername/cli-backtester.git
cd cli-backtester

# Build everything — Rust compiles automatically via exec-maven-plugin
./mvnw.cmd package -pl orchestrator    # Windows
./mvnw package -pl orchestrator        # Mac / Linux

# Run a backtest using the included sample data

# Windows
./mvnw.cmd spring-boot:run -pl orchestrator "-Dspring-boot.run.arguments=samples/data/monthly_adjusted_IBM.csv samples/strategies/golden_cross.json"

# Mac / Linux
./mvnw spring-boot:run -pl orchestrator -Dspring-boot.run.arguments="samples/data/monthly_adjusted_IBM.csv samples/strategies/golden_cross.json"

# Run tests
./mvnw.cmd test -pl orchestrator       # Windows
./mvnw test -pl orchestrator           # Mac / Linux
```

Sample strategies included in `orchestrator/samples/strategies/`:

| Strategy | Indicators | Logic |
|----------|-----------|-------|
| `rsi_oversold.json` | RSI(14) | Buy oversold, sell overbought |
| `golden_cross.json` | SMA(50) | Classic momentum |
| `sma_rsi_confluence.json` | RSI(14) + SMA(20) | Composite AND / OR |
| `ema_momentum.json` | EMA(12) | EMA trend following |
## Project structure

```
orchestrator/src/main/java/com/example/
├── ffi/
│   ├── bridge/NativeBridge.java        # owns Arena lifetime, loads native lib
│   ├── functions/
│   │   ├── NativeFunctions.java        # registry — add new function groups here
│   │   └── MomentumFunctions.java      # MethodHandles for each Rust function
│   └── layout/
│       ├── CandleMemory.java           # GroupLayout + VarHandles matching Rust Candle
│       └── SignalMemory.java           # GroupLayout + VarHandles matching Rust Signal
├── arena/
│   └── ArenaOps.java                   # extract price fields from candle slab
├── indicator/
│   ├── Indicator.java                  # resolved handle + index + window
│   └── IndicatorResolver.java          # maps strategy conditions to MethodHandles
├── strategy/
│   ├── StrategyParser.java             # @FunctionalInterface
│   ├── Strategy.java                   # record
│   ├── StrategyCondition.java          # interface — evaluate(Signal)
│   ├── SimpleCondition.java            # leaf node
│   ├── CompositeCondition.java         # AND / OR / NOT, recursive evaluate
│   ├── Signal.java                     # flyweight cursor, fixed indicator array
│   ├── Target.java                     # scalar value or indicator reference
│   ├── RiskParameters.java             # record + compact constructor validation
│   ├── ExecutionParameters.java        # capital, slippage, fees, risk-free rate
│   └── internal/JsonStrategyParser.java
├── ingestion/
│   ├── IngestionService.java
│   └── internal/IngestionServiceImpl.java  # CSV -> native MemorySegment
├── backtest/
│   ├── Backtester.java                 # signal loop, trade building, event publish
│   ├── BacktestCompletedEvent.java     # domain event — result + strategy
│   └── MetricsCalculator.java         # performance, risk, trade, execution metrics
├── result/
│   ├── BacktestResult.java             # pure data record
│   ├── BacktestPrinter.java            # @EventListener — ANSI formatted output
│   ├── EquityPoint.java
│   ├── Trade.java                      # round-trip entry/exit with PnL
│   └── metrics/
│       ├── Performance.java
│       ├── Risk.java
│       ├── TradeMetrics.java
│       ├── ExecutionMetrics.java
│       └── BacktestMetadata.java
├── persistence/
│   ├── PersistenceConfig.java          # DataSource bean — HikariCP + SQLiteConfig
│   └── internal/
│       └── SqlitePersistenceService.java  # @EventListener — JdbcClient writes
└── cli/
    └── TradingCommands.java            # wires inputs, triggers run

engine/src/
├── lib.rs
├── ffi/ffi_momentum.rs                 # extern "C" wrappers — FFI boundary only
└── indicators/
    ├── moving_average.rs               # calc_sma, calc_ema, calc_rsi
    └── momentum.rs                     # calc_roc, calc_macd, calc_stochastic
```

---

## What I'd add next

- **MACD and Stochastic wiring** — multi-output buffer execution path on the Java side
- **Benchmark flag** — `--benchmark` mode printing tick throughput and memory stats with JMH, comparing Rust-backed indicators against a pure Java baseline
- **Valhalla value classes** — `Signal` is a natural `value class` candidate once JEP 401 stabilises, replacing the manual flyweight entirely
- **Multi-symbol support** — one pipeline per symbol, results aggregated across instruments
- **Chart output** — equity curve exported to SVG
