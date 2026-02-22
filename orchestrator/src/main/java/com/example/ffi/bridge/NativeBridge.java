package com.example.ffi.bridge;

import com.example.ffi.functions.MomentumFunctions;
import com.example.ffi.functions.NativeFunctions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

@Slf4j
@Component
public final class NativeBridge implements AutoCloseable {
    private final Arena arena;
    private final NativeFunctions functions;

    private static final String LIB_NAME = "engine";
    private static final String LIB_PATH = "../engine/target/release/";

    public NativeBridge() {
        this.arena = Arena.ofConfined();
        log.info("Loading native library: {}", LIB_NAME);

        final String libName = System.mapLibraryName(LIB_NAME);
        final Path libPath = Path.of(LIB_PATH + libName).toAbsolutePath();

        final SymbolLookup lib = SymbolLookup.libraryLookup(libPath, arena);
        final Linker linker = Linker.nativeLinker();

        this.functions = new NativeFunctions(lib, linker);
        log.info("Native bridge initialized successfully");
    }

    public void execute(
            final MethodHandle handle,
            final MemorySegment state,
            final MemorySegment candles,
            final MemorySegment signals,
            final long count) {
        validateSegments(state, candles, signals);
        try {
            handle.invokeExact(state, candles, signals, count);
        } catch (Throwable t) {
            throw new RuntimeException("CRITICAL: Native engine failure", t);
        }
    }

    private void validateSegments(MemorySegment... segments) {
        if (!arena.scope().isAlive()) {
            throw new IllegalStateException("Native bridge arena is no longer alive");
        }
        for (MemorySegment seg : segments) {
            if (seg.address() == 0) {
                throw new IllegalStateException("Null memory segment passed to native bridge");
            }
        }
    }

    public MomentumFunctions getMomentumFunctions() {
        return functions.momentum;
    }

    @Override
    public void close() {
        log.info("Closing native bridge");
        arena.close();
    }
}