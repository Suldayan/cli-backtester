package com.example.persistence;

import com.example.backtest.Backtester;
import com.example.ingestion.IngestionService;
import com.example.orchestrator.OrchestratorApplication;
import com.example.strategy.Strategy;
import com.example.strategy.StrategyParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OrchestratorApplication.class)
class PersistenceIntegrationTest {

    @Autowired private JdbcClient jdbcClient;
    @Autowired private StrategyParser strategyParser;
    @Autowired private IngestionService ingestionService;
    @Autowired private Backtester backtester;

    @Test
    void backtest_persistsRunToDatabase() {
        final Strategy strategy = strategyParser.parse(resourcePath("strategies/simple_sma.json"));
        final int bars = ingestionService.processCSV(resourcePath("data/AAPL.csv"));

        backtester.run(
                ingestionService.candleBuffer(),
                ingestionService.closeBuffer(),
                ingestionService.indicatorBuffers(),
                bars,
                strategy
        );

        final int count = jdbcClient
                .sql("SELECT COUNT(*) FROM backtest_runs WHERE strategy_name = :name")
                .param("name", strategy.name())
                .query(Integer.class)
                .single();

        assertThat(count).isGreaterThan(0);
    }

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