package com.example.orchestrator.cli;

import com.example.backtest.Backtester;
import com.example.ingestion.IngestionService;
import com.example.result.BacktestPrinter;
import com.example.result.BacktestResult;
import com.example.strategy.Strategy;
import com.example.strategy.StrategyParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Command implements CommandLineRunner {
    private final IngestionService ingestionService;
    private final StrategyParser   strategyParser;
    private final Backtester backtester;
    private final BacktestPrinter backtestPrinter;

    private static final int EXPECTED_ARGS = 2;

    public Command(
            final IngestionService ingestionService,
            final StrategyParser strategyParser,
            final Backtester backtester, BacktestPrinter backtestPrinter) {
        this.ingestionService = ingestionService;
        this.strategyParser = strategyParser;
        this.backtester = backtester;
        this.backtestPrinter = backtestPrinter;
    }

    @Override
    public void run(final String... args) {
        if (args.length != EXPECTED_ARGS) {
            log.error("Usage: <path to ohlcv.csv> <path to strategy.json>");
            return;
        }

        final Strategy strategy = strategyParser.parse(args[1]);
        log.info("Strategy loaded: {}", strategy.name());

        final int totalBars = ingestionService.processCSV(args[0]);

        final BacktestResult result = backtester.run(
                ingestionService.candleBuffer(),
                ingestionService.closeBuffer(),
                ingestionService.indicatorBuffers(),
                totalBars,
                strategy
        );

        backtestPrinter.print(result);
    }
}