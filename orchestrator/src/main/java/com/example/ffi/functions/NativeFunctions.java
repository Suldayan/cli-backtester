package com.example.ffi.utils;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public final class NativeFunctions {
    public final MethodHandle sma;
    public final MethodHandle rsi;
    public final MethodHandle macd;

    public NativeFunctions(SymbolLookup lib, Linker linker) {
        sma = load(lib, linker, "process_sma_batch",
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG
                ));

        rsi = load(lib, linker, "process_rsi_batch",
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG
                ));

        macd = load(lib, linker, "process_macd_batch",
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG
                ));
    }

    private MethodHandle load(SymbolLookup lib, Linker linker, String name, FunctionDescriptor desc) {
        MemorySegment addr = lib.find(name)
                .orElseThrow(() -> new RuntimeException(String.format("Native function not found: %s", name)));
        return linker.downcallHandle(addr, desc);
    }
}
