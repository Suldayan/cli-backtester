package com.example.orchestrator.ingestion;

import com.example.ingestion.internal.IngestionServiceImpl;
import com.example.utils.CandleMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class IngestionServiceImplTest {

    @InjectMocks
    private IngestionServiceImpl ingestionService;

    @Test
    void writeLineToMemory_shouldCorrectlyParseAndWriteCandle() {
        try (Arena arena = Arena.ofShared()) {
            MemorySegment buffer = arena.allocate(CandleMemory.LAYOUT, 1);
            String line = "2024-01-15,100.5,105.2,99.8,103.4,1500000";

            ingestionService.writeLineToMemory(line, buffer, 0);

            long expectedTimestamp = LocalDate.parse("2024-01-15").toEpochDay();
            assertEquals(expectedTimestamp,
                    (long) CandleMemory.TIMESTAMP.get(buffer, 0L));
            assertEquals(100.5,
                    (double) CandleMemory.OPEN.get(buffer, 0L), 0.001);
            assertEquals(105.2,
                    (double) CandleMemory.HIGH.get(buffer, 0L), 0.001);
            assertEquals(99.8,
                    (double) CandleMemory.LOW.get(buffer, 0L), 0.001);
            assertEquals(103.4,
                    (double) CandleMemory.CLOSE.get(buffer, 0L), 0.001);
            assertEquals(1500000,
                    (double) CandleMemory.VOLUME.get(buffer, 0L), 0.001);
        }
    }

    @Test
    void processBatch_shouldWriteAllLinesToBuffer() {
        try (Arena arena = Arena.ofShared()) {
            MemorySegment buffer = arena.allocate(CandleMemory.LAYOUT, 3);
            List<String> batch = List.of(
                    "2024-01-15,100.5,105.2,99.8,103.4,1500000",
                    "2024-01-16,103.4,108.1,102.5,107.0,1600000",
                    "2024-01-17,107.0,110.5,106.0,109.2,1700000"
            );

            ingestionService.processBatch(batch, buffer);

            long offset1 = CandleMemory.LAYOUT.byteSize();
            long offset2 = 2 * CandleMemory.LAYOUT.byteSize();

            assertEquals(100.5,
                    (double) CandleMemory.OPEN.get(buffer, 0L), 0.001);
            assertEquals(103.4,
                    (double) CandleMemory.OPEN.get(buffer, offset1), 0.001);
            assertEquals(107.0,
                    (double) CandleMemory.OPEN.get(buffer, offset2), 0.001);
        }
    }
}