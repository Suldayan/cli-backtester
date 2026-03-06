package com.example.result.metrics;

import com.example.result.EquityPoint;

import java.util.List;

public record Performance(
        double totalReturnPct,
        double cagr,
        double totalPnl,
        double netPnlAfterCosts,
        List<EquityPoint> equityCurve
) {}
