package com.saga.engine.dto;

import com.saga.engine.enums.SagaStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class SagaInstanceDTO {

    private Long id;
    private Long sagaDefinitionId;
    private String sagaDefinitionName;
    private Integer sagaDefinitionVersion;
    private String correlationId;
    private SagaStatus status;
    private Map<String, Object> inputData;
    private Map<String, Object> outputData;
    private String errorMessage;
    private Integer globalTimeoutSeconds;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
