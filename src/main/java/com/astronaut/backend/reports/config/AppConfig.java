package com.astronaut.backend.reports.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;
import java.time.Duration;

/** Cliente de DynamoDB (base no relacional de los informes). */
@Configuration
public class AppConfig {

    /**
     * Cliente de DynamoDB con tiempos cortos: si AWS no responde, el servicio
     * pasa rápido a modo local en vez de quedarse esperando.
     */
    @Bean(destroyMethod = "close")
    public DynamoDbClient dynamoDbClient(ReportsProperties properties) {
        ReportsProperties.Dynamo cfg = properties.dynamodb();
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(cfg.region() == null || cfg.region().isBlank() ? "us-east-1" : cfg.region()))
                .credentialsProvider(credentials(cfg))
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(3))
                        .socketTimeout(Duration.ofSeconds(5)))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5))
                        .retryPolicy(RetryPolicy.builder().numRetries(1).build())
                        .build());
        if (cfg.endpoint() != null && !cfg.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(cfg.endpoint()));
        }
        return builder.build();
    }

    private static AwsCredentialsProvider credentials(ReportsProperties.Dynamo cfg) {
        if (cfg.accessKeyId() != null && !cfg.accessKeyId().isBlank()
                && cfg.secretAccessKey() != null && !cfg.secretAccessKey().isBlank()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(cfg.accessKeyId(), cfg.secretAccessKey()));
        }
        // variables de entorno, ~/.aws/credentials o el rol IAM del EC2
        return DefaultCredentialsProvider.create();
    }
}
