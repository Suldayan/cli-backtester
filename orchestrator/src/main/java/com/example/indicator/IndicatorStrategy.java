package com.example.indicator;

import java.lang.foreign.MemorySegment;

public interface IndicatorStrategy {
    String name();
    void execute(
            MemorySegment state,
            MemorySegment candles,
            MemorySegment signals,
            int count
    );
}
