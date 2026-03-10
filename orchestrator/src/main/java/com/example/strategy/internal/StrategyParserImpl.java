package com.example.strategy.internal;

import com.example.strategy.*;
import lombok.NonNull;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public final class StrategyParserImpl implements StrategyParser {
    private final ObjectMapper mapper;

    private static final int STARTING_INDEX = 0;

    public StrategyParserImpl(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Strategy parse(final String path) {
        try {
            JsonNode root = mapper.readTree(Path.of(path).toFile());
            return new Strategy(
                    root.get("name").asString(),
                    root.get("symbol").asString(),
                    parseCondition(root.get("open")),
                    parseCondition(root.get("close")),
                    parseRisk(root.get("risk")),
                    parseExecution(root.get("execution"))
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse strategy: " + path, e);
        }
    }

    private ExecutionParameters parseExecution(final JsonNode node) {
        return new ExecutionParameters(
                node.get("initial_capital").asDouble(),
                node.get("slippage_pct").asDouble(),
                node.get("fee_pct").asDouble(),
                node.get("risk_free_rate").asDouble()
        );
    }

    private StrategyCondition parseCondition(final @NonNull JsonNode node) {
        final String type = node.get("type").asString();
        if (type.equals("simple")) {
            return new SimpleCondition(
                    STARTING_INDEX,
                    node.get("indicator").asString(),
                    node.get("period").asInt(),
                    ConditionType.valueOf(node.get("condition").asString()),
                    parseTarget(node.get("target"))
            );
        }
        final Operator operator = Operator.valueOf(node.get("operator").asString());
        List<StrategyCondition> children = new ArrayList<>();
        for (final JsonNode child : node.get("conditions")) {
            children.add(parseCondition(child));
        }
        return new CompositeCondition(operator, children);
    }

    private Target parseTarget(final @NonNull JsonNode node) {
        if (node.has("value"))
            return new Target(node.get("value").asDouble(), null, null, STARTING_INDEX);
        return new Target(null, node.get("indicator").asString(), node.get("period").asInt(), STARTING_INDEX);
    }

    private RiskParameters parseRisk(final @NonNull JsonNode node) {
        return new RiskParameters(
                node.get("stop_loss_pct").asDouble(),
                node.get("take_profit_pct").asDouble(),
                node.get("position_size_pct").asDouble()
        );
    }
}