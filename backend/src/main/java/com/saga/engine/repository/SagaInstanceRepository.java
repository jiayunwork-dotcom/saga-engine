package com.saga.engine.repository;

import com.saga.engine.entity.SagaInstance;
import com.saga.engine.enums.SagaStatus;
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
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, Long> {

    Optional<SagaInstance> findByCorrelationIdAndStatus(String correlationId, SagaStatus status);

    Optional<SagaInstance> findByCorrelationId(String correlationId);

    List<SagaInstance> findByStatus(SagaStatus status);

    Page<SagaInstance> findByStatus(SagaStatus status, Pageable pageable);

    Page<SagaInstance> findBySagaDefinitionNameContaining(String sagaDefinitionName, Pageable pageable);

    Page<SagaInstance> findByStatusAndSagaDefinitionNameContaining(SagaStatus status, String sagaDefinitionName, Pageable pageable);

    @Query("SELECT s FROM SagaInstance s WHERE s.status = :status AND s.startedAt BETWEEN :startTime AND :endTime")
    List<SagaInstance> findByStatusAndTimeRange(@Param("status") SagaStatus status,
                                                @Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

    @Query("SELECT s FROM SagaInstance s WHERE s.createdAt BETWEEN :startTime AND :endTime ORDER BY s.createdAt")
    List<SagaInstance> findByTimeRange(@Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(s) FROM SagaInstance s WHERE s.status = :status")
    long countByStatus(@Param("status") SagaStatus status);

    @Query("SELECT COUNT(s) FROM SagaInstance s WHERE s.sagaDefinitionName = :definitionName AND s.status = :status")
    long countByDefinitionNameAndStatus(@Param("definitionName") String definitionName, @Param("status") SagaStatus status);

    @Query("SELECT s.sagaDefinitionName, COUNT(s) FROM SagaInstance s WHERE s.createdAt BETWEEN :startTime AND :endTime GROUP BY s.sagaDefinitionName")
    List<Object[]> countByDefinitionNameInTimeRange(@Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime);

    @Query("SELECT FUNCTION('date_trunc', 'hour', s.createdAt) as hour, COUNT(s) " +
           "FROM SagaInstance s WHERE s.createdAt BETWEEN :startTime AND :endTime " +
           "GROUP BY hour ORDER BY hour")
    List<Object[]> countPerHour(@Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime);

    @Query("SELECT s FROM SagaInstance s WHERE s.status IN :statuses")
    List<SagaInstance> findByStatusIn(@Param("statuses") List<SagaStatus> statuses);
}
