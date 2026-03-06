package com.example.result;

import com.example.strategy.Signal;

public record Trade(Signal buy, Signal sell) {
}
