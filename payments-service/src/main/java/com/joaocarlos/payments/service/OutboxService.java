package com.joaocarlos.payments.service;

public interface OutboxService {
    void publishEvent(String aggregateType, String aggregateId, String eventType, String topic, Object eventPayload);
}
