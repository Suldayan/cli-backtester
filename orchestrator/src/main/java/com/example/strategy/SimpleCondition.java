package com.example.strategy;

import lombok.NonNull;

public record SimpleCondition(
        String indicator,
        int period,
        ConditionType condition,
        Target target
) implements StrategyCondition {

    @Override
    public boolean evaluate(final @NonNull Signal signal) {
        return switch (condition) {
            case CROSSES_ABOVE -> signal.indicatorValue() > target.resolveValue(signal);
            case CROSSES_BELOW -> signal.indicatorValue() < target.resolveValue(signal);
        };
    }
}