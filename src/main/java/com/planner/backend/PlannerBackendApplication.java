package com.planner.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;

@SpringBootApplication
public class PlannerBackendApplication {
	private static final Logger log = LoggerFactory.getLogger(PlannerBackendApplication.class);

	@Value("${server.port:8080}")
	private String serverPort;

	@Value("${planner.data-dir:data}")
	private String dataDir;

	public static void main(String[] args) {
		SpringApplication.run(PlannerBackendApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onReady() {
		log.info("Planner backend iniciado com sucesso. port={} dataDir={}", serverPort, dataDir);
	}

}
