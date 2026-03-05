package com.example.strategy;

public record SimpleCondition(
        int index,
        String indicator,
        int period,
        ConditionType condition,
        Target target
) implements StrategyCondition {

    @Override
    public boolean evaluate(Signal signal) {
        final double value = signal.indicator(index);
        return switch (condition) {
            case CROSSES_ABOVE -> value > target.resolveValue(signal);
            case CROSSES_BELOW -> value < target.resolveValue(signal);
        };
    }
}