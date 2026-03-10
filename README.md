# cli-backtester

A command-line backtesting engine built with **Java 25** and **Rust**, using **Project Panama (JEP 454)** for zero-copy native interop. Define a strategy in JSON, point it at OHLCV data, get a full performance report back.

```bash
./mvnw spring-boot:run --args="data/AAPL.csv strategy.json"
```

```
INFO : Strategy loaded: RSI Oversold Strategy
INFO : BUY  @ 2022-09-30 | price: 138.20 | RSI(14): 38.45
INFO : SELL @ 2022-11-30 | price: 148.11 | RSI(14): 61.23
INFO : BUY  @ 2023-03-31 | price: 131.09 | RSI(14): 37.89
INFO : SELL @ 2023-05-31 | price: 128.59 | RSI(14): 62.10
...
INFO :
════════════════════════════════════════
 Backtest Completed - AAPL
 Period : 2014-12-31 -> 2025-05-30
 Bars   : 125 | Duration: 43ms
────────────────────────────────────────
 Performance
   Total Return : 18.42%
   CAGR         : 1.72%
   Net PnL      : 1842.30
────────────────────────────────────────
 Risk
   Max Drawdown : 6.21%  (14 days)
   Volatility   : 12.34%
   Sharpe       : 0.87
   Sortino      : 1.12
────────────────────────────────────────
 Trades
   Total  : 12 | Wins: 7 | Losses: 5
   Win Rate     : 58.33%
   Avg Win      : 412.50 | Avg Loss: -187.30
   Profit Factor: 2.20
────────────────────────────────────────
 Execution Costs
   Slippage: 24.50 | Fees: 12.25
   Avg Duration: 47 days
════════════════════════════════════════
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
  Commands              parse strategy.json → composite condition tree
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
  Backtester            evaluate condition tree → collect round-trip trades
        │
        ▼
  MetricsCalculator     compute performance, risk, and trade metrics
        │
        ▼
  BacktestPrinter       format and output results
```

### The FFI boundary

Java allocates `MemorySegment` slabs once at startup — one for candles, one per indicator type, one for close prices. These are passed directly to Rust as raw pointers. Rust reads and writes with no copies and no allocation. `invokeExact` is used over `invoke` to avoid varargs boxing on every native call.

```java
// Pre-allocated at construction time, reused every batch — zero allocation on hot path
this.candleBuffer    = arena.allocate(CandleMemory.LAYOUT, BATCH_SIZE);
this.closeBuffer     = arena.allocate(ValueLayout.JAVA_DOUBLE, BATCH_SIZE);
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
    let out    = std::slice::from_raw_parts_mut(out_ptr, len);
    calc_rsi(prices, period, out);
}
```

### Memory layouts

Java structs mirror Rust `#[repr(C)]` structs field-for-field. `VarHandle` accessors provide type-safe field reads and writes with no object allocation.

```java
// Java                                    // Rust
public static final GroupLayout LAYOUT =   #[repr(C)]
    MemoryLayout.structLayout(             pub struct Candle {
        JAVA_LONG.withName("timestamp"),       pub timestamp: i64,
        JAVA_DOUBLE.withName("open"),          pub open:      f64,
        JAVA_DOUBLE.withName("high"),          pub high:      f64,
        JAVA_DOUBLE.withName("low"),           pub low:       f64,
        JAVA_DOUBLE.withName("close"),         pub close:     f64,
        JAVA_DOUBLE.withName("volume")         pub volume:    f64,
    );                                     }
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
        "condition": "CROSSES_ABOVE", "target": { "value": 150.0 } },
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
        case OR  -> conditions.stream().anyMatch(c -> c.evaluate(signal));
        case NOT -> !conditions.getFirst().evaluate(signal);
    };
}
```

### Signal flyweight

`Signal` is a mutable cursor that repositions over the signal buffer rather than allocating a new object per bar — one allocation for the entire backtest run.

```java
public final class Signal {
    private double price;
    private long timestamp;
    private int action;
    private final double[] indicators = new double[8]; // fixed size, no allocation

    public void setIndicator(final int index, final double value) {
        indicators[index] = value;
    }
}
```

---

## Supported indicators

| Indicator | Rust function | Notes |
|-----------|--------------|-------|
| SMA | `compute_sma` | Rolling window, running sum update |
| EMA | `compute_ema` | SMA-seeded, standard alpha |
| ROC | `compute_roc` | Rate of change over N periods |
| RSI | `compute_rsi` | Wilder smoothing, 0-100 bounded |
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

## Getting started

**Prerequisites:** Java 25+, Rust (stable), Maven

```bash
# 1. Build the Rust engine
cd engine && cargo build --release

# 2. Run a backtest
./mvnw spring-boot:run --args="path/to/OHLCV.csv path/to/strategy.json"

# 3. Run tests (requires Rust library compiled first)
./mvnw clean test
```

CSV format expected:
```
timestamp,open,high,low,close,volume
2025-05-30,189.23,192.45,188.10,191.67,45231000
```

---

## Project structure

```
orchestrator/src/main/java/com/example/
├── ffi/
│   ├── bridge/NativeBridge.java        # owns Arena lifetime, loads .so, exposes functions
│   ├── functions/
│   │   ├── NativeFunctions.java        # registry — add new function groups here
│   │   └── MomentumFunctions.java      # MethodHandles for each Rust function
│   └── layout/
│       ├── CandleMemory.java           # GroupLayout + VarHandles matching Rust Candle
│       └── SignalMemory.java           # GroupLayout + VarHandles matching Rust Signal
├── arena/
│   └── ArenaOps.java                   # extract price fields from candle slab
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
│   ├── Indicator.java                  # resolved handle + index + window
│   └── internal/StrategyParserImpl.java
├── ingestion/
│   ├── IngestionService.java
│   └── internal/IngestionServiceImpl.java  # CSV -> native MemorySegment
├── backtest/
│   ├── Backtester.java                 # signal evaluation loop, trade building
│   └── MetricsCalculator.java          # performance, risk, trade, execution metrics
├── result/
│   ├── BacktestResult.java             # pure data record
│   ├── BacktestPrinter.java            # formatted output
│   ├── EquityPoint.java
│   ├── Trade.java                      # round-trip entry/exit with PnL
│   └── metrics/
│       ├── Performance.java
│       ├── Risk.java
│       ├── TradeMetrics.java
│       ├── ExecutionMetrics.java
│       └── BacktestMetadata.java
└── cli/
    └── Commands.java                   # wires everything, owns the run lifecycle

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
- **Valhalla value classes** — `Signal` is a natural `value class` candidate once JEP 401 stabilises, replacing the manual flyweight pattern
- **Multi-symbol support** — one pipeline per symbol, results aggregated across instruments
- **Chart output** — equity curve exported to SVG or rendered in terminal
- **Live mode** — poll a market data API on a timer, evaluate strategy in real time
