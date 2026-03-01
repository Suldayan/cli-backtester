package com.example.ffi.functions;

import java.lang.foreign.*;

/*
* Registry for all function indicator types
* */
public final class NativeFunctions {
    public final MomentumFunctions momentum;

    public NativeFunctions(final SymbolLookup lib, final Linker linker) {
        // TODO Register more function types as library grows
        this.momentum = new MomentumFunctions(lib, linker);
    }
}

