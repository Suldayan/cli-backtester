# cli-backtester

A high-performance CLI backtesting engine that utilizes the new API features of **Java 25** for high-level system orchestration and **Rust** for low-latency computational tasks.

## Status: Active Development
Building out the FFI layer and core memory architecture.

## System Philosophy

The core idea behind this project is to master the bridge between two powerful backend languages. I'm aiming for near-native performance while maintaining Java's robust ecosystem for the user-facing CLI and module management.

## Memory Architecture: The "Arena" Strategy

The backbone of this system is shared-memory communication using the **Arena API** introduced in Java 25. This API allows the Java side to take ownership of an allocated off-heap block of memory, which becomes the main domain for Rust-Java communication.

### Base + Offset Addressing

I treat the off-heap memory as a raw byte array where I'm responsible for the address arithmetic:

- **The Base**: The `MemorySegment` allocated by the Java `Arena` provides the base address of the shared memory block.
- **The Offset**: I define memory layouts where every field is mapped to a specific byte offset from that base.
- **The Match**: Each Java primitive type (e.g., `JAVA_DOUBLE`, `JAVA_INT`) must match the Rust type (e.g., `f64`, `i32`) exactly in size and alignment.

> [!WARNING]  
> **Manual Alignment**: The byte offsets in Java must match the memory alignment Rust expects (e.g., an 8-byte double must start at an offset divisible by 8). If these offsets are off by even a single byte, you'll be reading corrupted data or causing hardware alignment faults.

## Internal Abstractions

Since most of the system orchestration happens on the Java side (including memory management), I've built a heavy abstraction layer to keep the "unsafe" memory management readable:

- **FFI Module**: The gatekeeper. Handles `Arena` creation, defines the `MethodHandles` for Rust functions, and manages the lifecycle of native memory.
- **Interconnect Files**: Store the "blueprints"—the specific memory layouts and function descriptors.
- **Indicator Module**: Depends on the FFI module to send data to Rust for processing.

## Why FFM (Project Panama) over JNI?

JNI carries significant overhead when crossing the language boundary frequently. Here's why I went with FFM:

**Performance**: By using shared memory, I avoid the "copy tax" of JNI. Java and Rust look at the same bits in memory—no marshalling needed.

**Cleanliness**: FFM lets me define native interfaces purely in Java code without needing a C header "glue" layer.

**Memory Management**: JNI requires manual memory freeing, which adds complexity and potential for leaks. FFM's Arena handles cleanup automatically when the arena closes.

**The Trade-off**: The burden of accuracy is entirely on me. I must ensure the Java side is a perfect mirror of the Rust interface. While this increases maintenance, it forces cleaner and more disciplined code.

---

## Development Notes

### Current Challenges
- Ensuring proper struct alignment between Java layouts and Rust `repr(C)`
- Building clean abstractions around unsafe memory operations
- Defining a consistent error handling strategy across the FFI boundary
