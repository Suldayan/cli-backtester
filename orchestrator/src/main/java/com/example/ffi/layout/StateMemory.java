package com.example.ffi.layout;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

public class StateMemory {
    private static final String SUM_FIELD = "sum";
    private static final String COUNT_FIELD = "count";

    public static final GroupLayout LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_DOUBLE.withName(SUM_FIELD),
            ValueLayout.JAVA_LONG.withName(COUNT_FIELD)
    );

    public static final long BYTE_SIZE = LAYOUT.byteSize();
    public static final VarHandle SUM = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(SUM_FIELD));
    public static final VarHandle COUNT = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(COUNT_FIELD));
}
