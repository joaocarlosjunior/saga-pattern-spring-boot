package com.joaocarlos.orders.service.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joaocarlos.core.dto.events.OrderCreatedEvent;
import com.joaocarlos.orders.dao.jpa.entity.OutboxEntity;
import com.joaocarlos.orders.dao.jpa.entity.OutboxStatus;
import com.joaocarlos.orders.dao.jpa.repository.OutboxRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxPublisherSchedulerTest {

    private OutboxRepository outboxRepository;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private ObjectMapper objectMapper;
    private OutboxPublisherScheduler publisherScheduler;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(OutboxRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper();
        publisherScheduler = new OutboxPublisherScheduler(outboxRepository, kafkaTemplate, objectMapper, 50, 3);
    }

    @Test
    void processOutboxEvents_shouldPublishEventAndUpdateStatusToProcessed() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(orderId, UUID.randomUUID(), UUID.randomUUID(), 1);

        OutboxEntity entity = new OutboxEntity();
        entity.setId(UUID.randomUUID());
        entity.setAggregateType("ORDER");
        entity.setAggregateId(orderId.toString());
        entity.setEventType(OrderCreatedEvent.class.getName());
        entity.setTopic("orders-events");
        entity.setPayload(objectMapper.writeValueAsString(event));
        entity.setStatus(OutboxStatus.PENDING);
        entity.setCreatedAt(Instant.now());
        entity.setRetryCount(0);
        entity.setTraceParent("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(entity));

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        publisherScheduler.processOutboxEvents();

        assertEquals(OutboxStatus.PROCESSED, entity.getStatus());
        assertNotNull(entity.getProcessedAt());
        verify(outboxRepository, times(1)).save(entity);
    }

    @Test
    void processOutboxEvents_shouldIncrementRetryCountAndMarkFailedOnMaxRetries() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(orderId, UUID.randomUUID(), UUID.randomUUID(), 1);

        OutboxEntity entity = new OutboxEntity();
        entity.setId(UUID.randomUUID());
        entity.setAggregateType("ORDER");
        entity.setAggregateId(orderId.toString());
        entity.setEventType(OrderCreatedEvent.class.getName());
        entity.setTopic("orders-events");
        entity.setPayload(objectMapper.writeValueAsString(event));
        entity.setStatus(OutboxStatus.PENDING);
        entity.setCreatedAt(Instant.now());
        entity.setRetryCount(2);

        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(entity));

        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka error"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedFuture);

        publisherScheduler.processOutboxEvents();

        assertEquals(3, entity.getRetryCount());
        assertEquals(OutboxStatus.FAILED, entity.getStatus());
        verify(outboxRepository, times(1)).save(entity);
    }
}
