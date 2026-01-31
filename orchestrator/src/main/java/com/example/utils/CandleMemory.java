package com.example.utils;

import java.lang.foreign.*;
import java.lang.invoke.VarHandle;

public class CandleMemory {
    public static final GroupLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_LONG.withName("timestamp"), 
        ValueLayout.JAVA_DOUBLE.withName("open"),    
        ValueLayout.JAVA_DOUBLE.withName("high"),    
        ValueLayout.JAVA_DOUBLE.withName("low"),     
        ValueLayout.JAVA_DOUBLE.withName("close"),   
        ValueLayout.JAVA_DOUBLE.withName("volume")   
    );

    public static final VarHandle TIMESTAMP = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("timestamp"));
    public static final VarHandle OPEN = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("open"));
    public static final VarHandle HIGH = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("high"));
    public static final VarHandle LOW = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("low"));
    public static final VarHandle CLOSE = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("close"));
    public static final VarHandle VOLUME = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("volume"));
}