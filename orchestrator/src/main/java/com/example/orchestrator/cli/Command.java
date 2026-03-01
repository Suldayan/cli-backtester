package com.example.orchestrator.cli;

import com.example.ingestion.IngestionService;
import com.example.strategy.Strategy;
import com.example.strategy.StrategyParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Command implements CommandLineRunner {
    private final IngestionService ingestionService;
    private final StrategyParser strategyParser;

    private static final short EXPECTED_ARGS = 2;

    public Command(final IngestionService ingestionService, final StrategyParser strategyParser) {
        this.ingestionService = ingestionService;
        this.strategyParser = strategyParser;
    }

    @Override
    public void run(final String... args) {
        if (args.length != EXPECTED_ARGS) {
            log.error("Invalid arguments.\nUsage: <path to ohlcv.csv> <path to strategy.json>");
            return;
        }

        final String ohlcvPath = args[0];
        final String strategyPath = args[1];

        log.info("Loading strategy from: {}", strategyPath);
        final Strategy strategy = strategyParser.parse(strategyPath);
        log.info("Strategy loaded: {}", strategy.name());

        log.info("Running backtest on: {}", ohlcvPath);
        ingestionService.processCSV(ohlcvPath, strategy);

        log.info("Backtest complete.");
    }
}
