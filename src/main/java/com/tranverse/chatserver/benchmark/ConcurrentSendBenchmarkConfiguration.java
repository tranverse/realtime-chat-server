package com.tranverse.chatserver.benchmark;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("benchmark")
public class ConcurrentSendBenchmarkConfiguration {

    @Bean
    ApplicationRunner initializeConcurrentSendBenchmark(ConcurrentSendBenchmarkSetupService setupService) {
        return arguments -> setupService.initialize();
    }
}
