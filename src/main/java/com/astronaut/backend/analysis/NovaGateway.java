package com.astronaut.backend.analysis;

import com.astronaut.backend.exception.ServiceUnavailableException;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;
import java.time.Duration;

@Component
public class NovaGateway {
    public record Reply(String text, Integer inputTokens, Integer outputTokens) {}
    private final AnalysisProperties properties;
    public NovaGateway(AnalysisProperties properties) { this.properties=properties; }
    public Reply converse(String system, String context) {
        // Default credentials chain: instance role in EC2, no access key in code.
        try (var client=BedrockRuntimeClient.builder().region(Region.of(properties.region()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                    .apiCallTimeout(Duration.ofSeconds(45)).apiCallAttemptTimeout(Duration.ofSeconds(40))
                    .retryPolicy(software.amazon.awssdk.core.retry.RetryPolicy.none()).build()).build()) {
            var response=client.converse(ConverseRequest.builder().modelId(properties.modelId())
                .system(SystemContentBlock.builder().text(system).build())
                .messages(Message.builder().role(ConversationRole.USER)
                    .content(ContentBlock.fromText(context)).build())
                .inferenceConfig(InferenceConfiguration.builder().maxTokens(1800).temperature(0F).build())
                .build());
            if (response.stopReason()!=StopReason.END_TURN || response.output()==null
                    || response.output().message()==null) throw new IllegalStateException();
            String text=response.output().message().content().stream().filter(b->b.text()!=null)
                .map(ContentBlock::text).collect(java.util.stream.Collectors.joining());
            if (text.length()>16000) throw new IllegalStateException();
            return new Reply(text,response.usage().inputTokens(),response.usage().outputTokens());
        } catch (RuntimeException e) {
            // Do not leak SDK errors, request bodies, tokens, or private metrics.
            throw new ServiceUnavailableException("Ground model unavailable or incomplete response; existing alerts remain authoritative.", null);
        }
    }
}
