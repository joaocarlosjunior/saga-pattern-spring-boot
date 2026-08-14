package com.joaocarlos.payments.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joaocarlos.payments.dao.jpa.entity.OutboxEntity;
import com.joaocarlos.payments.dao.jpa.entity.OutboxStatus;
import com.joaocarlos.payments.dao.jpa.repository.OutboxRepository;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class OutboxServiceImpl implements OutboxService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxServiceImpl.class);

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxServiceImpl(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void publishEvent(String aggregateType, String aggregateId, String eventType, String topic, Object eventPayload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(eventPayload);
            OutboxEntity entity = new OutboxEntity();
            entity.setAggregateType(aggregateType);
            entity.setAggregateId(aggregateId);
            entity.setEventType(eventType);
            entity.setTopic(topic);
            entity.setPayload(payloadJson);
            entity.setStatus(OutboxStatus.PENDING);
            entity.setCreatedAt(Instant.now());
            entity.setRetryCount(0);

            Map<String, String> carrier = new HashMap<>();
            GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(
                    Context.current(),
                    carrier,
                    (c, key, value) -> {
                        if (c != null && key != null && value != null) {
                            c.put(key, value);
                        }
                    }
            );
            entity.setTraceParent(carrier.get("traceparent"));

            outboxRepository.save(entity);
            LOGGER.info("Saved outbox record for eventType: {} aggregateId: {} topic: {} traceparent: {}", eventType, aggregateId, topic, entity.getTraceParent());
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize event payload for eventType: {} aggregateId: {}", eventType, aggregateId, e);
            throw new RuntimeException("Error serializing outbox payload", e);
        }
    }
}
