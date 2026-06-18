package com.saga.engine.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class TemplateDTO {

    private Long id;
    private String name;
    private String description;
    private List<String> categoryTags;
    private String version;
    private String status;
    private String publisher;
    private String reviewer;
    private List<Map<String, Object>> stepDefinition;
    private Integer downloadCount;
    private String sceneDescription;
    private List<String> dependencies;
    private List<TemplateDTO> dependencyTemplates;
    private String reviewComment;
    private Boolean favorited;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private Double averageScore;
    private Long ratingCount;
}
