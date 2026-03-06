package com.example.result;

import com.example.result.metrics.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record BacktestResult(
        Performance performance,
        Risk risk,
        TradeMetrics trades,
        ExecutionMetrics execution,
        BacktestMetadata metadata
) {
    // TODO configure a proper formatter class
    public void print() {
        log.info("""
            ════════════════════════════════════════
             Backtest Complete — {}
             Period : {} → {}
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
                metadata.symbol(),
                metadata.periodStart(), metadata.periodEnd(),
                metadata.barsProcessed(), metadata.computeTimeMs(),
                String.format("%.2f", performance.totalReturnPct()),
                String.format("%.2f", performance.cagr()),
                String.format("%.2f", performance.netPnlAfterCosts()),
                String.format("%.2f", risk.maxDrawdownPct()),
                risk.maxDrawdownDurationDays(),
                String.format("%.2f", risk.volatility()),
                String.format("%.2f", risk.sharpeRatio()),
                String.format("%.2f", risk.sortinoRatio()),
                trades.totalTrades(), trades.wins(), trades.losses(),
                String.format("%.2f", trades.winRatePct()),
                String.format("%.2f", trades.avgWin()),
                String.format("%.2f", trades.avgLoss()),
                String.format("%.2f", trades.profitFactor()),
                String.format("%.2f", execution.totalSlippageCost()),
                String.format("%.2f", execution.totalFeesCost()),
                execution.avgTradeDurationDays()
        );
    }
}
