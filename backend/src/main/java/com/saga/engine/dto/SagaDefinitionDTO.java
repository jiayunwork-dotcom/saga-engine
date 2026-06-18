package com.saga.engine.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class SagaDefinitionDTO {

    private Long id;
    private String name;
    private String description;
    private Integer version;
    private List<Map<String, Object>> definition;
    private Integer globalTimeoutSeconds;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
