package com.example.ingestion.internal;

import com.example.ingestion.IngestionService;
import com.example.utils.CandleMemory;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Gatherers;

@Service
@Slf4j
public class IngestionServiceImpl implements IngestionService {
    private static final int BATCH_SIZE = 10_000;

    @Override
    public void processCSV(final @NonNull String path) {
        log.info("Processing CSV with path: {}", path);

        try (final Arena arena = Arena.ofShared()) {
            log.debug("Creating memory segment for candles with batch size: {}", BATCH_SIZE);
            final MemorySegment candleBuffer = arena.allocate(CandleMemory.LAYOUT, BATCH_SIZE);

            try (final var lines = Files.lines(Path.of(path))) {
                lines.skip(1)
                        .gather(Gatherers.windowFixed(BATCH_SIZE))
                        .forEach(batch -> processBatch(batch, candleBuffer));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void writeLineToMemory(
            final String line,
            final MemorySegment buffer,
            final int index) {
        // TODO: make manual parser for split
        final String[] parts = line.split(",");

        final long ts = LocalDate.parse(parts[0]).toEpochDay();
        final double open = Double.parseDouble(parts[1]);
        final double high = Double.parseDouble(parts[2]);
        final double low = Double.parseDouble(parts[3]);
        final double close = Double.parseDouble(parts[4]);
        final double vol = Double.parseDouble(parts[5]);

        final long offset = (long) index * CandleMemory.LAYOUT.byteSize();

        CandleMemory.TIMESTAMP.set(buffer, offset, ts);
        CandleMemory.OPEN.set(buffer, offset, open);
        CandleMemory.HIGH.set(buffer, offset, high);
        CandleMemory.LOW.set(buffer, offset, low);
        CandleMemory.CLOSE.set(buffer, offset, close);
        CandleMemory.VOLUME.set(buffer, offset, vol);
    }

    @Override
    public void processBatch(final List<String> batch, final MemorySegment buffer) {
        final int SIZE = batch.size();
        for (int i = 0; i < SIZE; i++) {
            writeLineToMemory(batch.get(i), buffer, i);
        }

        log.debug("Sending batch of {} to Rust", SIZE);
        // nativeBridge.processCandleBatch(buffer, batch.size());
    }
}

