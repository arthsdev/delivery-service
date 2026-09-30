package com.artheus.deliveryservice.delivery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryAttemptServiceTest {

    @Mock
    private DeliveryAttemptRepository repository;

    @Mock
    private DeliveryProcessor processor;

    private Clock clock;
    private DeliveryAttemptService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-09-29T10:00:00Z"), ZoneId.of("UTC"));
        service = new DeliveryAttemptService(repository, processor, clock);
    }

    @Test
    @DisplayName("Should create delivery attempt, save to database, and trigger processor")
    void shouldCreateDeliveryAttemptAndTriggerProcessor() {
        UUID eventId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        DeliveryRequest request = new DeliveryRequest(eventId, subscriptionId, "https://example.com/webhook", "{\"payload\":\"data\"}");

        DeliveryResponse response = service.create(request);

        ArgumentCaptor<DeliveryAttempt> captor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        verify(repository, times(1)).save(captor.capture());

        DeliveryAttempt savedAttempt = captor.getValue();
        assertThat(savedAttempt.getEventId()).isEqualTo(eventId);
        assertThat(savedAttempt.getTargetUrl()).isEqualTo("https://example.com/webhook");

        verify(processor, times(1)).process(savedAttempt.getPublicId());
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("Should return DeliveryResponse when publicId is found")
    void shouldFindDeliveryAttemptByPublicId() {
        UUID publicId = UUID.randomUUID();
        DeliveryAttempt attempt = DeliveryAttempt.create(
                UUID.randomUUID(), UUID.randomUUID(), "https://example.com/webhook", "{}", clock
        );

        when(repository.findByPublicId(publicId)).thenReturn(Optional.of(attempt));

        DeliveryResponse response = service.findByPublicId(publicId);

        assertThat(response).isNotNull();
        verify(repository, times(1)).findByPublicId(publicId);
    }

    @Test
    @DisplayName("Should throw exception when publicId is not found")
    void shouldThrowExceptionWhenPublicIdNotFound() {
        UUID publicId = UUID.randomUUID();
        when(repository.findByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByPublicId(publicId))
                .isInstanceOf(DeliveryAttemptNotFoundException.class);
    }
}