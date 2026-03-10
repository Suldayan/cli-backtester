package com.example.backtest;

import com.example.ffi.bridge.NativeBridge;
import com.example.ffi.layout.CandleMemory;
import com.example.indicator.Indicator;
import com.example.indicator.IndicatorResolver;
import com.example.result.BacktestResult;
import com.example.result.EquityPoint;
import com.example.result.Trade;
import com.example.strategy.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class Backtester {
    private final NativeBridge nativeBridge;
    private final MetricsCalculator metricsCalculator;
    private final IndicatorResolver indicatorResolver;

    public Backtester(final NativeBridge nativeBridge, final MetricsCalculator metricsCalculator, IndicatorResolver indicatorResolver) {
        this.nativeBridge = nativeBridge;
        this.metricsCalculator = metricsCalculator;
        this.indicatorResolver = indicatorResolver;
    }

    public BacktestResult run(
            final MemorySegment candleBuffer,
            final MemorySegment closeBuffer,
            final Map<String, MemorySegment> indicatorBuffers,
            final int totalBars,
            final Strategy strategy) {

        final long startTime  = System.currentTimeMillis();
        final List<Indicator> indicators = indicatorResolver.resolveAll(strategy.openCondition());
        final MemorySegment[] buffers = indicators.stream()
                .map(i -> indicatorBuffers.get(i.name()))
                .toArray(MemorySegment[]::new);

        final List<Trade> trades = new ArrayList<>();
        final List<EquityPoint> equityCurve = new ArrayList<>();
        final Signal signal = new Signal();

        double  capital = strategy.execution().initialCapital();
        double  entryPrice = 0.0;
        LocalDate entryDate = null;
        boolean inPosition = false;
        LocalDate firstDate = null;
        LocalDate lastDate = null;

        // Run each indicator over the full candle buffer
        for (final Indicator indicator : indicators) {
            nativeBridge.execute(
                    indicator.handle(),
                    closeBuffer,
                    buffers[indicator.index()],
                    totalBars,
                    indicator.window()
            );
        }

        for (int i = 0; i < totalBars; i++) {
            final long candleOffset = (long) i * CandleMemory.BYTE_SIZE;
            final long indicatorOffset = (long) i * Double.BYTES;

            final double price = (double) CandleMemory.CLOSE.get(candleBuffer, candleOffset);
            final long tsEpoch = (long) CandleMemory.TIMESTAMP.get(candleBuffer, candleOffset);
            final LocalDate date = LocalDate.ofEpochDay(tsEpoch);

            if (firstDate == null) firstDate = date;
            lastDate = date;

            for (int j = 0; j < indicators.size(); j++) {
                signal.setIndicator(j, buffers[j].get(ValueLayout.JAVA_DOUBLE, indicatorOffset));
            }

            if (Double.isNaN(signal.indicator(0))) continue;

            // Update equity curve on every bar
            equityCurve.add(new EquityPoint(date, capital));

            final boolean openSignal  = strategy.openCondition().evaluate(signal);
            final boolean closeSignal = strategy.closeCondition().evaluate(signal);

            if (openSignal && !inPosition) {
                entryPrice  = price;
                entryDate   = date;
                inPosition  = true;
                log.info("BUY  @ {} | price: {} | indicators: {}",
                        date, String.format("%.2f", price), formatIndicators(indicators, signal));

            } else if (closeSignal && inPosition) {
                final Trade trade = buildTrade(
                        strategy, entryDate, entryPrice, date, price, capital
                );
                trades.add(trade);
                capital += trade.netPnl();
                inPosition = false;
                log.info("SELL @ {} | price: {} | indicators: {} | PnL: {}",
                        date, String.format("%.2f", price),
                        formatIndicators(indicators, signal),
                        String.format("%.2f", trade.netPnl()));
            }
        }

        final long computeTimeMs = System.currentTimeMillis() - startTime;

        return metricsCalculator.calculate(
                trades, equityCurve, strategy,
                firstDate, lastDate, totalBars, computeTimeMs
        );
    }

    private Trade buildTrade(
            final Strategy strategy,
            final LocalDate entryDate,
            final double entryPrice,
            final LocalDate exitDate,
            final double exitPrice,
            final double capital) {

        final double positionSize  = capital * (strategy.risk().positionSize() / 100.0);
        final double grossPnl = (exitPrice - entryPrice) / entryPrice * positionSize;
        final double slippageCost  = positionSize * (strategy.execution().slippagePct() / 100.0) * 2;
        final double feesCost = positionSize * (strategy.execution().feePct() / 100.0) * 2;
        final double netPnl = grossPnl - slippageCost - feesCost;

        return new Trade(
                strategy.symbol(),
                entryDate,
                exitDate,
                entryPrice,
                exitPrice,
                positionSize,
                grossPnl,
                slippageCost,
                feesCost,
                netPnl,
                netPnl > 0
        );
    }

    private String formatIndicators(final List<Indicator> indicators, final Signal signal) {
        final StringBuilder sb = new StringBuilder();
        for (final Indicator ind : indicators) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(ind.name())
                    .append("(").append(ind.window()).append(")")
                    .append(": ").append(String.format("%.2f", signal.indicator(ind.index())));
        }
        return sb.toString();
    }
}