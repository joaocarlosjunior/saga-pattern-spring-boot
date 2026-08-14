package com.joaocarlos.orders.service.scheduler;

import com.joaocarlos.orders.dao.jpa.entity.OutboxStatus;
import com.joaocarlos.orders.dao.jpa.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class OutboxCleanerScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxCleanerScheduler.class);

    private final OutboxRepository outboxRepository;
    private final int retentionDays;

    public OutboxCleanerScheduler(
            OutboxRepository outboxRepository,
            @Value("${orders.outbox.retention-days:7}") int retentionDays) {
        this.outboxRepository = outboxRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${orders.outbox.cleaner.cron:0 0 3 * * *}")
    @Transactional
    public void cleanProcessedOutboxEvents() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        LOGGER.info("Starting cleanup of PROCESSED outbox records older than {} days (cutoff: {})...", retentionDays, cutoff);

        int deletedCount = outboxRepository.deleteByStatusAndProcessedAtBefore(OutboxStatus.PROCESSED, cutoff);

        LOGGER.info("Finished outbox cleanup. Removed {} PROCESSED record(s).", deletedCount);
    }
}
