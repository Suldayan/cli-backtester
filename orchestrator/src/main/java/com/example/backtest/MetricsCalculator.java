package com.example.backtest;

import com.example.result.BacktestResult;
import com.example.result.EquityPoint;
import com.example.result.Trade;
import com.example.result.metrics.*;
import com.example.strategy.Strategy;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

@Component
public class MetricsCalculator {

    public BacktestResult calculate(
            final List<Trade> trades,
            final List<EquityPoint> equityCurve,
            final Strategy strategy,
            final LocalDate periodStart,
            final LocalDate periodEnd,
            final int barsProcessed,
            final long computeTimeMs) {

        final double initialCapital = strategy.execution().initialCapital();

        return new BacktestResult(
                buildPerformance(trades, equityCurve, initialCapital, periodStart, periodEnd),
                buildRisk(equityCurve, trades, strategy.execution().riskFreeRate()),
                buildTradeMetrics(trades),
                buildExecutionMetrics(trades),
                new BacktestMetadata(
                        strategy.symbol(),
                        periodStart,
                        periodEnd,
                        barsProcessed,
                        computeTimeMs
                )
        );
    }

    private Performance buildPerformance(
            final List<Trade> trades,
            final List<EquityPoint> equityCurve,
            final double initialCapital,
            final LocalDate start,
            final LocalDate end) {

        final double netPnl       = trades.stream().mapToDouble(Trade::netPnl).sum();
        final double finalEquity  = initialCapital + netPnl;
        final double totalReturn  = (netPnl / initialCapital) * 100.0;
        final double years        = ChronoUnit.DAYS.between(start, end) / 365.25;
        final double cagr         = years > 0
                ? (Math.pow(finalEquity / initialCapital, 1.0 / years) - 1.0) * 100.0
                : 0.0;

        return new Performance(totalReturn, cagr, netPnl + grossCosts(trades), netPnl, equityCurve);
    }

    private Risk buildRisk(
            final List<EquityPoint> equityCurve,
            final List<Trade> trades,
            final double riskFreeRate) {

        // Max drawdown
        double peak = Double.NEGATIVE_INFINITY;
        double maxDrawdown = 0.0;
        long maxDrawdownDuration = 0;
        long currentDrawdownStart = 0;

        for (int i = 0; i < equityCurve.size(); i++) {
            final double equity = equityCurve.get(i).equity();
            if (equity > peak) {
                peak = equity;
                currentDrawdownStart = i;
            }
            final double drawdown = (peak - equity) / peak * 100.0;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
                maxDrawdownDuration = i - currentDrawdownStart;
            }
        }

        // Volatility — std dev of daily returns
        final double[] returns = dailyReturns(equityCurve);
        final double volatility = standardDeviation(returns) * Math.sqrt(252) * 100.0;

        // Sharpe
        final double avgReturn  = Arrays.stream(returns).average().orElse(0.0);
        final double dailyRf    = riskFreeRate / 252.0;
        final double stdDev     = standardDeviation(returns);
        final double sharpe     = stdDev == 0 ? 0.0 : ((avgReturn - dailyRf) / stdDev) * Math.sqrt(252);

        // Sortino — only downside deviation
        final double[] negReturns    = Arrays.stream(returns).filter(r -> r < dailyRf).toArray();
        final double downsideDev     = standardDeviation(negReturns) * Math.sqrt(252);
        final double sortino         = downsideDev == 0 ? 0.0 : ((avgReturn - dailyRf) / (downsideDev / Math.sqrt(252)));

        return new Risk(maxDrawdown, maxDrawdownDuration, volatility, sharpe, sortino);
    }

    private TradeMetrics buildTradeMetrics(final List<Trade> trades) {
        if (trades.isEmpty()) {
            return new TradeMetrics(0, 0, 0, 0.0, 0.0, 0.0, 0.0);
        }

        final int wins    = (int) trades.stream().filter(Trade::isWin).count();
        final int losses  = trades.size() - wins;
        final double winRate = (double) wins / trades.size() * 100.0;

        final double avgWin = trades.stream()
                .filter(Trade::isWin)
                .mapToDouble(Trade::netPnl)
                .average().orElse(0.0);

        final double avgLoss = trades.stream()
                .filter(t -> !t.isWin())
                .mapToDouble(Trade::netPnl)
                .average().orElse(0.0);

        final double grossWins   = trades.stream().filter(Trade::isWin).mapToDouble(Trade::grossPnl).sum();
        final double grossLosses = trades.stream().filter(t -> !t.isWin()).mapToDouble(t -> Math.abs(t.grossPnl())).sum();
        final double profitFactor = grossLosses == 0 ? Double.POSITIVE_INFINITY : grossWins / grossLosses;

        return new TradeMetrics(trades.size(), wins, losses, winRate, avgWin, avgLoss, profitFactor);
    }

    private ExecutionMetrics buildExecutionMetrics(final List<Trade> trades) {
        final double totalSlippage = trades.stream().mapToDouble(Trade::slippageCost).sum();
        final double totalFees     = trades.stream().mapToDouble(Trade::feesCost).sum();
        final long avgDuration     = (long) trades.stream().mapToLong(Trade::durationDays).average().orElse(0);
        return new ExecutionMetrics(totalSlippage, totalFees, avgDuration);
    }

    private double[] dailyReturns(final List<EquityPoint> curve) {
        if (curve.size() < 2) return new double[0];
        final double[] returns = new double[curve.size() - 1];
        for (int i = 1; i < curve.size(); i++) {
            final double prev = curve.get(i - 1).equity();
            returns[i - 1] = prev == 0 ? 0.0 : (curve.get(i).equity() - prev) / prev;
        }
        return returns;
    }

    private double standardDeviation(final double[] values) {
        if (values.length == 0) return 0.0;
        final double mean = Arrays.stream(values).average().orElse(0.0);
        final double variance = Arrays.stream(values).map(v -> Math.pow(v - mean, 2)).average().orElse(0.0);
        return Math.sqrt(variance);
    }

    private double grossCosts(final List<Trade> trades) {
        return trades.stream().mapToDouble(t -> t.slippageCost() + t.feesCost()).sum();
    }
}