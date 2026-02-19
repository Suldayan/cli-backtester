package com.example.ffi.functions;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public final class MomentumFunctions {
    public final MethodHandle sma;

    private static final String SMA_FUNCTION = "process_sma_batch";
    private static final FunctionDescriptor SMA_DESCRIPTOR = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG
    );

    public MomentumFunctions(final SymbolLookup lib, final Linker linker) {
        this.sma = load(lib, linker, SMA_FUNCTION, SMA_DESCRIPTOR);
        // TODO: add more momentum functions that are registered inside the rust engine
    }

    private MethodHandle load(final SymbolLookup lib, final Linker linker, final String symbol, final FunctionDescriptor descriptor) {
        final MemorySegment address = lib.find(symbol)
                .orElseThrow(() -> new UnsatisfiedLinkError(String.format("Missing native function: %s", symbol)));

        return linker.downcallHandle(address, descriptor);
    }

    public MethodHandle sma() {
        return sma;
    }
}
