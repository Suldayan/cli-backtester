package com.example.indicator;

import com.example.ffi.bridge.NativeBridge;
import com.example.strategy.CompositeCondition;
import com.example.strategy.SimpleCondition;
import com.example.strategy.StrategyCondition;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

@Component
public final class IndicatorResolver {
    private final NativeBridge nativeBridge;

    public IndicatorResolver(NativeBridge nativeBridge) {
        this.nativeBridge = nativeBridge;
    }

    public MethodHandle resolveHandle(final String indicator) {
        return switch (indicator.toUpperCase()) {
            case "SMA" -> nativeBridge.getMomentumFunctions().sma;
            case "EMA" -> nativeBridge.getMomentumFunctions().ema;
            case "ROC" -> nativeBridge.getMomentumFunctions().roc;
            default -> throw new IllegalArgumentException("Unsupported indicator: " + indicator);
        };
    }

    public List<Indicator> resolveAll(final StrategyCondition condition) {
        final List<Indicator> result = new ArrayList<>();
        resolveAllRecursive(condition, result);
        return result;
    }

    private void resolveAllRecursive(final StrategyCondition condition, final List<Indicator> result) {
        if (condition instanceof SimpleCondition simple) {
            final int index = result.size();
            result.add(new Indicator(
                    index,
                    simple.indicator(),
                    simple.period(),
                    resolveHandle(simple.indicator())
            ));
        } else if (condition instanceof CompositeCondition composite) {
            for (final StrategyCondition child : composite.conditions()) {
                resolveAllRecursive(child, result);
            }
        }
    }
}
