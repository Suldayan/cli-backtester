package com.example.ingestion.internal;

import com.example.arena.ArenaOps;
import com.example.ffi.bridge.NativeBridge;
import com.example.ffi.layout.CandleMemory;
import com.example.ffi.layout.SignalMemory;
import com.example.ingestion.IngestionService;
import com.example.strategy.Signal;
import com.example.strategy.SimpleCondition;
import com.example.strategy.Strategy;
import com.example.strategy.StrategyCondition;
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
import java.util.List;
import java.util.stream.Gatherers;

@Slf4j
@Service
public class IngestionServiceImpl implements IngestionService {
    private final NativeBridge nativeBridge;
    private final Arena arena;

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

        log.info("IngestionService buffers pre-allocated for batch size: {}", BATCH_SIZE);
    }

    @Override
    public void processCSV(final String path, final Strategy strategy) {
        log.info("Processing CSV: {} with strategy: {}", path, strategy.name());

        // Resolve once — not per batch
        final MethodHandle handle = resolveHandle(strategy.openCondition());
        final int window = resolveWindow(strategy.openCondition());
        final Signal signal = new Signal();

        try (final var lines = Files.lines(Path.of(path))) {
            lines.skip(1)
                    .gather(Gatherers.windowFixed(BATCH_SIZE))
                    .forEach(batch -> processBatch(batch, handle, window, signal, strategy));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read CSV: " + path, e);
        }
    }

    private void processBatch(
            final List<String> batch,
            final MethodHandle handle,
            final int window,
            final Signal signal,
            final Strategy strategy) {

        final int size = batch.size();

        for (int i = 0; i < size; i++) {
            writeLineToMemory(batch.get(i), candleBuffer, i);
        }

        ArenaOps.extractPrices(candleBuffer, closeBuffer, CandleMemory.CLOSE, size);

        // Rust writes SMA values into indicatorBuffer — plain f64 array
        nativeBridge.execute(handle, closeBuffer, indicatorBuffer, size, window);

        // Java reads price from candle slab, indicator from indicatorBuffer
        // and populates signalBuffer for condition evaluation
        for (int i = 0; i < size; i++) {
            final long candleOffset = (long) i * CandleMemory.BYTE_SIZE;
            final long signalOffset = (long) i * SignalMemory.BYTE_SIZE;
            final long indicatorOffset = (long) i * ValueLayout.JAVA_DOUBLE.byteSize();

            final double price = (double) CandleMemory.CLOSE.get(candleBuffer, candleOffset);
            final double indicator = indicatorBuffer.get(ValueLayout.JAVA_DOUBLE, indicatorOffset);
            final long   timestamp = (long) CandleMemory.TIMESTAMP.get(candleBuffer, candleOffset);

            // Write signal struct for condition evaluation
            SignalMemory.PRICE.set(signalBuffer, signalOffset, price);
            SignalMemory.INDICATOR.set(signalBuffer, signalOffset, indicator);
            SignalMemory.TIMESTAMP.set(signalBuffer, signalOffset, timestamp);
            SignalMemory.SYMBOL_ID.set(signalBuffer, signalOffset, strategy.symbol().hashCode());

            // Skip warmup period — SMA not valid yet
            if (Double.isNaN(indicator)) continue;

            signal.read(signalBuffer, i);

            if (strategy.openCondition().evaluate(signal)) {
                log.info("BUY  @ {} | price: {} | sma({}): {}",
                        LocalDate.ofEpochDay(timestamp), price, window, indicator);
            } else if (strategy.closeCondition().evaluate(signal)) {
                log.info("SELL @ {} | price: {} | sma({}): {}",
                        LocalDate.ofEpochDay(timestamp), price, window, indicator);
            }
        }
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

    /**
     * Walks the condition tree to find the first SimpleCondition
     * and maps its indicator name to the corresponding MethodHandle.
     * MACD and Stochastic require multiple output buffers and are not
     * supported in this execution path — handle separately when needed.
     */
    private MethodHandle resolveHandle(final StrategyCondition condition) {
        if (condition instanceof SimpleCondition simple) {
            log.debug("Supported indicator found: {}", ((SimpleCondition) condition).indicator());
            return switch (simple.indicator().toUpperCase()) {
                case "SMA" -> nativeBridge.getMomentumFunctions().sma;
                case "EMA" -> nativeBridge.getMomentumFunctions().ema;
                case "ROC" -> nativeBridge.getMomentumFunctions().roc;
                default -> throw new IllegalArgumentException(
                        String.format("Unsupported indicator: {}" + simple.indicator())
                );
            };
        }
        // Composite — recurse into first child to find the lead indicator
        if (condition instanceof com.example.strategy.CompositeCondition composite) {
            log.debug("Condition found as composite");
            return resolveHandle(composite.conditions().getFirst());
        }
        throw new IllegalArgumentException("Cannot resolve handle from condition: " + condition);
    }

    /**
     * Extracts the period/window from the first SimpleCondition in the tree.
     */
    private int resolveWindow(final StrategyCondition condition) {
        if (condition instanceof SimpleCondition simple) {
            return simple.period();
        }
        if (condition instanceof com.example.strategy.CompositeCondition composite) {
            return resolveWindow(composite.conditions().getFirst());
        }
        throw new IllegalArgumentException("Cannot resolve window from condition: " + condition);
    }

    @PreDestroy
    public void close() {
        arena.close();
    }
}