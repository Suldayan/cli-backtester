package com.example.result;

import com.example.backtest.BacktestCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BacktestPrinter {

    @EventListener
    public void onBacktestCompleted(final BacktestCompletedEvent event) {
        print(event.result());
    }

    // ---------- NUMBER FORMATTING ----------
    private static String fmt(final double v) {
        return String.format("%.2f", v);
    }

    // ---------- DYNAMIC COLOR HELPERS ----------
    private static String colorReturn(final double v) {
        if (v > 0) return Ansi.GREEN.wrap(fmt(v));
        if (v < 0) return Ansi.RED.wrap(fmt(v));
        return fmt(v);
    }

    private static String colorDrawdown(final double v) {
        if (v > 20) return Ansi.RED.wrap(fmt(v));
        if (v > 10) return Ansi.YELLOW.wrap(fmt(v));
        return fmt(v);
    }

    private static String colorSharpe(final double v) {
        if (v > 1.0) return Ansi.GREEN.wrap(fmt(v));
        if (v >= 0) return Ansi.YELLOW.wrap(fmt(v));
        return Ansi.RED.wrap(fmt(v));
    }

    private static String colorWinRate(final double v) {
        if (v >= 50) return Ansi.GREEN.wrap(fmt(v));
        return Ansi.RED.wrap(fmt(v));
    }

    // ---------- TEMPLATE ----------
    private static final String TEMPLATE = """
        {}══════════════════════════════════════════════{}
         {}Backtest Completed{} - {}
         Period : {} -> {}
         Bars   : {} | Duration: {}ms
        {}──────────────────────────────────────────────{}
         {}Performance{}
           Total Return : {}%
           CAGR         : {}%
           Net PnL      : {}
        {}──────────────────────────────────────────────{}
         {}Risk{}
           Max Drawdown : {}%  ({} days)
           Volatility   : {}%
           Sharpe       : {}
           Sortino      : {}
        {}──────────────────────────────────────────────{}
         {}Trades{}
           Total  : {} | Wins: {} | Losses: {}
           Win Rate     : {}%
           Avg Win      : {} | Avg Loss: {}
           Profit Factor: {}
        {}──────────────────────────────────────────────{}
         {}Execution Costs{}
           Slippage: {} | Fees: {}
           Avg Duration: {} days
        {}══════════════════════════════════════════════{}
        """;

    // ---------- MAIN PRINT METHOD ----------
    private void print(final BacktestResult result) {
        final var p = result.performance();
        final var r = result.risk();
        final var t = result.trades();
        final var e = result.execution();
        final var m = result.metadata();

        log.info(TEMPLATE,
                // Top border
                Ansi.CYAN.code, Ansi.RESET.code,
                // Title
                Ansi.GREEN.code, Ansi.RESET.code,

                m.symbol(), m.periodStart(), m.periodEnd(),
                m.barsProcessed(), m.computeTimeMs(),

                // Performance header
                Ansi.BLUE.code, Ansi.RESET.code,
                Ansi.YELLOW.code, Ansi.RESET.code,

                colorReturn(p.totalReturnPct()),
                fmt(p.cagr()),
                fmt(p.netPnlAfterCosts()),

                // Risk header
                Ansi.BLUE.code, Ansi.RESET.code,
                Ansi.YELLOW.code, Ansi.RESET.code,

                colorDrawdown(r.maxDrawdownPct()),
                r.maxDrawdownDurationDays(),
                fmt(r.volatility()),
                colorSharpe(r.sharpeRatio()),
                fmt(r.sortinoRatio()),

                // Trades header
                Ansi.BLUE.code, Ansi.RESET.code,
                Ansi.YELLOW.code, Ansi.RESET.code,

                t.totalTrades(), t.wins(), t.losses(),
                colorWinRate(t.winRatePct()),
                fmt(t.avgWin()),
                fmt(t.avgLoss()),
                fmt(t.profitFactor()),

                // Execution header
                Ansi.BLUE.code, Ansi.RESET.code,
                Ansi.YELLOW.code, Ansi.RESET.code,

                fmt(e.totalSlippageCost()),
                fmt(e.totalFeesCost()),
                e.avgTradeDurationDays(),

                // Bottom border
                Ansi.CYAN.code, Ansi.RESET.code
        );
    }
}