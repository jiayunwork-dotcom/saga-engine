package com.saga.engine.service;

import com.saga.engine.dto.TriggerSagaRequest;
import com.saga.engine.entity.SagaDefinition;
import com.saga.engine.entity.SagaInstance;
import com.saga.engine.entity.StepExecution;
import com.saga.engine.enums.EventType;
import com.saga.engine.enums.SagaStatus;
import com.saga.engine.enums.StepStatus;
import com.saga.engine.enums.StepType;
import com.saga.engine.event.SagaEvent;
import com.saga.engine.repository.SagaInstanceRepository;
import com.saga.engine.repository.StepExecutionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaExecutionService {

    private final SagaDefinitionService sagaDefinitionService;
    private final SagaInstanceRepository sagaInstanceRepository;
    private final StepExecutionRepository stepExecutionRepository;
    private final EventBusService eventBusService;
    private final DistributedLockService distributedLockService;
    private final RetryService retryService;
    private final HttpCallerService httpCallerService;
    private final CompensationService compensationService;
    private final ObjectMapper objectMapper;
    private final ExpressionEvaluatorService expressionEvaluatorService;
    private final StringRedisTemplate redisTemplate;

    @Value("${saga.default-timeout-seconds:30}")
    private int defaultTimeoutSeconds;

    @Value("${saga.default-max-retries:3}")
    private int defaultMaxRetries;

    @Value("${saga.correlation-id-prefix:saga:correlation:}")
    private String correlationIdPrefix;

    @Value("${saga.default-retry-strategy:exponential}")
    private String defaultRetryStrategy;

    @Value("${saga.default-retry-base-delay-ms:1000}")
    private long defaultBaseDelayMs;

    @Value("${saga.default-retry-max-delay-ms:30000}")
    private long defaultMaxDelayMs;

    private final ExecutorService parallelExecutor = Executors.newFixedThreadPool(20);

    @Transactional
    public SagaInstance triggerSaga(TriggerSagaRequest request) {
        String correlationId = request.getCorrelationId();
        
        Optional<SagaInstance> existing = sagaInstanceRepository
                .findByCorrelationIdAndStatus(correlationId, SagaStatus.RUNNING);
        if (existing.isPresent()) {
            log.info("Saga instance already running for correlationId: {}", correlationId);
            return existing.get();
        }

        SagaDefinition definition;
        if (request.getVersion() != null) {
            definition = sagaDefinitionService.getDefinitionEntityByNameAndVersion(
                    request.getSagaName(), request.getVersion());
        } else {
            definition = sagaDefinitionService.getLatestDefinitionEntity(request.getSagaName());
        }

        SagaInstance instance = new SagaInstance();
        instance.setSagaDefinitionId(definition.getId());
        instance.setSagaDefinitionName(definition.getName());
        instance.setSagaDefinitionVersion(definition.getVersion());
        instance.setCorrelationId(correlationId);
        instance.setStatus(SagaStatus.CREATED);
        instance.setInputData(request.getInputData());
        instance.setStartedAt(LocalDateTime.now());

        SagaInstance savedInstance = sagaInstanceRepository.save(instance);

        initializeStepExecutions(savedInstance, definition);

        eventBusService.publishEvent(new SagaEvent(EventType.SAGA_STARTED, savedInstance.getId()));

        executeSagaAsync(savedInstance.getId());

        return savedInstance;
    }

    private void initializeStepExecutions(SagaInstance instance, SagaDefinition definition) {
        List<Map<String, Object>> steps = definition.getDefinition();
        int order = 0;

        for (Map<String, Object> step : steps) {
            StepExecution stepExec = new StepExecution();
            stepExec.setSagaInstanceId(instance.getId());
            stepExec.setStepId((String) step.get("id"));
            stepExec.setStepName((String) step.get("name"));
            stepExec.setStepType(StepType.valueOf(((String) step.getOrDefault("type", "SEQUENTIAL")).toUpperCase()));
            stepExec.setStatus(StepStatus.PENDING);
            stepExec.setExecutionOrder(order++);
            
            Map<String, Object> forwardAction = (Map<String, Object>) step.get("forwardAction");
            if (forwardAction != null) {
                stepExec.setRequestUrl((String) forwardAction.get("url"));
                stepExec.setRequestMethod((String) forwardAction.getOrDefault("method", "POST"));
                stepExec.setRequestBody((String) forwardAction.get("body"));
            }

            stepExec.setMaxRetries((Integer) step.getOrDefault("maxRetries", defaultMaxRetries));
            stepExec.setTimeoutSeconds((Integer) step.getOrDefault("timeoutSeconds", defaultTimeoutSeconds));

            stepExecutionRepository.save(stepExec);
        }
    }

    @Async
    public void executeSagaAsync(Long instanceId) {
        String lockKey = "saga-instance-" + instanceId;
        
        try {
            if (!distributedLockService.tryLock(lockKey, 300)) {
                log.warn("Could not acquire lock for saga instance: {}", instanceId);
                return;
            }

            executeSaga(instanceId);
            
        } catch (Exception e) {
            log.error("Error executing saga instance: {}", instanceId, e);
        } finally {
            distributedLockService.unlock(lockKey);
        }
    }

    @Transactional
    public void executeSaga(Long instanceId) {
        SagaInstance instance = sagaInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("Saga instance not found: " + instanceId));

        if (instance.getStatus() != SagaStatus.CREATED && instance.getStatus() != SagaStatus.RUNNING) {
            log.warn("Saga instance {} is not in runnable state: {}", instanceId, instance.getStatus());
            return;
        }

        SagaDefinition definition = sagaDefinitionService.getDefinitionEntityByNameAndVersion(
                instance.getSagaDefinitionName(), instance.getSagaDefinitionVersion());

        int globalTimeoutSeconds = definition.getGlobalTimeoutSeconds() != null
                ? definition.getGlobalTimeoutSeconds() : 300;

        instance.setStatus(SagaStatus.RUNNING);
        sagaInstanceRepository.save(instance);

        List<StepExecution> steps = stepExecutionRepository.findBySagaInstanceIdOrderByExecutionOrder(instanceId);
        List<Map<String, Object>> stepDefinitions = definition.getDefinition();

        try {
            if (expressionEvaluatorService.isTimeoutExceeded(instance.getStartedAt(), globalTimeoutSeconds)) {
                throw new RuntimeException("Saga global timeout exceeded before execution started");
            }

            executeSteps(steps, stepDefinitions, instance, globalTimeoutSeconds);
            
            instance.setStatus(SagaStatus.COMPLETED);
            instance.setCompletedAt(LocalDateTime.now());
            sagaInstanceRepository.save(instance);

            eventBusService.publishEvent(new SagaEvent(EventType.SAGA_COMPLETED, instanceId));

        } catch (Exception e) {
            log.error("Saga execution failed: {}", instanceId, e);
            handleSagaFailure(instance, e.getMessage());
        }
    }

    private void executeSteps(List<StepExecution> steps, List<Map<String, Object>> stepDefinitions,
                              SagaInstance instance, int globalTimeoutSeconds) throws Exception {
        Map<String, Object> context = new HashMap<>();

        int i = 0;
        while (i < steps.size()) {
            checkGlobalTimeout(instance.getStartedAt(), globalTimeoutSeconds);

            StepExecution step = steps.get(i);
            Map<String, Object> stepDef = findStepDefinition(stepDefinitions, step.getStepId());

            if (step.getStepType() == StepType.PARALLEL) {
                List<StepExecution> parallelSteps = collectParallelSteps(steps, i);
                List<Map<String, Object>> parallelStepDefs = new ArrayList<>();
                for (StepExecution ps : parallelSteps) {
                    parallelStepDefs.add(findStepDefinition(stepDefinitions, ps.getStepId()));
                }
                executeParallelSteps(parallelSteps, parallelStepDefs, instance, context, globalTimeoutSeconds);
                i += parallelSteps.size();
                
                if (i < steps.size() && steps.get(i).getStepType() == StepType.SYNC_POINT) {
                    i++;
                }
            } else {
                executeSequentialStep(step, stepDef, instance, context);
                i++;
            }
        }

        instance.setOutputData(context);
    }

    private void checkGlobalTimeout(LocalDateTime startedAt, int globalTimeoutSeconds) {
        if (expressionEvaluatorService.isTimeoutExceeded(startedAt, globalTimeoutSeconds)) {
            throw new RuntimeException("Saga global timeout exceeded after " +
                    expressionEvaluatorService.getElapsedSeconds(startedAt) + " seconds (limit: " + globalTimeoutSeconds + "s)");
        }
    }

    private Map<String, Object> findStepDefinition(List<Map<String, Object>> stepDefinitions, String stepId) {
        if (stepDefinitions == null) return null;
        for (Map<String, Object> def : stepDefinitions) {
            if (stepId.equals(def.get("id"))) {
                return def;
            }
        }
        return null;
    }

    private List<StepExecution> collectParallelSteps(List<StepExecution> steps, int startIndex) {
        List<StepExecution> parallelSteps = new ArrayList<>();
        for (int i = startIndex; i < steps.size(); i++) {
            if (steps.get(i).getStepType() == StepType.PARALLEL) {
                parallelSteps.add(steps.get(i));
            } else if (steps.get(i).getStepType() == StepType.SYNC_POINT) {
                break;
            } else {
                break;
            }
        }
        return parallelSteps;
    }

    private void executeSequentialStep(StepExecution step, Map<String, Object> stepDef,
                                       SagaInstance instance, Map<String, Object> context) throws Exception {
        if (step.getStatus() == StepStatus.COMPLETED) {
            return;
        }

        if (instance.getStatus() == SagaStatus.COMPENSATING) {
            step.setStatus(StepStatus.SKIPPED);
            stepExecutionRepository.save(step);
            return;
        }

        if (!evaluateStepCondition(stepDef, step, instance, context)) {
            log.info("Step {} skipped due to condition not met", step.getStepId());
            step.setStatus(StepStatus.SKIPPED);
            stepExecutionRepository.save(step);
            eventBusService.publishEvent(new SagaEvent(EventType.STEP_SKIPPED, instance.getId(), step.getStepId()));
            return;
        }

        eventBusService.publishEvent(new SagaEvent(EventType.STEP_STARTED, instance.getId(), step.getStepId()));

        executeStepWithRetry(step, stepDef, instance, context);

        if (step.getStatus() == StepStatus.COMPLETED) {
            eventBusService.publishEvent(new SagaEvent(EventType.STEP_COMPLETED, instance.getId(), step.getStepId()));
        } else if (step.getStatus() == StepStatus.FAILED || step.getStatus() == StepStatus.TIMED_OUT) {
            eventBusService.publishEvent(new SagaEvent(EventType.STEP_FAILED, instance.getId(), step.getStepId()));
            throw new RuntimeException("Step failed: " + step.getStepName() + " - " + step.getErrorMessage());
        }
    }

    private boolean evaluateStepCondition(Map<String, Object> stepDef, StepExecution step,
                                          SagaInstance instance, Map<String, Object> context) {
        if (stepDef == null) {
            return true;
        }
        String condition = (String) stepDef.get("condition");
        if (condition == null || condition.trim().isEmpty()) {
            return true;
        }
        try {
            return expressionEvaluatorService.evaluateCondition(condition, context, instance.getInputData());
        } catch (Exception e) {
            log.error("Error evaluating condition for step {}: {}", step.getStepId(), e.getMessage());
            return false;
        }
    }

    private void executeParallelSteps(List<StepExecution> parallelSteps, List<Map<String, Object>> parallelStepDefs,
                                      SagaInstance instance, Map<String, Object> context,
                                      int globalTimeoutSeconds) throws Exception {
        if (parallelSteps.isEmpty()) {
            return;
        }

        List<Callable<StepExecutionResult>> tasks = new ArrayList<>();
        for (int idx = 0; idx < parallelSteps.size(); idx++) {
            final StepExecution step = parallelSteps.get(idx);
            final Map<String, Object> stepDef = parallelStepDefs.get(idx);
            tasks.add(() -> {
                try {
                    Map<String, Object> stepContext = new HashMap<>(context);
                    if (evaluateStepCondition(stepDef, step, instance, stepContext)) {
                        executeStepWithRetry(step, stepDef, instance, stepContext);
                    } else {
                        log.info("Parallel step {} skipped due to condition not met", step.getStepId());
                        step.setStatus(StepStatus.SKIPPED);
                        stepExecutionRepository.save(step);
                    }
                    return new StepExecutionResult(step, stepContext, null);
                } catch (Exception e) {
                    return new StepExecutionResult(step, null, e);
                }
            });
        }

        long remainingTimeout = expressionEvaluatorService.getRemainingSeconds(instance.getStartedAt(), globalTimeoutSeconds);
        List<Future<StepExecutionResult>> futures = parallelExecutor.invokeAll(tasks, 
                Math.max(5, remainingTimeout), TimeUnit.SECONDS);
        
        boolean hasFailure = false;
        for (Future<StepExecutionResult> future : futures) {
            try {
                if (!future.isDone()) {
                    hasFailure = true;
                    continue;
                }
                StepExecutionResult result = future.get();
                if (result.error != null || result.step.getStatus() == StepStatus.FAILED 
                    || result.step.getStatus() == StepStatus.TIMED_OUT) {
                    hasFailure = true;
                } else if (result.step.getStatus() == StepStatus.COMPLETED) {
                    context.putAll(result.context);
                }
            } catch (Exception e) {
                hasFailure = true;
            }
        }

        if (hasFailure) {
            throw new RuntimeException("One or more parallel steps failed");
        }
    }

    private void executeStepWithRetry(StepExecution step, Map<String, Object> stepDef,
                                      SagaInstance instance, Map<String, Object> context) {
        step.setStatus(StepStatus.RUNNING);
        step.setStartedAt(LocalDateTime.now());
        stepExecutionRepository.save(step);

        int maxRetries = step.getMaxRetries();
        String retryStrategy = defaultRetryStrategy;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (instance.getStatus() == SagaStatus.COMPENSATING) {
                step.setStatus(StepStatus.SKIPPED);
                stepExecutionRepository.save(step);
                return;
            }

            if (attempt > 0) {
                step.setRetryCount(attempt);
                stepExecutionRepository.save(step);
                
                long delay = retryService.calculateDelay(attempt, retryStrategy, 
                        defaultBaseDelayMs, defaultMaxDelayMs);
                retryService.sleep(delay);
            }

            try {
                executeSingleStep(step, stepDef, instance, context);
                
                step.setStatus(StepStatus.COMPLETED);
                step.setCompletedAt(LocalDateTime.now());
                stepExecutionRepository.save(step);
                return;

            } catch (Exception e) {
                log.warn("Step {} attempt {} failed: {}", step.getStepId(), attempt + 1, e.getMessage());
                step.setErrorMessage(e.getMessage());
                stepExecutionRepository.save(step);
            }
        }

        step.setStatus(StepStatus.FAILED);
        step.setCompletedAt(LocalDateTime.now());
        stepExecutionRepository.save(step);
    }

    private void executeSingleStep(StepExecution step, Map<String, Object> stepDef,
                                   SagaInstance instance, Map<String, Object> context) throws Exception {
        if (step.getRequestUrl() == null || step.getRequestMethod() == null) {
            return;
        }

        Map<String, Object> forwardAction = stepDef != null ? (Map<String, Object>) stepDef.get("forwardAction") : null;
        String bodyTemplate = null;
        if (forwardAction != null) {
            bodyTemplate = (String) forwardAction.get("body");
        }

        String url = expressionEvaluatorService.resolveTemplate(step.getRequestUrl(), context, instance.getInputData());
        String method = step.getRequestMethod();
        String body = bodyTemplate != null ? expressionEvaluatorService.resolveTemplate(bodyTemplate, context, instance.getInputData()) : null;

        if (body == null && step.getRequestBody() != null) {
            body = expressionEvaluatorService.resolveTemplate(step.getRequestBody(), context, instance.getInputData());
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Saga-Idempotency-Key", 
                step.getSagaInstanceId() + "-" + step.getStepId() + "-" + step.getRetryCount());

        HttpCallerService.HttpResult result = httpCallerService.executeCall(
                url, method, body, headers, step.getTimeoutSeconds());

        step.setResponseBody(result.getResponseBody());
        step.setResponseStatus(result.getStatusCode());

        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage() != null ? 
                    result.getErrorMessage() : "HTTP call failed with status " + result.getStatusCode());
        }

        if (result.getResponseBody() != null && !result.getResponseBody().isEmpty()) {
            try {
                Map<String, Object> responseMap = objectMapper.readValue(
                        result.getResponseBody(), new TypeReference<Map<String, Object>>() {});
                context.put(step.getStepId() + "_response", responseMap);
            } catch (Exception e) {
                context.put(step.getStepId() + "_response", result.getResponseBody());
            }
        }
    }

    private void handleSagaFailure(SagaInstance instance, String errorMessage) {
        instance.setStatus(SagaStatus.COMPENSATING);
        instance.setErrorMessage(errorMessage);
        sagaInstanceRepository.save(instance);

        eventBusService.publishEvent(new SagaEvent(EventType.SAGA_COMPENSATING, instance.getId()));

        compensationService.startCompensation(instance.getId());
    }

    public SagaInstance getInstance(Long id) {
        return sagaInstanceRepository.findById(id).orElse(null);
    }

    public List<StepExecution> getStepExecutions(Long instanceId) {
        return stepExecutionRepository.findBySagaInstanceIdOrderByExecutionOrder(instanceId);
    }

    private static class StepExecutionResult {
        StepExecution step;
        Map<String, Object> context;
        Exception error;

        StepExecutionResult(StepExecution step, Map<String, Object> context, Exception error) {
            this.step = step;
            this.context = context;
            this.error = error;
        }
    }
}
