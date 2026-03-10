package com.example.orchestrator;

import com.example.backtest.Backtester;
import com.example.ffi.layout.CandleMemory;
import com.example.ingestion.IngestionService;
import com.example.result.BacktestPrinter;
import com.example.result.BacktestResult;
import com.example.result.metrics.TradeMetrics;
import com.example.strategy.Strategy;
import com.example.strategy.StrategyParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Full vertical slice integration test.
 * Requires native library to be compiled before running:
 *   cd engine && cargo build --release
 */
@SpringBootTest
class BacktestIntegrationTest {

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private StrategyParser strategyParser;

    @Autowired
    private Backtester backtester;

    @Autowired
    private BacktestPrinter backtestPrinter;

    // --- Strategy parsing tests ---

    @Test
    void simpleSmaStrategy_parsesWithoutError() {
        assertThatNoException().isThrownBy(() ->
                strategyParser.parse(resourcePath("strategies/simple_sma.json")));
    }

    @Test
    void compositeStrategy_parsesWithoutError() {
        assertThatNoException().isThrownBy(() ->
                strategyParser.parse(resourcePath("strategies/composite_sma_roc.json")));
    }

    @Test
    void simpleSmaStrategy_hasCorrectName() {
        final Strategy strategy = strategyParser.parse(resourcePath("strategies/simple_sma.json"));
        assertThat(strategy.name()).isEqualTo("Simple SMA Strategy");
    }

    @Test
    void compositeStrategy_hasCorrectName() {
        final Strategy strategy = strategyParser.parse(resourcePath("strategies/composite_sma_roc.json"));
        assertThat(strategy.name()).isEqualTo("Composite SMA + ROC Strategy");
    }

    @Test
    void simpleSmaStrategy_hasExecutionParameters() {
        final Strategy strategy = strategyParser.parse(resourcePath("strategies/simple_sma.json"));
        assertThat(strategy.execution().initialCapital()).isEqualTo(10000.0);
        assertThat(strategy.execution().slippagePct()).isEqualTo(0.1);
        assertThat(strategy.execution().feePct()).isEqualTo(0.05);
    }

    @Test
    void invalidStrategyJson_throwsMeaningfulError() {
        assertThatThrownBy(() ->
                strategyParser.parse(resourcePath("strategies/invalid.json")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse strategy");
    }

    // --- Ingestion tests ---

    @Test
    void processCSV_returnsCorrectBarCount() {
        final int bars = ingestionService.processCSV(resourcePath("data/AAPL.csv"));
        assertThat(bars).isGreaterThan(0);
    }

    @Test
    void processCSV_populatesCandleBuffer() {
        final int bars = ingestionService.processCSV(resourcePath("data/AAPL.csv"));
        assertThat(bars).isGreaterThan(0);
        // Verify candle buffer has data — first close price should be non-zero
        final double firstClose = (double) CandleMemory.CLOSE.get(
                ingestionService.candleBuffer(), 0L);
        assertThat(firstClose).isGreaterThan(0.0);
    }

    // --- Full pipeline tests ---

    @Test
    void simpleStrategy_completesWithoutError() {
        final Strategy strategy = strategyParser.parse(resourcePath("strategies/simple_sma.json"));
        final int bars = ingestionService.processCSV(resourcePath("data/AAPL.csv"));

        assertThatNoException().isThrownBy(() ->
                backtester.run(
                        ingestionService.candleBuffer(),
                        ingestionService.closeBuffer(),
                        ingestionService.indicatorBuffers(),
                        bars,
                        strategy
                ));

        final BacktestResult result = backtester.run(
                ingestionService.candleBuffer(),
                ingestionService.closeBuffer(),
                ingestionService.indicatorBuffers(),
                bars,
                strategy
        );

        backtestPrinter.print(result);
    }

    @Test
    void compositeStrategy_completesWithoutError() {
        final Strategy strategy = strategyParser.parse(resourcePath("strategies/composite_sma_roc.json"));
        final int bars = ingestionService.processCSV(resourcePath("data/AAPL.csv"));

        assertThatNoException().isThrownBy(() ->
                backtester.run(
                        ingestionService.candleBuffer(),
                        ingestionService.closeBuffer(),
                        ingestionService.indicatorBuffers(),
                        bars,
                        strategy
                ));

        final BacktestResult result = backtester.run(
                ingestionService.candleBuffer(),
                ingestionService.closeBuffer(),
                ingestionService.indicatorBuffers(),
                bars,
                strategy
        );

        backtestPrinter.print(result);
    }

    @Test
    void simpleStrategy_producesSomeTrades() {
        final Strategy strategy = strategyParser.parse(resourcePath("strategies/simple_sma.json"));
        final int bars = ingestionService.processCSV(resourcePath("data/AAPL.csv"));

        final BacktestResult result = backtester.run(
                ingestionService.candleBuffer(),
                ingestionService.closeBuffer(),
                ingestionService.indicatorBuffers(),
                bars,
                strategy
        );

        assertThat(result.trades().totalTrades())
                .as("Expected at least one round-trip trade")
                .isGreaterThan(0);
    }

    @Test
    void simpleStrategy_metaIsPopulated() {
        final Strategy strategy = strategyParser.parse(resourcePath("strategies/simple_sma.json"));
        final int bars = ingestionService.processCSV(resourcePath("data/AAPL.csv"));

        final BacktestResult result = backtester.run(
                ingestionService.candleBuffer(),
                ingestionService.closeBuffer(),
                ingestionService.indicatorBuffers(),
                bars,
                strategy
        );

        assertThat(result.metadata().symbol()).isEqualTo("AAPL");
        assertThat(result.metadata().barsProcessed()).isEqualTo(bars);
        assertThat(result.metadata().periodStart()).isNotNull();
        assertThat(result.metadata().periodEnd()).isNotNull();
        assertThat(result.metadata().computeTimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void simpleStrategy_tradeMetricsAreConsistent() {
        final Strategy strategy = strategyParser.parse(resourcePath("strategies/simple_sma.json"));
        final int bars = ingestionService.processCSV(resourcePath("data/AAPL.csv"));

        final BacktestResult result = backtester.run(
                ingestionService.candleBuffer(),
                ingestionService.closeBuffer(),
                ingestionService.indicatorBuffers(),
                bars,
                strategy
        );

        final TradeMetrics trades = result.trades();
        assertThat(trades.wins() + trades.losses()).isEqualTo(trades.totalTrades());
        assertThat(trades.winRatePct()).isBetween(0.0, 100.0);
        assertThat(trades.profitFactor()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void simpleStrategy_riskMetricsAreValid() {
        final Strategy strategy = strategyParser.parse(resourcePath("strategies/simple_sma.json"));
        final int bars = ingestionService.processCSV(resourcePath("data/AAPL.csv"));

        final BacktestResult result = backtester.run(
                ingestionService.candleBuffer(),
                ingestionService.closeBuffer(),
                ingestionService.indicatorBuffers(),
                bars,
                strategy
        );

        assertThat(result.risk().maxDrawdownPct()).isGreaterThanOrEqualTo(0.0);
        assertThat(result.risk().volatility()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void simpleStrategy_equityCurveIsPopulated() {
        final Strategy strategy = strategyParser.parse(resourcePath("strategies/simple_sma.json"));
        final int bars = ingestionService.processCSV(resourcePath("data/AAPL.csv"));

        final BacktestResult result = backtester.run(
                ingestionService.candleBuffer(),
                ingestionService.closeBuffer(),
                ingestionService.indicatorBuffers(),
                bars,
                strategy
        );

        assertThat(result.performance().equityCurve())
                .as("Equity curve should have entries")
                .isNotEmpty();
        assertThat(result.performance().equityCurve().getFirst().equity())
                .isEqualTo(strategy.execution().initialCapital());
    }

    // --- Helpers ---

    private String resourcePath(final String relativePath) {
        final URL resource = getClass().getClassLoader().getResource(relativePath);
        assertThat(resource).as("Test resource not found: " + relativePath).isNotNull();
        try {
            return Path.of(resource.toURI()).toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid resource path: " + relativePath, e);
        }
    }
}