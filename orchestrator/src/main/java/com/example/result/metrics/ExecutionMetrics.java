package com.example.result.metrics;

public record ExecutionMetrics(
        double totalSlippageCost,
        double totalFeesCost,
        long avgTradeDurationDays
) {}
