package com.example.ingestion;

import com.example.strategy.Strategy;

public interface IngestionService {
    void processCSV(String path, Strategy strategy);
}