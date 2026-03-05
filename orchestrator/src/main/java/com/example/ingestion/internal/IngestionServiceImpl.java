package com.example.ingestion.internal;

import com.example.arena.ArenaOps;
import com.example.ffi.bridge.NativeBridge;
import com.example.ffi.layout.CandleMemory;
import com.example.ffi.layout.SignalMemory;
import com.example.indicator.Indicator;
import com.example.ingestion.IngestionService;
import com.example.strategy.*;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Gatherers;

@Slf4j
@Service
public class IngestionServiceImpl implements IngestionService {
    private final NativeBridge nativeBridge;
    private final Arena arena;
    private final Map<String, MemorySegment> indicatorBuffers;

    // Pre-allocated indicator, candle, and signal slabs
    private final MemorySegment candleBuffer;
    private final MemorySegment signalBuffer;
    private final MemorySegment indicatorBuffer;

    // Pre-allocated price extraction buffers — reused every batch, zero allocation on hot path
    private final MemorySegment closeBuffer;

    // TODO: configure when more indicators are supported
    private final MemorySegment highBuffer;
    private final MemorySegment lowBuffer;

    private static final int BATCH_SIZE = 10_000;

    public IngestionServiceImpl(final NativeBridge nativeBridge) {
        this.nativeBridge = nativeBridge;
        this.arena = Arena.ofConfined();

        this.candleBuffer = arena.allocate(CandleMemory.LAYOUT, BATCH_SIZE);
        this.signalBuffer = arena.allocate(SignalMemory.LAYOUT, BATCH_SIZE);
        this.indicatorBuffer = arena.allocate(ValueLayout.JAVA_DOUBLE, BATCH_SIZE);

        // One double per candle, pre-allocated once
        this.closeBuffer = arena.allocate(ValueLayout.JAVA_DOUBLE, BATCH_SIZE);
        this.highBuffer  = arena.allocate(ValueLayout.JAVA_DOUBLE, BATCH_SIZE);
        this.lowBuffer   = arena.allocate(ValueLayout.JAVA_DOUBLE, BATCH_SIZE);

        this.indicatorBuffers = Map.of(
                "SMA", arena.allocate(ValueLayout.JAVA_DOUBLE, BATCH_SIZE),
                "EMA", arena.allocate(ValueLayout.JAVA_DOUBLE, BATCH_SIZE),
                "ROC", arena.allocate(ValueLayout.JAVA_DOUBLE, BATCH_SIZE)
        );

        log.info("IngestionService buffers pre-allocated for batch size: {}", BATCH_SIZE);
    }

    @Override
    public void processCSV(final String path, final Strategy strategy) {
        log.info("Processing CSV: {} with strategy: {}", path, strategy.name());

        final List<Indicator> indicators = resolveAll(strategy.openCondition());
        final MemorySegment[] buffers    = indicators.stream()
                .map(i -> indicatorBuffers.get(i.name()))
                .toArray(MemorySegment[]::new);
        final Signal signal = new Signal();

        try (final var lines = Files.lines(Path.of(path))) {
            lines.skip(1)
                    .gather(Gatherers.windowFixed(BATCH_SIZE))
                    .forEach(batch -> processBatch(batch, indicators, buffers, signal, strategy));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read CSV: " + path, e);
        }
    }

    private void processBatch(
            final List<String> batch,
            final List<Indicator> indicators,
            final MemorySegment[] buffers,
            final Signal signal,
            final Strategy strategy) {

        final int size = batch.size();

        for (int i = 0; i < size; i++) {
            writeLineToMemory(batch.get(i), candleBuffer, i);
        }

        ArenaOps.extractPrices(candleBuffer, closeBuffer, CandleMemory.CLOSE, size);

        // Run each indicator sequentially into its own pre-allocated buffer
        for (final Indicator indicator : indicators) {
            nativeBridge.execute(
                    indicator.handle(),
                    closeBuffer,
                    buffers[indicator.index()],
                    (long) size,
                    (long) indicator.window()
            );
        }

        for (int i = 0; i < size; i++) {
            final long candleOffset    = (long) i * CandleMemory.BYTE_SIZE;
            final long signalOffset    = (long) i * SignalMemory.BYTE_SIZE;
            final long indicatorOffset = (long) i * Double.BYTES;

            final double price     = (double) CandleMemory.CLOSE.get(candleBuffer, candleOffset);
            final long   timestamp = (long)   CandleMemory.TIMESTAMP.get(candleBuffer, candleOffset);

            // Populate indicator slots on signal
            for (int j = 0; j < indicators.size(); j++) {
                signal.setIndicator(j, buffers[j].get(ValueLayout.JAVA_DOUBLE, indicatorOffset));
            }

            signal.read(signalBuffer, i);

            // Skip warmup — primary indicator not valid yet
            if (Double.isNaN(signal.indicator(0))) continue;

            // Write populated signal back to buffer
            SignalMemory.PRICE.set(signalBuffer, signalOffset, price);
            SignalMemory.TIMESTAMP.set(signalBuffer, signalOffset, timestamp);
            SignalMemory.SYMBOL_ID.set(signalBuffer, signalOffset, strategy.symbol().hashCode());

            if (strategy.openCondition().evaluate(signal)) {
                SignalMemory.ACTION.set(signalBuffer, signalOffset, 1);
                log.info("BUY  @ {} | price: {} | indicators: {}",
                        LocalDate.ofEpochDay(timestamp), price, formatIndicators(indicators, signal));
            } else if (strategy.closeCondition().evaluate(signal)) {
                SignalMemory.ACTION.set(signalBuffer, signalOffset, -1);
                log.info("SELL @ {} | price: {} | indicators: {}",
                        LocalDate.ofEpochDay(timestamp), price, formatIndicators(indicators, signal));
            } else {
                SignalMemory.ACTION.set(signalBuffer, signalOffset, 0);
            }
        }
    }

    private String formatIndicators(final List<Indicator> indicators, final Signal signal) {
        final StringBuilder sb = new StringBuilder();
        for (final Indicator ind : indicators) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(ind.name())
                    .append("(").append(ind.window()).append(")")
                    .append(": ").append(signal.indicator(ind.index()));
        }
        return sb.toString();
    }

    private void writeLineToMemory(
            final String line,
            final MemorySegment buffer,
            final int index) {
        // TODO: replace split() with manual char parser — eliminates String[] allocation
        final String[] parts = line.split(",");

        final long offset = (long) index * CandleMemory.BYTE_SIZE;

        CandleMemory.TIMESTAMP.set(buffer, offset, LocalDate.parse(parts[0]).toEpochDay());
        CandleMemory.OPEN.set(buffer, offset,      Double.parseDouble(parts[1]));
        CandleMemory.HIGH.set(buffer, offset,      Double.parseDouble(parts[2]));
        CandleMemory.LOW.set(buffer, offset,       Double.parseDouble(parts[3]));
        CandleMemory.CLOSE.set(buffer, offset,     Double.parseDouble(parts[4]));
        CandleMemory.VOLUME.set(buffer, offset,    Double.parseDouble(parts[5]));
    }

    private MethodHandle resolveHandle(final String indicator) {
        return switch (indicator.toUpperCase()) {
            case "SMA" -> nativeBridge.getMomentumFunctions().sma;
            case "EMA" -> nativeBridge.getMomentumFunctions().ema;
            case "ROC" -> nativeBridge.getMomentumFunctions().roc;
            default -> throw new IllegalArgumentException("Unsupported indicator: " + indicator);
        };
    }

    private List<Indicator> resolveAll(final StrategyCondition condition) {
        final List<Indicator> result = new ArrayList<>();
        resolveAllRecursive(condition, result);
        return result;
    }

    private void resolveAllRecursive(final StrategyCondition condition, final List<Indicator> result) {
        if (condition instanceof SimpleCondition simple) {
            final int index = result.size();
            result.add(new Indicator(
                    index,
                    simple.indicator(),
                    simple.period(),
                    resolveHandle(simple.indicator())
            ));
        } else if (condition instanceof CompositeCondition composite) {
            for (StrategyCondition child : composite.conditions()) {
                resolveAllRecursive(child, result);
            }
        }
    }

    @PreDestroy
    public void close() {
        arena.close();
    }
}