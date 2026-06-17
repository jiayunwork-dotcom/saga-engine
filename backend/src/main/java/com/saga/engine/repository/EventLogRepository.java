package com.saga.engine.repository;

import com.saga.engine.entity.EventLog;
import com.saga.engine.enums.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventLogRepository extends JpaRepository<EventLog, Long> {

    Optional<EventLog> findByEventId(String eventId);

    List<EventLog> findBySagaInstanceIdOrderByOccurredAt(Long sagaInstanceId);

    List<EventLog> findBySagaDefinitionIdOrderByOccurredAt(Long sagaDefinitionId);

    Page<EventLog> findBySagaInstanceId(Long sagaInstanceId, Pageable pageable);

    @Query("SELECT e FROM EventLog e WHERE e.sagaInstanceId = :instanceId AND e.eventType = :eventType ORDER BY e.occurredAt DESC")
    List<EventLog> findLatestByInstanceAndType(@Param("instanceId") Long instanceId, @Param("eventType") EventType eventType);

    @Query("SELECT e FROM EventLog e WHERE e.processed = false ORDER BY e.occurredAt ASC")
    List<EventLog> findUnprocessedEvents();

    @Query("SELECT e FROM EventLog e WHERE e.processed = false AND e.occurredAt < :beforeTime ORDER BY e.occurredAt ASC")
    List<EventLog> findUnprocessedEventsBefore(@Param("beforeTime") LocalDateTime beforeTime);

    @Query("SELECT e FROM EventLog e WHERE e.sagaInstanceId = :instanceId AND e.occurredAt BETWEEN :startTime AND :endTime ORDER BY e.occurredAt")
    List<EventLog> findByInstanceAndTimeRange(@Param("instanceId") Long instanceId,
                                              @Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);
}
