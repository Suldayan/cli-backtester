package com.example.result;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BacktestPrinter {

    public void print(final BacktestResult result) {
        final var p = result.performance();
        final var r = result.risk();
        final var t = result.trades();
        final var e = result.execution();
        final var m = result.metadata();

        log.info("""
            ════════════════════════════════════════
             Backtest Completed - {}
             Period : {} -> {}
             Bars   : {} | Duration: {}ms
            ────────────────────────────────────────
             Performance
               Total Return : {}%
               CAGR         : {}%
               Net PnL      : {}
            ────────────────────────────────────────
             Risk
               Max Drawdown : {}%  ({} days)
               Volatility   : {}%
               Sharpe       : {}
               Sortino      : {}
            ────────────────────────────────────────
             Trades
               Total  : {} | Wins: {} | Losses: {}
               Win Rate     : {}%
               Avg Win      : {} | Avg Loss: {}
               Profit Factor: {}
            ────────────────────────────────────────
             Execution Costs
               Slippage: {} | Fees: {}
               Avg Duration: {} days
            ════════════════════════════════════════
            """,
                m.symbol(), m.periodStart(), m.periodEnd(),
                m.barsProcessed(), m.computeTimeMs(),
                String.format("%.2f", p.totalReturnPct()),
                String.format("%.2f", p.cagr()),
                String.format("%.2f", p.netPnlAfterCosts()),
                String.format("%.2f", r.maxDrawdownPct()),
                r.maxDrawdownDurationDays(),
                String.format("%.2f", r.volatility()),
                String.format("%.2f", r.sharpeRatio()),
                String.format("%.2f", r.sortinoRatio()),
                t.totalTrades(), t.wins(), t.losses(),
                String.format("%.2f", t.winRatePct()),
                String.format("%.2f", t.avgWin()),
                String.format("%.2f", t.avgLoss()),
                String.format("%.2f", t.profitFactor()),
                String.format("%.2f", e.totalSlippageCost()),
                String.format("%.2f", e.totalFeesCost()),
                e.avgTradeDurationDays()
        );
    }
}