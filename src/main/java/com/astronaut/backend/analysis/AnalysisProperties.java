package com.astronaut.backend.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("analysis.bedrock")
public record AnalysisProperties(boolean enabled, String region, String modelId, String apiToken) {
    @Configuration
    @EnableConfigurationProperties(AnalysisProperties.class)
    static class Registration {}
}
