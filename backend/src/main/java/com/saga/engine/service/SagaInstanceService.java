package com.saga.engine.service;

import com.saga.engine.dto.SagaInstanceDTO;
import com.saga.engine.dto.StepExecutionDTO;
import com.saga.engine.entity.SagaInstance;
import com.saga.engine.entity.StepExecution;
import com.saga.engine.enums.SagaStatus;
import com.saga.engine.enums.StepStatus;
import com.saga.engine.repository.SagaInstanceRepository;
import com.saga.engine.repository.StepExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaInstanceService {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final StepExecutionRepository stepExecutionRepository;

    public Page<SagaInstanceDTO> getInstances(SagaStatus status, String sagaName, Pageable pageable) {
        Page<SagaInstance> instances;
        
        if (status != null && sagaName != null && !sagaName.isEmpty()) {
            instances = sagaInstanceRepository.findByStatusAndSagaDefinitionNameContaining(status, sagaName, pageable);
        } else if (status != null) {
            instances = sagaInstanceRepository.findByStatus(status, pageable);
        } else if (sagaName != null && !sagaName.isEmpty()) {
            instances = sagaInstanceRepository.findBySagaDefinitionNameContaining(sagaName, pageable);
        } else {
            instances = sagaInstanceRepository.findAll(pageable);
        }
        
        return instances.map(this::convertToDTO);
    }

    public SagaInstanceDTO getInstance(Long id) {
        SagaInstance instance = sagaInstanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Saga instance not found: " + id));
        return convertToDTO(instance);
    }

    public List<StepExecutionDTO> getStepExecutions(Long instanceId) {
        List<StepExecution> steps = stepExecutionRepository.findBySagaInstanceIdOrderByExecutionOrder(instanceId);
        return steps.stream()
                .map(this::convertStepToDTO)
                .collect(Collectors.toList());
    }

    public SagaInstanceDTO getInstanceByCorrelationId(String correlationId) {
        SagaInstance instance = sagaInstanceRepository.findByCorrelationId(correlationId)
                .orElseThrow(() -> new RuntimeException("Saga instance not found for correlationId: " + correlationId));
        return convertToDTO(instance);
    }

    @Transactional
    public SagaInstanceDTO pauseInstance(Long id) {
        SagaInstance instance = sagaInstanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Saga instance not found: " + id));
        
        if (instance.getStatus() == SagaStatus.RUNNING) {
            instance.setStatus(SagaStatus.PAUSED);
            sagaInstanceRepository.save(instance);
            log.info("Paused saga instance: {}", id);
        }
        
        return convertToDTO(instance);
    }

    @Transactional
    public SagaInstanceDTO resumeInstance(Long id) {
        SagaInstance instance = sagaInstanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Saga instance not found: " + id));
        
        if (instance.getStatus() == SagaStatus.PAUSED) {
            instance.setStatus(SagaStatus.RUNNING);
            sagaInstanceRepository.save(instance);
            log.info("Resumed saga instance: {}", id);
        }
        
        return convertToDTO(instance);
    }

    @Transactional
    public SagaInstanceDTO retryFailedStep(Long instanceId, String stepId) {
        SagaInstance instance = sagaInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("Saga instance not found: " + instanceId));
        
        StepExecution step = stepExecutionRepository.findBySagaInstanceIdAndStepId(instanceId, stepId)
                .orElseThrow(() -> new RuntimeException("Step not found: " + stepId));
        
        if (step.getStatus() == StepStatus.FAILED || step.getStatus() == StepStatus.TIMED_OUT) {
            step.setStatus(StepStatus.PENDING);
            step.setRetryCount(0);
            step.setErrorMessage(null);
            stepExecutionRepository.save(step);
            log.info("Reset step {} for retry on instance {}", stepId, instanceId);
        }
        
        return convertToDTO(instance);
    }

    @Transactional
    public SagaInstanceDTO manualCompensate(Long id) {
        SagaInstance instance = sagaInstanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Saga instance not found: " + id));
        
        if (instance.getStatus() == SagaStatus.FAILED || instance.getStatus() == SagaStatus.NEED_INTERVENTION) {
            instance.setStatus(SagaStatus.COMPENSATING);
            sagaInstanceRepository.save(instance);
            log.info("Manual compensation started for instance: {}", id);
        }
        
        return convertToDTO(instance);
    }

    private SagaInstanceDTO convertToDTO(SagaInstance entity) {
        SagaInstanceDTO dto = new SagaInstanceDTO();
        dto.setId(entity.getId());
        dto.setSagaDefinitionId(entity.getSagaDefinitionId());
        dto.setSagaDefinitionName(entity.getSagaDefinitionName());
        dto.setSagaDefinitionVersion(entity.getSagaDefinitionVersion());
        dto.setCorrelationId(entity.getCorrelationId());
        dto.setStatus(entity.getStatus());
        dto.setInputData(entity.getInputData());
        dto.setOutputData(entity.getOutputData());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setStartedAt(entity.getStartedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private StepExecutionDTO convertStepToDTO(StepExecution entity) {
        StepExecutionDTO dto = new StepExecutionDTO();
        dto.setId(entity.getId());
        dto.setSagaInstanceId(entity.getSagaInstanceId());
        dto.setStepId(entity.getStepId());
        dto.setStepName(entity.getStepName());
        dto.setStepType(entity.getStepType());
        dto.setStatus(entity.getStatus());
        dto.setExecutionOrder(entity.getExecutionOrder());
        dto.setRequestUrl(entity.getRequestUrl());
        dto.setRequestMethod(entity.getRequestMethod());
        dto.setRequestBody(entity.getRequestBody());
        dto.setResponseBody(entity.getResponseBody());
        dto.setResponseStatus(entity.getResponseStatus());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setRetryCount(entity.getRetryCount());
        dto.setMaxRetries(entity.getMaxRetries());
        dto.setTimeoutSeconds(entity.getTimeoutSeconds());
        dto.setStartedAt(entity.getStartedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
