package com.artheus.deliveryservice.delivery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryStreamConsumerTest {

    private static final String STREAM_KEY = "test-stream";
    private static final String GROUP = "test-group";
    private static final String CONSUMER = "test-consumer";
    private static final long WAIT_MS = 2000;

    @Mock
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;

    @Mock
    private StringRedisTemplate redisTemplate;

    @SuppressWarnings("rawtypes")
    @Mock
    private StreamOperations streamOperations; // raw on purpose: avoids generic clashes with opsForStream()

    @Mock
    private DeliveryStreamListener deliveryStreamListener;

    @Mock
    private ApplicationArguments args;

    private DeliveryStreamConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DeliveryStreamConsumer(
                container, redisTemplate, deliveryStreamListener,
                STREAM_KEY, GROUP, CONSUMER, Duration.ofMillis(10));
    }

    @Test
    @DisplayName("Should create the consumer group and register the listener on run")
    void shouldCreateGroupAndRegisterListenerOnRun() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);

        consumer.run(args);

        verify(container, timeout(WAIT_MS))
                .register(any(StreamMessageListenerContainer.StreamReadRequest.class), eq(deliveryStreamListener));
        verify(streamOperations).createGroup(eq(STREAM_KEY), eq(ReadOffset.from("0")), eq(GROUP));
    }

    @Test
    @DisplayName("Should continue when the consumer group already exists (BUSYGROUP)")
    void shouldContinueWhenGroupAlreadyExists() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        doThrow(new RedisSystemException("BUSYGROUP Consumer Group name already exists", new RuntimeException("BUSYGROUP")))
                .when(streamOperations).createGroup(anyString(), any(), anyString());

        consumer.run(args);

        verify(container, timeout(WAIT_MS))
                .register(any(StreamMessageListenerContainer.StreamReadRequest.class), eq(deliveryStreamListener));
    }

    @Test
    @DisplayName("Should keep retrying the setup while Redis is unreachable and register once it is back")
    void shouldRetryWhileRedisIsUnreachable() {
        when(redisTemplate.opsForStream())
                .thenThrow(new RedisConnectionFailureException("Connection refused"))
                .thenThrow(new RedisConnectionFailureException("Connection refused"))
                .thenReturn(streamOperations);

        consumer.run(args);

        verify(container, timeout(WAIT_MS))
                .register(any(StreamMessageListenerContainer.StreamReadRequest.class), eq(deliveryStreamListener));
        verify(redisTemplate, times(3)).opsForStream();
        verify(streamOperations, times(1)).createGroup(eq(STREAM_KEY), eq(ReadOffset.from("0")), eq(GROUP));
    }

    @Test
    @DisplayName("Should not retry errors that are not connectivity failures")
    void shouldNotRetryErrorsThatAreNotConnectivityFailures() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        doThrow(new RedisSystemException("WRONGTYPE Operation against a key holding the wrong kind of value",
                new RuntimeException("WRONGTYPE")))
                .when(streamOperations).createGroup(anyString(), any(), anyString());

        consumer.run(args);

        verify(streamOperations, timeout(WAIT_MS)).createGroup(anyString(), any(), anyString());
        verify(container, after(200).never())
                .register(any(StreamMessageListenerContainer.StreamReadRequest.class), any());
        verify(streamOperations, times(1)).createGroup(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Should stop the setup thread when the application shuts down (@PreDestroy)")
    void shouldStopSetupThreadOnShutdown() throws InterruptedException {
        when(redisTemplate.opsForStream()).thenThrow(new RedisConnectionFailureException("Down"));

        consumer.run(args);
        Thread setupThread = Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().equals("stream-consumer-setup"))
                .findFirst()
                .orElseThrow();

        consumer.stop();
        setupThread.join(WAIT_MS);

        assertThat(setupThread.isAlive()).isFalse();
    }
}