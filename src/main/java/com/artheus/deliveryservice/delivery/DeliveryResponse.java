package com.artheus.deliveryservice.delivery;

import java.time.LocalDateTime;
import java.util.UUID;

public record DeliveryResponse(
        UUID publicId,
        UUID eventId,
        UUID subscriptionId,
        String targetUrl,
        Status status,
        int attemptCount,
        LocalDateTime lastAttemptAt,
        LocalDateTime createdAt
) {
    public static DeliveryResponse from(DeliveryAttempt attempt) {
        return new DeliveryResponse(
                attempt.getPublicId(),
                attempt.getEventId(),
                attempt.getSubscriptionId(),
                attempt.getTargetUrl(),
                attempt.getStatus(),
                attempt.getAttemptCount(),
                attempt.getLastAttemptAt(),
                attempt.getCreatedAt()
        );
    }
}
