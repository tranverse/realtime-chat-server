package com.tranverse.chatserver.benchmark;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("benchmark")
public class MessageHistoryBenchmarkConfiguration {

    @Bean
    ApplicationRunner initializeMessageHistoryBenchmark(
            MessageHistoryBenchmarkSetupService setupService) {
        return arguments -> setupService.initialize();
    }
}
