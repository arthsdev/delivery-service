package com.artheus.deliveryservice.delivery;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Slf4j
@Component
public class DeliveryStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final DeliveryAttemptService deliveryAttemptService;
    private final StringRedisTemplate redisTemplate;
    private final Validator validator;
    private final String streamKey;
    private final String groupName;

    public DeliveryStreamListener(
            DeliveryAttemptService deliveryAttemptService,
            StringRedisTemplate redisTemplate,
            Validator validator,
            @Value("${event-stream.deliveries-key}") String streamKey,
            @Value("${event-stream.group}") String groupName) {
        this.deliveryAttemptService = deliveryAttemptService;
        this.redisTemplate = redisTemplate;
        this.validator = validator;
        this.streamKey = streamKey;
        this.groupName = groupName;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        DeliveryRequest request = toValidRequest(message);
        if (request == null) {
            acknowledge(message);
            return;
        }

        try {
            deliveryAttemptService.create(request);
        } catch (RuntimeException e) {
            log.error("Failed to process stream message [id={}], leaving it pending", message.getId(), e);
            return;
        }

        acknowledge(message);
    }

    private DeliveryRequest toValidRequest(MapRecord<String, String, String> message) {
        Map<String, String> body = message.getValue();

        UUID eventId;
        UUID subscriptionId;

        try {
            eventId = UUID.fromString(body.get("eventId"));
            subscriptionId = UUID.fromString(body.get("subscriptionId"));
        } catch (NullPointerException | IllegalArgumentException e) {
            log.error("Malformed message [id={}]: invalid or missing UUID fields (eventId, subscriptionId)", message.getId(), e);
            return null;
        }

        String targetUrl = body.get("targetUrl");
        String payload = body.get("payload");

        DeliveryRequest request = new DeliveryRequest(eventId, subscriptionId, targetUrl, payload);

        Set<ConstraintViolation<DeliveryRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining(", "));

            log.error("Invalid message [id={}]: validation failed -> [{}]", message.getId(), errors);
            return null;
        }

        return request;
    }

    private void acknowledge(MapRecord<String, String, String> message) {
        try {
            redisTemplate.opsForStream().acknowledge(streamKey, groupName, message.getId());
        } catch (RuntimeException e) {
            log.error("Failed to acknowledge stream message [id={}]", message.getId(), e);
        }
    }
}