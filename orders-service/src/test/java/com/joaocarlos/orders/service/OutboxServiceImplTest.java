package com.joaocarlos.orders.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joaocarlos.core.dto.events.OrderCreatedEvent;
import com.joaocarlos.orders.dao.jpa.entity.OutboxEntity;
import com.joaocarlos.orders.dao.jpa.entity.OutboxStatus;
import com.joaocarlos.orders.dao.jpa.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxServiceImplTest {

    private OutboxRepository outboxRepository;
    private ObjectMapper objectMapper;
    private OutboxServiceImpl outboxService;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(OutboxRepository.class);
        objectMapper = new ObjectMapper();
        outboxService = new OutboxServiceImpl(outboxRepository, objectMapper);
    }

    @Test
    void publishEvent_shouldSavePendingOutboxEntity() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(orderId, customerId, productId, 2);

        outboxService.publishEvent(
                "ORDER",
                orderId.toString(),
                OrderCreatedEvent.class.getName(),
                "orders-events",
                event
        );

        ArgumentCaptor<OutboxEntity> captor = ArgumentCaptor.forClass(OutboxEntity.class);
        verify(outboxRepository, times(1)).save(captor.capture());

        OutboxEntity savedEntity = captor.getValue();
        assertEquals("ORDER", savedEntity.getAggregateType());
        assertEquals(orderId.toString(), savedEntity.getAggregateId());
        assertEquals(OrderCreatedEvent.class.getName(), savedEntity.getEventType());
        assertEquals("orders-events", savedEntity.getTopic());
        assertEquals(OutboxStatus.PENDING, savedEntity.getStatus());
        assertEquals(0, savedEntity.getRetryCount());
        assertNotNull(savedEntity.getCreatedAt());
        assertTrue(savedEntity.getPayload().contains(orderId.toString()));
    }
}
