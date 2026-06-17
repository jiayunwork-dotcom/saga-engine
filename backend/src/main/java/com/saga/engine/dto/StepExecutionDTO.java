package com.saga.engine.dto;

import com.saga.engine.enums.StepStatus;
import com.saga.engine.enums.StepType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StepExecutionDTO {

    private Long id;
    private Long sagaInstanceId;
    private String stepId;
    private String stepName;
    private StepType stepType;
    private StepStatus status;
    private Integer executionOrder;
    private String requestUrl;
    private String requestMethod;
    private String requestBody;
    private String responseBody;
    private Integer responseStatus;
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetries;
    private Integer timeoutSeconds;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
