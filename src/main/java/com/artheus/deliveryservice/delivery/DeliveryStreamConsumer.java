package com.artheus.deliveryservice.delivery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "event-stream.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class DeliveryStreamConsumer implements ApplicationRunner {

    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final StringRedisTemplate redisTemplate;
    private final String streamKey;
    private final String groupName;
    private final String consumerName;

    public DeliveryStreamConsumer(
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container,
            StringRedisTemplate redisTemplate,
            @Value("${event-stream.deliveries-key}") String streamKey,
            @Value("${event-stream.group}") String groupName,
            @Value("${event-stream.consumer-name}") String consumerName) {
        this.container = container;
        this.redisTemplate = redisTemplate;
        this.streamKey = streamKey;
        this.groupName = groupName;
        this.consumerName = consumerName;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), groupName);
        } catch (RedisSystemException e) {
            if (!isBusyGroupException(e)) {
                throw e;
            }
            log.info("Consumer group '{}' already exists for stream '{}', continuing startup.", groupName, streamKey);
        }

        StreamListener<String, MapRecord<String, String, String>> listener = message -> {
            log.info("Message received from Stream - ID: {}, Stream: {}, Payload: {}",
                    message.getId(), message.getStream(), message.getValue());
        };

        container.receive(
                Consumer.from(groupName, consumerName),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                listener
        );
    }

    private boolean isBusyGroupException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}