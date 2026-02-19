package com.example.indicator;

import com.example.ffi.bridge.NativeBridge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.lang.foreign.MemorySegment;

@Slf4j
@Service
public final class IndicatorEngineImpl implements IndicatorEngine {
    private final NativeBridge bridge;

    public IndicatorEngineImpl(NativeBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void calculateIndicator(
            Indicator indicator,
            MemorySegment state,
            MemorySegment candles,
            MemorySegment signals,
            long count
    ) {
        bridge.execute(
                indicator.fn(),
                state,
                candles,
                signals,
                count
        );
    }
}
