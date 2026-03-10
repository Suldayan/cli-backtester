package com.example.ingestion.internal;

import com.example.arena.ArenaOps;
import com.example.ffi.layout.CandleMemory;
import com.example.ingestion.IngestionService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Gatherers;

@Slf4j
@Service
public class IngestionServiceImpl implements IngestionService {
    private final Arena arena;
    private final MemorySegment candleBuffer;
    private final MemorySegment closeBuffer;
    private final Map<String, MemorySegment> indicatorBuffers;

    private static final int BATCH_SIZE = 10_000;

    public IngestionServiceImpl() {
        this.arena = Arena.ofConfined();
        this.candleBuffer = arena.allocate(CandleMemory.LAYOUT, BATCH_SIZE);
        this.closeBuffer  = arena.allocate(ValueLayout.JAVA_DOUBLE, BATCH_SIZE);
        this.indicatorBuffers = Map.of(
                "SMA", arena.allocate(ValueLayout.JAVA_DOUBLE, BATCH_SIZE),
                "EMA", arena.allocate(ValueLayout.JAVA_DOUBLE, BATCH_SIZE),
                "ROC", arena.allocate(ValueLayout.JAVA_DOUBLE, BATCH_SIZE)
        );
    }

    @Override
    public int processCSV(final String path) {
        log.info("Loading CSV: {}", path);
        final int[] count = {0};

        try (final var lines = Files.lines(Path.of(path))) {
            lines.skip(1)
                    .gather(Gatherers.windowFixed(BATCH_SIZE))
                    .forEach(batch -> {
                        for (int i = 0; i < batch.size(); i++) {
                            writeLineToMemory(batch.get(i), candleBuffer, i);
                        }
                        // extract close prices into closeBuffer
                        ArenaOps.extractPrices(candleBuffer, closeBuffer, CandleMemory.CLOSE, batch.size());
                        count[0] += batch.size();
                    });
        } catch (IOException e) {
            throw new RuntimeException("Failed to read CSV: " + path, e);
        }

        log.info("Loaded {} bars", count[0]);
        return count[0];
    }

    @Override
    public MemorySegment candleBuffer() { return candleBuffer; }

    @Override
    public MemorySegment closeBuffer() { return closeBuffer; }

    @Override
    public Map<String, MemorySegment> indicatorBuffers() { return indicatorBuffers; }

    private void writeLineToMemory(final String line, final MemorySegment buffer, final int index) {
        final long offset = (long) index * CandleMemory.BYTE_SIZE;
        int start = 0;
        int field = 0;
        long timestamp = 0;
        double open = 0, high = 0, low = 0, close = 0, volume = 0;

        for (int i = 0; i <= line.length(); i++) {
            if (i == line.length() || line.charAt(i) == ',') {
                final String token = line.substring(start, i);
                switch (field) {
                    case 0 -> timestamp = LocalDate.parse(token).toEpochDay();
                    case 1 -> open      = Double.parseDouble(token);
                    case 2 -> high      = Double.parseDouble(token);
                    case 3 -> low       = Double.parseDouble(token);
                    case 4 -> close     = Double.parseDouble(token);
                    case 5 -> volume    = Double.parseDouble(token);
                }
                start = i + 1;
                field++;
            }
        }

        CandleMemory.TIMESTAMP.set(buffer, offset, timestamp);
        CandleMemory.OPEN.set(buffer, offset,      open);
        CandleMemory.HIGH.set(buffer, offset,      high);
        CandleMemory.LOW.set(buffer, offset,       low);
        CandleMemory.CLOSE.set(buffer, offset,     close);
        CandleMemory.VOLUME.set(buffer, offset,    volume);
    }

    @PreDestroy
    public void close() { arena.close(); }
}