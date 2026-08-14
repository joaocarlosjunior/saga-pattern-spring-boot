package com.joaocarlos.orchestrator.dao.jpa.repository;

import com.joaocarlos.orchestrator.dao.jpa.entity.OutboxEntity;
import com.joaocarlos.orchestrator.dao.jpa.entity.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEntity, UUID> {

    List<OutboxEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

    @Modifying
    @Query("DELETE FROM OutboxEntity o WHERE o.status = :status AND o.processedAt < :cutoff")
    int deleteByStatusAndProcessedAtBefore(@Param("status") OutboxStatus status, @Param("cutoff") Instant cutoff);
}
