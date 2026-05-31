package com.example.persistence.internal;

import com.example.backtest.BacktestCompletedEvent;
import com.example.result.BacktestResult;
import com.example.strategy.Strategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersistenceService {
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    @EventListener
    @Transactional
    public void onBacktestCompleted(final BacktestCompletedEvent event) {
        persist(event.result(), event.strategy());
    }

    private void persist(final BacktestResult result, final Strategy strategy) {
        final var m = result.metadata();
        final var p = result.performance();
        final var r = result.risk();
        final var t = result.trades();
        final var e = result.execution();

        jdbcClient.sql("""
            INSERT INTO backtest_runs (
                run_at, strategy_name, strategy_config, symbol,
                period_start, period_end, bars_processed, runtime_ms,
                total_trades, wins, losses, win_rate_pct,
                avg_win, avg_loss, profit_factor,
                net_pnl, total_return_pct, cagr,
                sharpe_ratio, sortino_ratio, max_drawdown_pct,
                max_drawdown_duration_days, volatility,
                total_slippage_cost, total_fees_cost,
                avg_trade_duration_days, equity_curve
            ) VALUES (
                :runAt, :strategyName, :strategyConfig, :symbol,
                :periodStart, :periodEnd, :barsProcessed, :runtimeMs,
                :totalTrades, :wins, :losses, :winRatePct,
                :avgWin, :avgLoss, :profitFactor,
                :netPnl, :totalReturnPct, :cagr,
                :sharpeRatio, :sortinoRatio, :maxDrawdownPct,
                :maxDrawdownDurationDays, :volatility,
                :totalSlippageCost, :totalFeesCost,
                :avgTradeDurationDays, :equityCurve
            )
            """)
                .param("runAt",                    Instant.now().toString())
                .param("strategyName",             strategy.name())
                .param("strategyConfig",           toStrategyJson(strategy))
                .param("symbol",                   m.symbol())
                .param("periodStart",              m.periodStart() != null ? m.periodStart().toString() : null)
                .param("periodEnd",                m.periodEnd() != null ? m.periodEnd().toString() : null)
                .param("barsProcessed",            m.barsProcessed())
                .param("runtimeMs",                m.computeTimeMs())
                .param("totalTrades",              t.totalTrades())
                .param("wins",                     t.wins())
                .param("losses",                   t.losses())
                .param("winRatePct",               t.winRatePct())
                .param("avgWin",                   t.avgWin())
                .param("avgLoss",                  t.avgLoss())
                .param("profitFactor",             t.profitFactor())
                .param("netPnl",                   p.netPnlAfterCosts())
                .param("totalReturnPct",           p.totalReturnPct())
                .param("cagr",                     p.cagr())
                .param("sharpeRatio",              r.sharpeRatio())
                .param("sortinoRatio",             r.sortinoRatio())
                .param("maxDrawdownPct",           r.maxDrawdownPct())
                .param("maxDrawdownDurationDays",  r.maxDrawdownDurationDays())
                .param("volatility",               r.volatility())
                .param("totalSlippageCost",        e.totalSlippageCost())
                .param("totalFeesCost",            e.totalFeesCost())
                .param("avgTradeDurationDays",     e.avgTradeDurationDays())
                .param("equityCurve",              toEquityCurveJson(result))
                .update();

        log.info("Run persisted — strategy: {} | symbol: {}", strategy.name(), m.symbol());
    }

    private String toStrategyJson(final Strategy strategy) {
        return objectMapper.writeValueAsString(Map.of(
                "name", strategy.name(),
                "symbol", strategy.symbol(),
                "risk", strategy.risk(),
                "execution", strategy.execution()
        ));
    }

    private String toEquityCurveJson(final BacktestResult result) {
        return objectMapper.writeValueAsString(result.performance().equityCurve());
    }
}