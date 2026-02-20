package com.example.strategy;

import lombok.NonNull;

import java.util.List;

public record CompositeCondition(Operator operator, List<StrategyCondition> conditions
) implements StrategyCondition {

    @Override
    public boolean evaluate(final @NonNull Signal signal) {
        return switch (operator) {
            case AND -> conditions.stream().allMatch(c -> c.evaluate(signal));
            case OR  -> conditions.stream().anyMatch(c -> c.evaluate(signal));
            case NOT -> !conditions.getFirst().evaluate(signal);
        };
    }
}