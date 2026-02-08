package com.example.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

@Slf4j
@Component
public class NativeBridge {
    private final MethodHandle processBatchHandle;

    public NativeBridge() {
        String libName = System.mapLibraryName("engine");
        var libPath = Path.of("../engine/target/release", libName).toAbsolutePath();

        log.info("Linking Native Engine: {}", libPath);

        // Use a global arena for the library lookup so it stays loaded for the app lifetime
        SymbolLookup lib = SymbolLookup.libraryLookup(libPath, Arena.global());

        // 2. Find the function - Name must match the #[no_mangle] name in Rust
        MemorySegment funcAddr = lib.find("process_candle_batch")
                .orElseThrow(() -> new RuntimeException("Native function 'process_candle_batch' not found!"));

        // 3. Define the New Signature
        // Rust: pub unsafe extern "C" fn process_candle_batch(state, candles, signals, len)
        FunctionDescriptor desc = FunctionDescriptor.ofVoid(
                ValueLayout.ADDRESS,        // 1st arg: *mut StrategyState
                ValueLayout.ADDRESS,        // 2nd arg: *const Candle (Input)
                ValueLayout.ADDRESS,        // 3rd arg: *mut Signal (Output)
                ValueLayout.JAVA_LONG       // 4th arg: usize len
        );

        // 4. Link the Downcall Handle
        this.processBatchHandle = Linker.nativeLinker().downcallHandle(funcAddr, desc);
    }

    /**
     * Executes the strategy logic in Rust.
     * @param stateBuffer   Persistent memory for indicators
     * @param candleBuffer  Input batch of market data
     * @param signalBuffer  Output batch for trade decisions
     * @param count         Number of candles in the current batch
     */
    public void processBatch(
            MemorySegment stateBuffer,
            MemorySegment candleBuffer,
            MemorySegment signalBuffer,
            long count) {
        validateSegments(stateBuffer, candleBuffer, signalBuffer);

        try {
            // invokeExact is high-performance but strict:
            // the arguments must perfectly match the Descriptor types.
            processBatchHandle.invokeExact(stateBuffer, candleBuffer, signalBuffer, count);
        } catch (Throwable e) {
            log.error("Critical failure in Rust Engine execution", e);
            throw new RuntimeException("Native Bridge Crash", e);
        }
    }

    private void validateSegments(MemorySegment... segments) {
        for (MemorySegment segment : segments) {
            if (segment.address() == 0) {
                throw new IllegalArgumentException("Native Engine Error: Null memory address passed.");
            }

            // If the Arena that created it was closed, this returns false
            if (!segment.scope().isAlive()) {
                throw new IllegalStateException("Native Engine Error: Memory segment is no longer alive.");
            }
        }
    }
}