package com.example.ingestion;

import lombok.NonNull;

import java.lang.foreign.MemorySegment;
import java.util.List;

public interface IngestionService {
    void processCSV(final @NonNull String path);
    void writeLineToMemory(
            final String line,
            final MemorySegment buffer,
            final int index);
    void processBatch(final List<String> batch, final MemorySegment buffer);
}
