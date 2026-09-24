package com.artheus.deliveryservice.delivery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DeliveryRequest(
        @NotNull(message = "EventId cannot be null.")
        UUID eventId,

        @NotNull(message = "SubscriptionId cannot be null.")
        UUID subscriptionId,

        @NotBlank(message = "TargetUrl cannot be blank.")
        String targetUrl,

        @NotBlank(message = "Payload cannot be blank.")
        String payload
) {}
