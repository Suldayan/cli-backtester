package com.example.ffi.layout;

import java.lang.foreign.*;
import java.lang.invoke.VarHandle;

public class SignalMemory {
    private static final String ACTION_FIELD = "action";
    private static final String PRICE_FIELD = "price";
    private static final String INDICATOR_FIELD = "indicator";

    public static final GroupLayout LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName(ACTION_FIELD),      // 0 = Hold, 1 = Buy, -1 = Sell
            MemoryLayout.paddingLayout(4),
            ValueLayout.JAVA_DOUBLE.withName(PRICE_FIELD),    // Execution price
            ValueLayout.JAVA_DOUBLE.withName(INDICATOR_FIELD) // The calculated value
    );

    public static final long BYTE_SIZE = LAYOUT.byteSize();
    public static final VarHandle ACTION = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(ACTION_FIELD));
    public static final VarHandle PRICE = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(PRICE_FIELD));
    public static final VarHandle INDICATOR = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(INDICATOR_FIELD));
}