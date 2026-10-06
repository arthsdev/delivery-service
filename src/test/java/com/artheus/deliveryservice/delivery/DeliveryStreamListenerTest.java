package com.artheus.deliveryservice.delivery;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryStreamListenerTest {

    private static final String STREAM_KEY = "test-stream";
    private static final String GROUP = "test-group";

    @Mock
    private DeliveryAttemptService deliveryAttemptService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, String, String> streamOperations;

    @Captor
    private ArgumentCaptor<DeliveryRequest> requestCaptor;

    private DeliveryStreamListener listener;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        listener = new DeliveryStreamListener(
                deliveryAttemptService, redisTemplate, validator, STREAM_KEY, GROUP);
    }

    private MapRecord<String, String, String> messageWith(Map<String, String> fields) {
        return StreamRecords.newRecord()
                .in(STREAM_KEY)
                .ofMap(fields)
                .withId(RecordId.of("1-0"));
    }

    private Map<String, String> validFields(UUID eventId, UUID subscriptionId) {
        return Map.of(
                "eventId", eventId.toString(),
                "subscriptionId", subscriptionId.toString(),
                "targetUrl", "https://webhook.site/test",
                "payload", "{\"orderId\":123}",
                "schemaVersion", "1"
        );
    }

    @Test
    void shouldCreateDeliveryAndAcknowledgeWhenMessageIsValid() {
        UUID eventId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        MapRecord<String, String, String> message = messageWith(validFields(eventId, subscriptionId));

        doReturn(streamOperations).when(redisTemplate).opsForStream();

        listener.onMessage(message);

        verify(deliveryAttemptService).create(requestCaptor.capture());
        DeliveryRequest request = requestCaptor.getValue();
        assertEquals(eventId, request.eventId());
        assertEquals(subscriptionId, request.subscriptionId());
        assertEquals("https://webhook.site/test", request.targetUrl());
        assertEquals("{\"orderId\":123}", request.payload());

        verify(streamOperations).acknowledge(STREAM_KEY, GROUP, message.getId());
    }

    @Test
    void shouldNotAcknowledgeWhenCreateFails() {
        UUID eventId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        MapRecord<String, String, String> message = messageWith(validFields(eventId, subscriptionId));

        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(deliveryAttemptService).create(org.mockito.ArgumentMatchers.any());

        listener.onMessage(message);

        verify(deliveryAttemptService).create(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void shouldAcknowledgeAndSkipCreateWhenUuidIsInvalid() {
        Map<String, String> invalidFields = Map.of(
                "eventId", "not-a-uuid",
                "subscriptionId", UUID.randomUUID().toString(),
                "targetUrl", "https://webhook.site/test",
                "payload", "{\"orderId\":123}"
        );
        MapRecord<String, String, String> message = messageWith(invalidFields);

        doReturn(streamOperations).when(redisTemplate).opsForStream();

        listener.onMessage(message);

        verifyNoInteractions(deliveryAttemptService);
        verify(streamOperations).acknowledge(STREAM_KEY, GROUP, message.getId());
    }

    @Test
    void shouldAcknowledgeAndSkipCreateWhenTargetUrlIsBlank() {
        UUID eventId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Map<String, String> invalidFields = Map.of(
                "eventId", eventId.toString(),
                "subscriptionId", subscriptionId.toString(),
                "targetUrl", " ",
                "payload", "{\"orderId\":123}"
        );
        MapRecord<String, String, String> message = messageWith(invalidFields);

        doReturn(streamOperations).when(redisTemplate).opsForStream();

        listener.onMessage(message);

        verifyNoInteractions(deliveryAttemptService);
        verify(streamOperations).acknowledge(STREAM_KEY, GROUP, message.getId());
    }
}