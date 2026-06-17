package com.saga.engine.repository;

import com.saga.engine.entity.StepExecution;
import com.saga.engine.enums.StepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StepExecutionRepository extends JpaRepository<StepExecution, Long> {

    List<StepExecution> findBySagaInstanceIdOrderByExecutionOrder(Long sagaInstanceId);

    Optional<StepExecution> findBySagaInstanceIdAndStepId(Long sagaInstanceId, String stepId);

    List<StepExecution> findBySagaInstanceIdAndStatus(Long sagaInstanceId, StepStatus status);

    @Query("SELECT s FROM StepExecution s WHERE s.sagaInstanceId = :instanceId AND s.status = :status ORDER BY s.executionOrder")
    List<StepExecution> findCompletedSteps(@Param("instanceId") Long instanceId, @Param("status") StepStatus status);

    @Query("SELECT s.stepName, COUNT(s) FROM StepExecution s WHERE s.status = 'FAILED' GROUP BY s.stepName ORDER BY COUNT(s) DESC")
    List<Object[]> findTopFailedSteps();

    @Query("SELECT s FROM StepExecution s WHERE s.sagaInstanceId = :instanceId AND s.stepType = 'PARALLEL' AND s.status IN :statuses")
    List<StepExecution> findParallelStepsByInstanceAndStatuses(@Param("instanceId") Long instanceId, @Param("statuses") List<StepStatus> statuses);
}
