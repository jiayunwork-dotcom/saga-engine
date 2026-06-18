package com.saga.engine.entity;

import com.saga.engine.enums.TemplateStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Entity
@Table(name = "saga_template", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name", "version"})
})
public class SagaTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> categoryTags;

    @Column(nullable = false, length = 50)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TemplateStatus status = TemplateStatus.PENDING_REVIEW;

    @Column(nullable = false, length = 100)
    private String publisher;

    @Column(length = 100)
    private String reviewer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "step_definition", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> stepDefinition;

    @Column(name = "download_count", nullable = false)
    private Integer downloadCount = 0;

    @Column(name = "scene_description", columnDefinition = "text")
    private String sceneDescription;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
