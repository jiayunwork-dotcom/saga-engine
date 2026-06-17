package com.saga.engine.repository;

import com.saga.engine.entity.DeadLetterQueue;
import com.saga.engine.enums.DeadLetterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeadLetterQueueRepository extends JpaRepository<DeadLetterQueue, Long> {

    List<DeadLetterQueue> findByStatus(DeadLetterStatus status);

    Page<DeadLetterQueue> findByStatus(DeadLetterStatus status, Pageable pageable);

    Optional<DeadLetterQueue> findBySagaInstanceIdAndStepId(Long sagaInstanceId, String stepId);

    List<DeadLetterQueue> findBySagaInstanceId(Long sagaInstanceId);

    long countByStatus(DeadLetterStatus status);
}
