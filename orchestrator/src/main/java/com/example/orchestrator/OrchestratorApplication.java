package com.example.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
		"com.example.ingestion",
		"com.example.ffi",
		"com.example.indicator",
		"com.example.strategy",
		"com.example.arena"
})
public class OrchestratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrchestratorApplication.class, args);
	}

}
