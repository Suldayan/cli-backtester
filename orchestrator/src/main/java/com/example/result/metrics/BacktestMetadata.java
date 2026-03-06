package com.example.result.metrics;

import java.time.LocalDate;

public record BacktestMetadata(
        String symbol,
        LocalDate periodStart,
        LocalDate periodEnd,
        int barsProcessed,
        long computeTimeMs
) {}