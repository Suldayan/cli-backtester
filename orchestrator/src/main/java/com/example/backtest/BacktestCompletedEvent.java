package com.example.backtest;

import com.example.result.BacktestResult;
import com.example.strategy.Strategy;

public record BacktestCompletedEvent(
        BacktestResult result,
        Strategy strategy
) {}