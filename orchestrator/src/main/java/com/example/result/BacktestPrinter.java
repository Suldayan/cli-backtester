package com.example.result;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;

@Slf4j
@Component
public class BacktestPrinter {
    // ---------- NUMBER FORMATTING ----------
    private static final DecimalFormat TWO_DEC = new DecimalFormat("#0.00");

    private static String pct(double v) {
        return TWO_DEC.format(v);
    }

    private static String num(double v) {
        return TWO_DEC.format(v);
    }

    // ---------- DYNAMIC COLOR HELPERS ----------
    private static String colorReturn(double v) {
        if (v > 0) return Ansi.GREEN.wrap(pct(v));
        if (v < 0) return Ansi.RED.wrap(pct(v));
        return pct(v);
    }

    private static String colorDrawdown(double v) {
        if (v > 20) return Ansi.RED.wrap(pct(v));
        if (v > 10) return Ansi.YELLOW.wrap(pct(v));
        return pct(v);
    }

    private static String colorSharpe(double v) {
        if (v > 1.0) return Ansi.GREEN.wrap(pct(v));
        if (v >= 0) return Ansi.YELLOW.wrap(pct(v));
        return Ansi.RED.wrap(pct(v));
    }

    private static String colorWinRate(double v) {
        if (v >= 50) return Ansi.GREEN.wrap(pct(v));
        return Ansi.RED.wrap(pct(v));
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
    public void print(final BacktestResult result) {
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
                pct(p.cagr()),
                num(p.netPnlAfterCosts()),

                // Risk header
                Ansi.BLUE.code, Ansi.RESET.code,
                Ansi.YELLOW.code, Ansi.RESET.code,

                colorDrawdown(r.maxDrawdownPct()),
                r.maxDrawdownDurationDays(),
                pct(r.volatility()),
                colorSharpe(r.sharpeRatio()),
                pct(r.sortinoRatio()),

                // Trades header
                Ansi.BLUE.code, Ansi.RESET.code,
                Ansi.YELLOW.code, Ansi.RESET.code,

                t.totalTrades(), t.wins(), t.losses(),
                colorWinRate(t.winRatePct()),
                num(t.avgWin()),
                num(t.avgLoss()),
                num(t.profitFactor()),

                // Execution header
                Ansi.BLUE.code, Ansi.RESET.code,
                Ansi.YELLOW.code, Ansi.RESET.code,

                num(e.totalSlippageCost()),
                num(e.totalFeesCost()),
                e.avgTradeDurationDays(),

                // Bottom border
                Ansi.CYAN.code, Ansi.RESET.code
        );
    }
}
