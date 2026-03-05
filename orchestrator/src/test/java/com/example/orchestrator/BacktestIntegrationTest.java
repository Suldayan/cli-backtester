package com.example.orchestrator;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.ingestion.IngestionService;
import com.example.strategy.Strategy;
import com.example.strategy.StrategyParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

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

    private ListAppender<ILoggingEvent> logAppender;
    private Logger ingestionLogger;

    @BeforeEach
    void attachLogAppender() {
        // Capture log output from IngestionServiceImpl so we can assert on signals
        ingestionLogger = (Logger) LoggerFactory.getLogger(
                "com.example.ingestion.internal.IngestionServiceImpl"
        );
        logAppender = new ListAppender<>();
        logAppender.start();
        ingestionLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        ingestionLogger.detachAppender(logAppender);
    }

    // --- Strategy parsing tests ---

    @Test
    void simpleSmaStrategy_parsesWithoutError() {
        final String path = resourcePath("strategies/simple_sma.json");
        assertThatNoException().isThrownBy(() -> strategyParser.parse(path));
    }

    @Test
    void compositeSmaRocStrategy_parsesWithoutError() {
        final String path = resourcePath("strategies/composite_sma_roc.json");
        assertThatNoException().isThrownBy(() -> strategyParser.parse(path));
    }

    @Test
    void simpleSmaStrategy_hasCorrectName() {
        final Strategy strategy = strategyParser.parse(
                resourcePath("strategies/simple_sma.json")
        );
        assertThat(strategy.name()).isEqualTo("Simple SMA Strategy");
    }

    @Test
    void compositeStrategy_hasCorrectName() {
        final Strategy strategy = strategyParser.parse(
                resourcePath("strategies/composite_sma_roc.json")
        );
        assertThat(strategy.name()).isEqualTo("Composite SMA + ROC Strategy");
    }

    // --- Full pipeline tests ---

    @Test
    void simpleStrategy_processesCsvWithoutError() {
        final String csvPath      = resourcePath("data/AAPL.csv");
        final String strategyPath = resourcePath("strategies/simple_sma.json");
        final Strategy strategy = strategyParser.parse(strategyPath);

        assertThatNoException().isThrownBy(() ->
                ingestionService.processCSV(csvPath, strategy)
        );
    }

    @Test
    void compositeStrategy_processesCsvWithoutError() {
        final String csvPath      = resourcePath("data/AAPL.csv");
        final String strategyPath = resourcePath("strategies/composite_sma_roc.json");
        final Strategy strategy = strategyParser.parse(strategyPath);

        assertThatNoException().isThrownBy(() ->
                ingestionService.processCSV(csvPath, strategy)
        );
    }

    @Test
    void simpleStrategy_producesSomeSignals() {
        final String csvPath      = resourcePath("data/AAPL.csv");
        final Strategy strategy = strategyParser.parse(
                resourcePath("strategies/simple_sma.json")
        );

        ingestionService.processCSV(csvPath, strategy);

        final List<String> signals = logMessages();
        assertThat(signals)
                .as("Expected at least one BUY or SELL signal to be logged")
                .anyMatch(msg -> msg.contains("BUY") || msg.contains("SELL"));
    }

    @Test
    void simpleStrategy_signalsContainPriceAndIndicator() {
        final Strategy strategy = strategyParser.parse(
                resourcePath("strategies/simple_sma.json")
        );

        ingestionService.processCSV(resourcePath("data/AAPL.csv"), strategy);

        logMessages().stream()
                .filter(msg -> msg.contains("BUY") || msg.contains("SELL"))
                .forEach(msg -> {
                    assertThat(msg).contains("price:");
                    assertThat(msg).contains("SMA(");
                });
    }

    @Test
    void invalidStrategyJson_throwsMeaningfulError() {
        assertThatNoException()
                .isThrownBy(() -> strategyParser.parse(resourcePath("strategies/simple_sma.json")));
    }

    // --- Helpers ---

    private String resourcePath(final String relativePath) {
        final URL resource = getClass().getClassLoader().getResource(relativePath);
        assertThat(resource)
                .as("Test resource not found: " + relativePath)
                .isNotNull();
        try {
            return Path.of(resource.toURI()).toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid resource path: " + relativePath, e);
        }
    }

    private List<String> logMessages() {
        return logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}