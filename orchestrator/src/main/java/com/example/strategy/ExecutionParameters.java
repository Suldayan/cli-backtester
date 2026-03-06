package com.example.strategy;

public record ExecutionParameters(
        double initialCapital,
        double slippagePct,
        double feePct,
        double riskFreeRate
) {
    public ExecutionParameters {
        if (initialCapital <= 0)
            throw new IllegalArgumentException("initialCapital must be > 0");
        if (slippagePct < 0)
            throw new IllegalArgumentException("slippagePct must be >= 0");
        if (feePct < 0)
            throw new IllegalArgumentException("feePct must be >= 0");
    }
}