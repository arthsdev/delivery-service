package com.artheus.deliveryservice.delivery;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DeliveryRequest(
        @Schema(description = "Public identifier of the originating event", example = "405b3ebc-afbe-4cd4-adf6-83194c1bf279")
        @NotNull(message = "EventId cannot be null.")
        UUID eventId,

        @Schema(description = "Public identifier of the subscription this delivery is for", example = "f78b013b-83d7-4e9a-be78-665c4fb0ef81")
        @NotNull(message = "SubscriptionId cannot be null.")
        UUID subscriptionId,

        @Schema(description = "URL the payload will be delivered to", example = "https://webhook.site/421cf96b-fe43-4f10-ba12-7bdd412bd892")
        @NotBlank(message = "TargetUrl cannot be blank.")
        String targetUrl,

        @Schema(description = "Event body, serialized as a JSON string", example = "{\"orderId\":123,\"productId\":10}")
        @NotBlank(message = "Payload cannot be blank.")
        String payload
) {}