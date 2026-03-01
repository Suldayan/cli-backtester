package com.example.ffi.functions;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public final class MomentumFunctions {
    public final MethodHandle sma;
    public final MethodHandle ema;
    public final MethodHandle roc;
    public final MethodHandle macd;
    public final MethodHandle stochastic;

    // (ADDRESS=prices, JAVA_LONG=len, JAVA_LONG=window, ADDRESS=out)
    private static final FunctionDescriptor SMA_DESC = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS
    );

    private static final FunctionDescriptor EMA_DESC = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS
    );

    private static final FunctionDescriptor ROC_DESC = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS
    );

    // (prices, len, fast, slow, signal, out_macd, out_signal, out_hist)
    private static final FunctionDescriptor MACD_DESC = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS
    );

    // (high, low, close, len, lookback, k_smoothing, d_smoothing, out_k, out_d)
    private static final FunctionDescriptor STOCHASTIC_DESC = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS
    );

    public MomentumFunctions(final SymbolLookup lib, final Linker linker) {
        this.sma = load(lib, linker, "compute_sma", SMA_DESC);
        this.ema = load(lib, linker, "compute_ema", EMA_DESC);
        this.roc = load(lib, linker, "compute_roc", ROC_DESC);
        this.macd = load(lib, linker, "compute_macd", MACD_DESC);
        this.stochastic = load(lib, linker, "compute_stochastic", STOCHASTIC_DESC);
    }

    private MethodHandle load(
            final SymbolLookup lib,
            final Linker linker,
            final String symbol,
            final FunctionDescriptor descriptor) {
        final MemorySegment address = lib.find(symbol)
                .orElseThrow(() -> new UnsatisfiedLinkError(
                        String.format("Missing native function: %s", symbol)
                ));
        return linker.downcallHandle(address, descriptor);
    }
}