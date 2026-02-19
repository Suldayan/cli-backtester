package com.example.ffi.functions;

import java.lang.foreign.*;

public final class NativeFunctions {
    public final MomentumFunctions momentum;

    public NativeFunctions(SymbolLookup lib, Linker linker) {
        // TODO Register more function types as library grows
        this.momentum = new MomentumFunctions(lib, linker);
    }
}

