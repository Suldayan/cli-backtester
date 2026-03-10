package com.example.ingestion;

import java.lang.foreign.MemorySegment;
import java.util.Map;

public interface IngestionService {
    int processCSV(String path);
    MemorySegment candleBuffer();
    MemorySegment closeBuffer();
    Map<String, MemorySegment> indicatorBuffers();
}