package com.artheus.deliveryservice.delivery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class DeliveryProcessorTest {

    @Mock
    private DeliveryAttemptRepository repository;

    private Clock clock;
    private DeliveryProcessor processor;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-09-29T10:00:00Z"), ZoneId.of("UTC"));
        processor = new DeliveryProcessor(repository, clock);

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(processor, "restClient", builder.build());
    }

    @Test
    @DisplayName("Should process successfully and mark status as SUCCESS when endpoint responds 200 OK")
    void shouldMarkSuccessWhenHttpCallSucceeds() {
        UUID publicId = UUID.randomUUID();
        String targetUrl = "https://webhook.site/test";
        String payload = "{\"event\":\"order_created\"}";

        DeliveryAttempt attempt = spy(DeliveryAttempt.create(
                UUID.randomUUID(), UUID.randomUUID(), targetUrl, payload, clock
        ));

        when(repository.findByPublicId(publicId)).thenReturn(Optional.of(attempt));

        mockServer.expect(requestTo(targetUrl))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(payload))
                .andRespond(withSuccess());

        processor.process(publicId);

        mockServer.verify();
        verify(attempt).markProcessing(clock);
        verify(attempt).markSuccess();
        verify(repository, times(2)).save(attempt); // Saved at start and completion
    }

    @Test
    @DisplayName("Should handle HTTP failure and mark status as FAILED when endpoint returns 500 error")
    void shouldMarkFailedWhenHttpCallReturnsError() {
        UUID publicId = UUID.randomUUID();
        String targetUrl = "https://webhook.site/test";

        DeliveryAttempt attempt = spy(DeliveryAttempt.create(
                UUID.randomUUID(), UUID.randomUUID(), targetUrl, "{}", clock
        ));

        when(repository.findByPublicId(publicId)).thenReturn(Optional.of(attempt));

        mockServer.expect(requestTo(targetUrl))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        processor.process(publicId);

        mockServer.verify();
        verify(attempt).markProcessing(clock);
        verify(attempt).markFailed();
        verify(repository, times(2)).save(attempt);
    }

    @Test
    @DisplayName("Should throw exception when attempt is not found in database")
    void shouldThrowExceptionWhenAttemptNotFound() {
        UUID publicId = UUID.randomUUID();
        when(repository.findByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor.process(publicId))
                .isInstanceOf(DeliveryAttemptNotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Should deliver the payload as application/json")
    void shouldSendPayloadAsJson() {
        UUID publicId = UUID.randomUUID();
        String targetUrl = "https://webhook.site/test";
        DeliveryAttempt attempt = spy(DeliveryAttempt.create(
                UUID.randomUUID(), UUID.randomUUID(), targetUrl, "{\"event\":\"order_created\"}", clock));
        when(repository.findByPublicId(publicId)).thenReturn(Optional.of(attempt));

        mockServer.expect(requestTo(targetUrl))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess());

        processor.process(publicId);

        mockServer.verify();
    }

    @Test
    @DisplayName("Should encode the payload as UTF-8")
    void shouldEncodePayloadAsUtf8() {
        UUID publicId = UUID.randomUUID();
        String targetUrl = "https://webhook.site/test";
        String payload = "{\"nome\":\"ação\"}";
        DeliveryAttempt attempt = spy(DeliveryAttempt.create(
                UUID.randomUUID(), UUID.randomUUID(), targetUrl, payload, clock));
        when(repository.findByPublicId(publicId)).thenReturn(Optional.of(attempt));

        mockServer.expect(requestTo(targetUrl))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().bytes(payload.getBytes(StandardCharsets.UTF_8)))
                .andRespond(withSuccess());

        processor.process(publicId);

        mockServer.verify();
    }
}