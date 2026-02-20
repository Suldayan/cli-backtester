package com.example.strategy;

import java.util.Objects;

/**
 * Represents the right-hand side of a condition comparison.
 * A target is either a fixed scalar value (e.g. RSI crosses below 30)
 * or a reference to another indicator's computed value (e.g. SMA 20 crosses above SMA 50).
 * Only one of {@code value} or {@code indicator} will be populated.
 */
public record Target(Double value, String indicator, Integer period) {
    public double resolveValue(final Signal signal) {
        return Objects.requireNonNullElseGet(value, signal::indicatorValue);
    }
}