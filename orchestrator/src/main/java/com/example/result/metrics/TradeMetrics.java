package com.example.result.metrics;

public record TradeMetrics(
        int totalTrades,
        int wins,
        int losses,
        double winRatePct,
        double avgWin,
        double avgLoss,
        double profitFactor
) {}
