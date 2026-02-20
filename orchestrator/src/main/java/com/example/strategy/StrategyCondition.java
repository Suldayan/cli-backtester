package com.example.strategy;

/*
* Where the composite pattern lives
* SignalContext is just a thin wrapper that gives the condition evaluator access to the current signal data without exposing the raw MemorySegment directly
* */
public interface StrategyCondition {
    boolean evaluate(Signal signal);
}
