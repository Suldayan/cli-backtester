package com.example.result.metrics;

public record Risk(
        double maxDrawdownPct,
        long maxDrawdownDurationDays,
        double volatility,
        double sharpeRatio,
        double sortinoRatio
) {}
