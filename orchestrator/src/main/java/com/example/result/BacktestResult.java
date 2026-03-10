package com.example.result;

import com.example.result.metrics.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record BacktestResult(
        Performance performance,
        Risk risk,
        TradeMetrics trades,
        ExecutionMetrics execution,
        BacktestMetadata metadata
) {}
