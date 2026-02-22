package com.example.arena;

import com.example.ffi.layout.CandleMemory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

public final class ArenaOps {
    private ArenaOps() {}

    /**
     * Extracts a single field from a candle slab into a contiguous double array segment.
     * The destination segment must be pre-allocated with capacity >= count * JAVA_DOUBLE size.
     */
    public static void extractPrices(
            final MemorySegment candles,
            final MemorySegment dest,
            final VarHandle fieldHandle,
            final int count) {
        for (int i = 0; i < count; i++) {
            final long srcOffset = (long) i * CandleMemory.BYTE_SIZE;
            final long dstOffset = (long) i * ValueLayout.JAVA_DOUBLE.byteSize();
            final double value = (double) fieldHandle.get(candles, srcOffset);
            dest.set(ValueLayout.JAVA_DOUBLE, dstOffset, value);
        }
    }
}
