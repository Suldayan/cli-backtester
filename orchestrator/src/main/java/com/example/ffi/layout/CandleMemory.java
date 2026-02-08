package com.example.ffi.layout;

import java.lang.foreign.*;
import java.lang.invoke.VarHandle;

public class CandleMemory {
    private static final String TIMESTAMP_FIELD = "timestamp";
    private static final String OPEN_FIELD = "open";
    private static final String HIGH_FIELD = "high";
    private static final String LOW_FIELD = "low";
    private static final String CLOSE_FIELD = "close";
    private static final String VOLUME_FIELD = "volume";

    public static final GroupLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_LONG.withName(TIMESTAMP_FIELD),
        ValueLayout.JAVA_DOUBLE.withName(OPEN_FIELD),
        ValueLayout.JAVA_DOUBLE.withName(HIGH_FIELD),
        ValueLayout.JAVA_DOUBLE.withName(LOW_FIELD),
        ValueLayout.JAVA_DOUBLE.withName(CLOSE_FIELD),
        ValueLayout.JAVA_DOUBLE.withName(VOLUME_FIELD)
    );

    public static final long BYTE_SIZE = LAYOUT.byteSize();
    public static final VarHandle TIMESTAMP = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(TIMESTAMP_FIELD));
    public static final VarHandle OPEN = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(OPEN_FIELD));
    public static final VarHandle HIGH = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(HIGH_FIELD));
    public static final VarHandle LOW = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(LOW_FIELD));
    public static final VarHandle CLOSE = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(CLOSE_FIELD));
    public static final VarHandle VOLUME = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(VOLUME_FIELD));
}