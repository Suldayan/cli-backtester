package com.example.ffi.bridge;

import com.example.ffi.functions.MomentumFunctions;
import com.example.ffi.functions.NativeFunctions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/*
* TODO:
*
Area	Opportunity	Impact
Arena usage	Avoid Arena.global()	Memory safety, lifetime control
MethodHandle	Pre‑bind arguments	Lower invocation overhead
Validation	Remove varargs + reduce checks	Zero allocations, faster hot path
Exception info	Add context	Better debugging
Spring	Avoid proxies or lazy load	More predictable startup
Library path	Make resolution robust	Portability
Varargs	Remove hidden allocations	GC reduction
* */

@Slf4j
@Component
public class NativeBridge {
    private final NativeFunctions functions;

    private static final String LIB_NAME = "engine";
    private static final String LIB_PATH = "../engine/target/release/";
    private static final String INVALID_MEMORY_ACCESS_ERR = "Invalid memory access attempted";

    public NativeBridge() {
        final String libName = System.mapLibraryName(LIB_NAME);
        final Path libPath = Path.of(LIB_PATH + libName).toAbsolutePath();
        final SymbolLookup lib = SymbolLookup.libraryLookup(libPath, Arena.global());
        final Linker linker = Linker.nativeLinker();

        this.functions = new NativeFunctions(lib, linker);
    }

    public MomentumFunctions getMomentumFunctions() {
        return functions.momentum;
    }

    public void execute(
            final MethodHandle handle,
            final MemorySegment state,
            final MemorySegment candles,
            final MemorySegment signals,
            final long count) {
        validate(state);
        validate(signals);
        validate(candles);
        try {
            handle.invoke(state, candles, signals, count);
        } catch (Throwable throwable) {
            throw new RuntimeException("CRITICAL: Native Engine Failure", throwable);
        }
    }

    private static void validate(final MemorySegment seg) {
        if (seg.address() == 0 || !seg.scope().isAlive()) {
            throw new IllegalStateException(INVALID_MEMORY_ACCESS_ERR);
        }
    }
}