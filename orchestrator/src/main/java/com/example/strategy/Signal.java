package com.example.strategy;

import com.example.ffi.layout.SignalMemory;

import java.lang.foreign.MemorySegment;

public final class Signal {
    private double price;
    private double indicatorValue;
    private int action;

    public void read(final MemorySegment signals, final int index) {
        final long offset = (long) index * SignalMemory.BYTE_SIZE;
        this.price = (double) SignalMemory.PRICE.get(signals, offset);
        this.indicatorValue = (double) SignalMemory.INDICATOR.get(signals, offset);
        this.action = (int) SignalMemory.ACTION.get(signals, offset);
    }

    // TODO to be used in the ingestion service when made
    public double price() { return price; }
    public double indicatorValue() { return indicatorValue; }
    public int action() { return action; }
}