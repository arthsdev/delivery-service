package com.artheus.deliveryservice.delivery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryProcessor {

    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final Clock clock;

    private final RestClient restClient = RestClient.builder()
            .requestFactory(clientRequestFactory())
            .build();

    @Async("deliveryTaskExecutor")
    @Transactional
    public void process(UUID publicId) {
        DeliveryAttempt attempt = deliveryAttemptRepository.findByPublicId(publicId)
                .orElseThrow(() -> new DeliveryAttemptNotFoundException(publicId));

        attempt.markProcessing(clock);
        deliveryAttemptRepository.save(attempt);

        try {
            getBodilessEntity(attempt);
            attempt.markSuccess();
            log.info("Delivery succeeded for attempt {}", publicId);

        } catch (RestClientException ex) {
            attempt.markFailed();
            log.warn("Delivery failed for attempt {}: {}", publicId, ex.getMessage());
        }

        deliveryAttemptRepository.save(attempt);
    }

    private @NonNull ResponseEntity<Void> getBodilessEntity(DeliveryAttempt attempt) {
        return restClient.post()
                .uri(attempt.getTargetUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(attempt.getPayload().getBytes(StandardCharsets.UTF_8))
                .retrieve()
                .toBodilessEntity();
    }

    private static org.springframework.http.client.ClientHttpRequestFactory clientRequestFactory() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        return factory;
    }
}