package com.example.ffi.layout;

import java.lang.foreign.*;
import java.lang.invoke.VarHandle;

public class SignalMemory {
    private static final String ACTION_FIELD = "action";
    private static final String SYMBOL_ID_FIELD = "symbol_id";
    private static final String TIMESTAMP_FIELD = "timestamp";
    private static final String PRICE_FIELD = "price";
    private static final String INDICATOR_FIELD = "indicator";

    public static final GroupLayout LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName(ACTION_FIELD),
            ValueLayout.JAVA_INT.withName(SYMBOL_ID_FIELD),
            ValueLayout.JAVA_LONG.withName(TIMESTAMP_FIELD),
            ValueLayout.JAVA_DOUBLE.withName(PRICE_FIELD),
            ValueLayout.JAVA_DOUBLE.withName(INDICATOR_FIELD)
    );

    public static final long BYTE_SIZE = LAYOUT.byteSize();
    public static final VarHandle ACTION = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(ACTION_FIELD));
    public static final VarHandle SYMBOL_ID = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(SYMBOL_ID_FIELD));
    public static final VarHandle TIMESTAMP = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(TIMESTAMP_FIELD));
    public static final VarHandle PRICE = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(PRICE_FIELD));
    public static final VarHandle INDICATOR = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(INDICATOR_FIELD));
}