package com.example.strategy;

import com.example.ffi.layout.SignalMemory;

import java.lang.foreign.MemorySegment;

public final class Signal {
    private double price;
    private long timestamp;
    private int action;
    private final double[] indicators = new double[8];

    public void read(final MemorySegment signals, final int index) {
        final long offset = (long) index * SignalMemory.BYTE_SIZE;
        this.price = (double) SignalMemory.PRICE.get(signals, offset);
        this.timestamp = (long) SignalMemory.TIMESTAMP.get(signals, offset);
        this.action = (int) SignalMemory.ACTION.get(signals, offset);
    }

    public void setIndicator(final int index, final double value) {
        indicators[index] = value;
    }

    public double indicator(final int index) {
        return indicators[index];
    }

    public double price() {
        return price;
    }

    public long timestamp() {
        return timestamp;
    }

    public int action() {
        return action;
    }
}