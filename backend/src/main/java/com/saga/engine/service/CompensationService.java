package com.saga.engine.service;

import com.saga.engine.entity.DeadLetterQueue;
import com.saga.engine.entity.SagaDefinition;
import com.saga.engine.entity.SagaInstance;
import com.saga.engine.entity.StepExecution;
import com.saga.engine.enums.DeadLetterStatus;
import com.saga.engine.enums.EventType;
import com.saga.engine.enums.FailureType;
import com.saga.engine.enums.SagaStatus;
import com.saga.engine.enums.StepStatus;
import com.saga.engine.event.SagaEvent;
import com.saga.engine.repository.DeadLetterQueueRepository;
import com.saga.engine.repository.SagaDefinitionRepository;
import com.saga.engine.repository.SagaInstanceRepository;
import com.saga.engine.repository.StepExecutionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationService {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final StepExecutionRepository stepExecutionRepository;
    private final SagaDefinitionRepository sagaDefinitionRepository;
    private final DeadLetterQueueRepository deadLetterQueueRepository;
    private final EventBusService eventBusService;
    private final DistributedLockService distributedLockService;
    private final RetryService retryService;
    private final HttpCallerService httpCallerService;
    private final ObjectMapper objectMapper;
    private final ExpressionEvaluatorService expressionEvaluatorService;

    @Value("${saga.compensation-max-retries:5}")
    private int compensationMaxRetries;

    @Value("${saga.compensation-base-delay-ms:2000}")
    private long compensationBaseDelayMs;

    @Value("${saga.compensation-max-delay-ms:60000}")
    private long compensationMaxDelayMs;

    @Async
    public void startCompensation(Long instanceId) {
        String lockKey = "compensation-" + instanceId;
        
        try {
            if (!distributedLockService.tryLock(lockKey, 600)) {
                log.warn("Could not acquire compensation lock for instance: {}", instanceId);
                return;
            }

            executeCompensation(instanceId);
            
        } catch (Exception e) {
            log.error("Error during compensation for instance: {}", instanceId, e);
        } finally {
            distributedLockService.unlock(lockKey);
        }
    }

    @Transactional
    public void executeCompensation(Long instanceId) {
        SagaInstance instance = sagaInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("Saga instance not found: " + instanceId));

        if (instance.getStatus() != SagaStatus.COMPENSATING) {
            log.warn("Instance {} is not in compensating state: {}", instanceId, instance.getStatus());
            return;
        }

        List<StepExecution> allSteps = stepExecutionRepository
                .findBySagaInstanceIdOrderByExecutionOrder(instanceId);

        List<StepExecution> completedSteps = new ArrayList<>();
        for (int i = allSteps.size() - 1; i >= 0; i--) {
            StepExecution step = allSteps.get(i);
            if (step.getStatus() == StepStatus.COMPLETED) {
                completedSteps.add(step);
            }
        }

        log.info("Starting compensation for instance {}, {} steps to compensate", 
                instanceId, completedSteps.size());

        boolean allCompensated = true;
        StepExecution failedStep = null;

        for (StepExecution step : completedSteps) {
            try {
                compensateStep(step, instance);
            } catch (Exception e) {
                log.error("Compensation failed for step {}: {}", step.getStepId(), e.getMessage());
                allCompensated = false;
                failedStep = step;
                addToDeadLetterQueue(instance, step, e.getMessage());
                break;
            }
        }

        if (allCompensated) {
            instance.setStatus(SagaStatus.FAILED);
            instance.setCompletedAt(LocalDateTime.now());
            sagaInstanceRepository.save(instance);

            eventBusService.publishEvent(new SagaEvent(EventType.SAGA_FAILED, instanceId));
            log.info("Compensation completed for instance: {}", instanceId);
        } else {
            instance.setStatus(SagaStatus.NEED_INTERVENTION);
            sagaInstanceRepository.save(instance);
            log.warn("Compensation failed for instance {}, needs manual intervention", instanceId);
        }
    }

    private void compensateStep(StepExecution step, SagaInstance instance) throws Exception {
        eventBusService.publishEvent(new SagaEvent(
                EventType.COMPENSATION_STARTED, instance.getId(), step.getStepId()));

        Map<String, Object> stepDef = getStepDefinition(instance, step.getStepId());
        if (stepDef == null) {
            log.warn("No step definition found for step: {}", step.getStepId());
            return;
        }

        Map<String, Object> compensation = (Map<String, Object>) stepDef.get("compensationAction");
        if (compensation == null) {
            log.info("No compensation action defined for step: {}", step.getStepId());
            return;
        }

        String url = (String) compensation.get("url");
        String method = (String) compensation.getOrDefault("method", "POST");
        String bodyTemplate = (String) compensation.get("body");

        boolean success = false;
        String lastError = null;

        for (int attempt = 0; attempt <= compensationMaxRetries; attempt++) {
            if (attempt > 0) {
                long delay = retryService.calculateExponentialDelay(
                        attempt, compensationBaseDelayMs, compensationMaxDelayMs);
                retryService.sleep(delay);
            }

            try {
                String idempotencyKey = instance.getId() + "-" + step.getStepId() + "-comp-" + attempt;
                
                Map<String, String> headers = new HashMap<>();
                headers.put("X-Saga-Idempotency-Key", idempotencyKey);
                headers.put("X-Saga-Compensation", "true");

                Map<String, Object> stepsContext = new HashMap<>();
                if (step.getResponseBody() != null) {
                    try {
                        Map<String, Object> responseMap = objectMapper.readValue(
                                step.getResponseBody(), 
                                new TypeReference<Map<String, Object>>() {});
                        stepsContext.put(step.getStepId() + "_response", responseMap);
                    } catch (Exception e) {
                        stepsContext.put(step.getStepId() + "_response", step.getResponseBody());
                    }
                }

                String resolvedUrl = expressionEvaluatorService.resolveTemplate(url, stepsContext, instance.getInputData());
                String body = bodyTemplate != null ? expressionEvaluatorService.resolveTemplate(bodyTemplate, stepsContext, instance.getInputData()) : null;

                HttpCallerService.HttpResult result = httpCallerService.executeCall(
                        resolvedUrl, method, body, headers, 30);

                if (result.isSuccess()) {
                    success = true;
                    break;
                } else {
                    lastError = result.getErrorMessage() != null ? 
                            result.getErrorMessage() : "HTTP " + result.getStatusCode();
                }
            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn("Compensation attempt {} failed for step {}: {}", 
                        attempt + 1, step.getStepId(), e.getMessage());
            }
        }

        if (!success) {
            eventBusService.publishEvent(new SagaEvent(
                    EventType.COMPENSATION_FAILED, instance.getId(), step.getStepId()));
            throw new RuntimeException("Compensation failed after " + compensationMaxRetries + 
                    " retries: " + lastError);
        }

        eventBusService.publishEvent(new SagaEvent(
                EventType.COMPENSATION_COMPLETED, instance.getId(), step.getStepId()));

        log.info("Compensation completed for step: {}", step.getStepId());
    }

    private Map<String, Object> getStepDefinition(SagaInstance instance, String stepId) {
        try {
            SagaDefinition definition = sagaDefinitionRepository
                    .findByNameAndVersion(instance.getSagaDefinitionName(), 
                            instance.getSagaDefinitionVersion())
                    .orElse(null);

            if (definition != null) {
                List<Map<String, Object>> steps = definition.getDefinition();
                for (Map<String, Object> step : steps) {
                    if (stepId.equals(step.get("id"))) {
                        return step;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting step definition", e);
        }
        return null;
    }

    @Transactional
    public void addToDeadLetterQueue(SagaInstance instance, StepExecution step, String errorMessage) {
        Optional<DeadLetterQueue> existing = deadLetterQueueRepository
                .findBySagaInstanceIdAndStepId(instance.getId(), step.getStepId());

        if (existing.isPresent()) {
            DeadLetterQueue dlq = existing.get();
            dlq.setRetryCount(dlq.getRetryCount() + 1);
            dlq.setErrorMessage(errorMessage);
            dlq.setStatus(DeadLetterStatus.PENDING);
            deadLetterQueueRepository.save(dlq);
        } else {
            DeadLetterQueue dlq = new DeadLetterQueue();
            dlq.setSagaInstanceId(instance.getId());
            dlq.setStepId(step.getStepId());
            dlq.setStepName(step.getStepName());
            dlq.setFailureType(FailureType.COMPENSATION_FAILED);
            dlq.setErrorMessage(errorMessage);
            dlq.setRetryCount(0);
            dlq.setStatus(DeadLetterStatus.PENDING);
            deadLetterQueueRepository.save(dlq);
        }
    }

    @Transactional
    public DeadLetterQueue retryDeadLetter(Long dlqId, String operator) {
        DeadLetterQueue dlq = deadLetterQueueRepository.findById(dlqId)
                .orElseThrow(() -> new RuntimeException("Dead letter not found: " + dlqId));

        dlq.setStatus(DeadLetterStatus.PROCESSING);
        dlq.setRetryCount(dlq.getRetryCount() + 1);
        deadLetterQueueRepository.save(dlq);

        SagaInstance instance = sagaInstanceRepository.findById(dlq.getSagaInstanceId())
                .orElseThrow(() -> new RuntimeException("Saga instance not found"));

        StepExecution step = stepExecutionRepository
                .findBySagaInstanceIdAndStepId(dlq.getSagaInstanceId(), dlq.getStepId())
                .orElseThrow(() -> new RuntimeException("Step execution not found"));

        try {
            compensateStep(step, instance);
            
            dlq.setStatus(DeadLetterStatus.RESOLVED);
            dlq.setHandledBy(operator);
            dlq.setHandledAt(LocalDateTime.now());
            deadLetterQueueRepository.save(dlq);

            checkAllDeadLettersResolved(instance);

        } catch (Exception e) {
            dlq.setStatus(DeadLetterStatus.PENDING);
            dlq.setErrorMessage(e.getMessage());
            deadLetterQueueRepository.save(dlq);
        }

        return dlq;
    }

    @Transactional
    public DeadLetterQueue markAsHandled(Long dlqId, String operator) {
        DeadLetterQueue dlq = deadLetterQueueRepository.findById(dlqId)
                .orElseThrow(() -> new RuntimeException("Dead letter not found: " + dlqId));

        dlq.setStatus(DeadLetterStatus.HANDLED);
        dlq.setHandledBy(operator);
        dlq.setHandledAt(LocalDateTime.now());
        deadLetterQueueRepository.save(dlq);

        SagaInstance instance = sagaInstanceRepository.findById(dlq.getSagaInstanceId()).orElse(null);
        if (instance != null) {
            checkAllDeadLettersResolved(instance);
        }

        return dlq;
    }

    private void checkAllDeadLettersResolved(SagaInstance instance) {
        List<DeadLetterQueue> pending = deadLetterQueueRepository
                .findBySagaInstanceId(instance.getId());

        boolean allResolved = pending.stream()
                .allMatch(dlq -> dlq.getStatus() == DeadLetterStatus.RESOLVED 
                        || dlq.getStatus() == DeadLetterStatus.HANDLED);

        if (allResolved) {
            instance.setStatus(SagaStatus.FAILED);
            instance.setCompletedAt(LocalDateTime.now());
            sagaInstanceRepository.save(instance);
        }
    }

    public List<DeadLetterQueue> getDeadLetterQueue(DeadLetterStatus status) {
        if (status != null) {
            return deadLetterQueueRepository.findByStatus(status);
        }
        return deadLetterQueueRepository.findAll();
    }

    public long countDeadLettersByStatus(DeadLetterStatus status) {
        return deadLetterQueueRepository.countByStatus(status);
    }
}
