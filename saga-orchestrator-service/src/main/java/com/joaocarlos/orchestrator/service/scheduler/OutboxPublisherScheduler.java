package com.joaocarlos.orchestrator.service.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joaocarlos.orchestrator.dao.jpa.entity.OutboxEntity;
import com.joaocarlos.orchestrator.dao.jpa.entity.OutboxStatus;
import com.joaocarlos.orchestrator.dao.jpa.repository.OutboxRepository;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisherScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisherScheduler.class);

    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier != null ? carrier.keySet() : Collections.emptyList();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier != null ? carrier.get(key) : null;
        }
    };

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final int maxRetries;

    public OutboxPublisherScheduler(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${orchestrator.outbox.publisher.batch-size:50}") int batchSize,
            @Value("${orchestrator.outbox.publisher.max-retries:3}") int maxRetries) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
    }

    @Scheduled(fixedDelayString = "${orchestrator.outbox.publisher.fixed-delay-ms:5000}")
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEntity> pendingEvents = outboxRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, batchSize)
        );

        if (pendingEvents.isEmpty()) {
            return;
        }

        LOGGER.info("Found {} PENDING outbox events to process.", pendingEvents.size());

        for (OutboxEntity entity : pendingEvents) {
            try {
                Object payloadObj;
                try {
                    Class<?> clazz = Class.forName(entity.getEventType());
                    payloadObj = objectMapper.readValue(entity.getPayload(), clazz);
                } catch (ClassNotFoundException e) {
                    LOGGER.warn("Class not found for eventType {}, using raw payload object.", entity.getEventType());
                    payloadObj = objectMapper.readValue(entity.getPayload(), Object.class);
                }

                ProducerRecord<String, Object> record = new ProducerRecord<>(
                        entity.getTopic(),
                        entity.getAggregateId(),
                        payloadObj
                );

                String traceParent = entity.getTraceParent();
                if (traceParent != null && !traceParent.isBlank()) {
                    record.headers().add("traceparent", traceParent.getBytes(StandardCharsets.UTF_8));

                    Map<String, String> carrier = Collections.singletonMap("traceparent", traceParent);
                    Context extractedContext = GlobalOpenTelemetry.getPropagators().getTextMapPropagator().extract(
                            Context.current(),
                            carrier,
                            MAP_GETTER
                    );

                    Tracer tracer = GlobalOpenTelemetry.getTracer("outbox-publisher");
                    Span span = tracer.spanBuilder("outbox_publish " + entity.getTopic())
                            .setParent(extractedContext)
                            .setSpanKind(SpanKind.PRODUCER)
                            .startSpan();

                    try (Scope scope = span.makeCurrent()) {
                        kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
                    } catch (Exception ex) {
                        span.recordException(ex);
                        throw ex;
                    } finally {
                        span.end();
                    }
                } else {
                    kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
                }

                entity.setStatus(OutboxStatus.PROCESSED);
                entity.setProcessedAt(Instant.now());
                outboxRepository.save(entity);

                LOGGER.info("Successfully published outbox event id: {} eventType: {} to topic: {}",
                        entity.getId(), entity.getEventType(), entity.getTopic());
            } catch (Exception e) {
                int newRetryCount = entity.getRetryCount() + 1;
                entity.setRetryCount(newRetryCount);

                if (newRetryCount >= maxRetries) {
                    entity.setStatus(OutboxStatus.FAILED);
                    LOGGER.error("Outbox event id: {} failed after {} retries. Marked as FAILED.", entity.getId(), newRetryCount, e);
                } else {
                    LOGGER.warn("Failed to publish outbox event id: {}. Retry count incremented to {}.", entity.getId(), newRetryCount, e);
                }

                outboxRepository.save(entity);
            }
        }
    }
}
