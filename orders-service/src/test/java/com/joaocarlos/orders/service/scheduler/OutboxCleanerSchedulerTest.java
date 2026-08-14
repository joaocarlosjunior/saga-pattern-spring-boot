package com.joaocarlos.orders.service.scheduler;

import com.joaocarlos.orders.dao.jpa.entity.OutboxStatus;
import com.joaocarlos.orders.dao.jpa.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OutboxCleanerSchedulerTest {

    private OutboxRepository outboxRepository;
    private OutboxCleanerScheduler cleanerScheduler;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(OutboxRepository.class);
        cleanerScheduler = new OutboxCleanerScheduler(outboxRepository, 7);
    }

    @Test
    void cleanProcessedOutboxEvents_shouldDeleteProcessedRecordsOlderThanCutoff() {
        when(outboxRepository.deleteByStatusAndProcessedAtBefore(eq(OutboxStatus.PROCESSED), any(Instant.class)))
                .thenReturn(5);

        cleanerScheduler.cleanProcessedOutboxEvents();

        verify(outboxRepository, times(1))
                .deleteByStatusAndProcessedAtBefore(eq(OutboxStatus.PROCESSED), any(Instant.class));
    }
}
