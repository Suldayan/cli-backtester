package com.example.result;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record Trade(
        String symbol,
        LocalDate entryDate,
        LocalDate exitDate,
        double entryPrice,
        double exitPrice,
        double positionSize,
        double grossPnl,
        double slippageCost,
        double feesCost,
        double netPnl,
        boolean isWin
) {
    public long durationDays() {
        return ChronoUnit.DAYS.between(entryDate, exitDate);
    }
}