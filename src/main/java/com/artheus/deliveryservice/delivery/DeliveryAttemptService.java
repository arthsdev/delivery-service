package com.artheus.deliveryservice.delivery;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryAttemptService {

    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final DeliveryProcessor deliveryProcessor;
    private final Clock clock;

    @Transactional
    public DeliveryResponse create(DeliveryRequest request) {
        DeliveryAttempt attempt = DeliveryAttempt.create(
                request.eventId(),
                request.subscriptionId(),
                request.targetUrl(),
                request.payload(),
                clock
        );

        deliveryAttemptRepository.save(attempt);

        deliveryProcessor.process(attempt.getPublicId());

        return DeliveryResponse.from(attempt);
    }

    @Transactional(readOnly = true)
    public DeliveryResponse findByPublicId(UUID publicId) {
        DeliveryAttempt attempt = deliveryAttemptRepository.findByPublicId(publicId)
                .orElseThrow(() -> new DeliveryAttemptNotFoundException(publicId));

        return DeliveryResponse.from(attempt);
    }
}