package com.saga.engine.entity;

import com.saga.engine.enums.StepStatus;
import com.saga.engine.enums.StepType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "step_execution")
public class StepExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saga_instance_id", nullable = false)
    private Long sagaInstanceId;

    @Column(name = "step_id", nullable = false, length = 100)
    private String stepId;

    @Column(name = "step_name", nullable = false)
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 50)
    private StepType stepType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StepStatus status;

    @Column(name = "execution_order", nullable = false)
    private Integer executionOrder;

    @Column(name = "request_url", length = 1024)
    private String requestUrl;

    @Column(name = "request_method", length = 20)
    private String requestMethod;

    @Column(name = "request_body", columnDefinition = "text")
    private String requestBody;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 3;

    @Column(name = "timeout_seconds", nullable = false)
    private Integer timeoutSeconds = 30;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
