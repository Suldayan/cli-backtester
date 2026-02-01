package com.example.orchestrator.cli;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Command implements CommandLineRunner {
    private static final short MAX_NUM_ARGS = 2;

    @Override
    public void run(String... args) {
        try {
            if (args.length != MAX_NUM_ARGS) {
                throw new IllegalArgumentException("Invalid number of arguments.\nUsage: <path to ohlcv.csv> <path to strategy config>");
            }

            final String OHLCV_PATH = args[0];
            final String CONFIG_PATH = args[1];

            log.info("Running backtest...");
            log.info("OHLCV Path: {} \n Strategy Config Path: {}", OHLCV_PATH, CONFIG_PATH);
        } catch (Exception e) {
            log.error("Backtest failed: {}", e.getMessage());
        }
    }
}
