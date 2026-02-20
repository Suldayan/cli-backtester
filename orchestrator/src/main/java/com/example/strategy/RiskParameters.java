package com.example.strategy;

/*
* Holds the percent that the user inputs for each field
* */
public record RiskParameters(
        double stopLoss,
        double takeProfit,
        double positionSize
) {
    public RiskParameters {
        if (stopLoss <= 0 || stopLoss >= 100)
            throw new IllegalArgumentException("stopLoss must be between 0 and 100");
        if (takeProfit <= 0 || takeProfit >= 100)
            throw new IllegalArgumentException("takeProfit must be between 0 and 100");
        if (positionSize <= 0 || positionSize >= 100)
            throw new IllegalArgumentException("positionSize must be between 0 and 100");
        if (takeProfit <= stopLoss)
            throw new IllegalArgumentException("takeProfit must be greater than stopLoss");
    }
}
