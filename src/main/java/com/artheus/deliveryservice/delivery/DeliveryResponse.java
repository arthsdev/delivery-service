package com.artheus.deliveryservice.delivery;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

public record DeliveryResponse(
        @Schema(description = "Public identifier of the delivery attempt", example = "9b1f2e3a-1234-4a5b-8c9d-abcdef123456")
        UUID publicId,

        @Schema(description = "Public identifier of the originating event")
        UUID eventId,

        @Schema(description = "Public identifier of the subscription this delivery is for")
        UUID subscriptionId,

        @Schema(description = "URL the payload was delivered to")
        String targetUrl,

        @Schema(description = "Current status of the delivery attempt")
        Status status,

        @Schema(description = "Number of delivery attempts made so far", example = "1")
        int attemptCount,

        @Schema(description = "Timestamp of the most recent delivery attempt")
        LocalDateTime lastAttemptAt,

        @Schema(description = "Timestamp when the delivery attempt was first created")
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