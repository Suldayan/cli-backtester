package com.example.indicator;

import java.lang.invoke.MethodHandle;

public record Indicator(
        String name,
        int window,
        MethodHandle handle) {
}
