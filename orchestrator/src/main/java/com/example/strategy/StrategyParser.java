package com.example.strategy;

@FunctionalInterface
public interface StrategyParser {
    Strategy parse(String path);
}
