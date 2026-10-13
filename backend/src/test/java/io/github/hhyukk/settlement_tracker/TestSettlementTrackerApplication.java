package io.github.hhyukk.settlement_tracker;

import org.springframework.boot.SpringApplication;

public class TestSettlementTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.from(SettlementTrackerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
