package com.saga.engine.entity;

import com.saga.engine.enums.DeadLetterStatus;
import com.saga.engine.enums.FailureType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "dead_letter_queue")
public class DeadLetterQueue {

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
    @Column(name = "failure_type", nullable = false, length = 50)
    private FailureType failureType;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DeadLetterStatus status;

    @Column(name = "handled_by", length = 100)
    private String handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

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
