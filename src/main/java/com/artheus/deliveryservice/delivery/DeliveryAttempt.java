package com.artheus.deliveryservice.delivery;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "delivery_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private UUID publicId;

    @Column(nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false)
    private UUID subscriptionId;

    @Column(nullable = false)
    private String targetUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(columnDefinition = "JSON", nullable = false)
    private String payload;

    @Column(nullable = false)
    private int attemptCount;

    private LocalDateTime lastAttemptAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static DeliveryAttempt create(
            UUID eventId,
            UUID subscriptionId,
            String targetUrl,
            String payload,
            Clock clock) {

        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.publicId = UUID.randomUUID();
        attempt.eventId = eventId;
        attempt.subscriptionId = subscriptionId;
        attempt.targetUrl = targetUrl;
        attempt.payload = payload;
        attempt.status = Status.PENDING;
        attempt.attemptCount = 0;
        attempt.createdAt = LocalDateTime.now(clock);
        return attempt;
    }

    public void markProcessing(Clock clock) {
        this.status = Status.PROCESSING;
        this.attemptCount++;
        this.lastAttemptAt = LocalDateTime.now(clock);
    }

    public void markSuccess() {
        this.status = Status.SUCCESS;
    }

    public void markFailed() {
        this.status = Status.FAILED;
    }
}