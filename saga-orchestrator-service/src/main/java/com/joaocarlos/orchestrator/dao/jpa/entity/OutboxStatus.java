package com.joaocarlos.orchestrator.dao.jpa.entity;

public enum OutboxStatus {
    PENDING,
    PROCESSED,
    FAILED
}
