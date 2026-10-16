package com.artheus.deliveryservice.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import java.time.Duration;

@Slf4j
@Configuration
public class StreamConfig {

    @Value("${event-stream.poll-timeout:10s}")
    private Duration pollTimeout;

    @Value("${event-stream.retry-pause:3s}")
    private Duration retryPause;

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(pollTimeout)
                        .errorHandler(this::handleReadError)
                        .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }

    private void handleReadError(Throwable throwable) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(throwable);
        log.warn("Stream read failed, retrying in {}: {} ({})",
                retryPause, cause.getClass().getSimpleName(), cause.getMessage());
        try {
            Thread.sleep(retryPause.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}