package com.example.indicator;

import java.lang.invoke.MethodHandle;

public record Indicator(
        int index,
        String name,
        int window,
        MethodHandle handle) {
}
