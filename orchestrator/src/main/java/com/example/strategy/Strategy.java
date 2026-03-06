package com.example.strategy;

public record Strategy(
        String name,
        String symbol,
        StrategyCondition openCondition,
        StrategyCondition closeCondition,
        RiskParameters risk
) {
    public int symbolId() {
        return symbol.hashCode();
    }
}
