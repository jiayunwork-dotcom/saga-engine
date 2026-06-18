package com.saga.engine.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TemplateRatingDTO {

    private Long id;
    private Long templateId;
    private Long userId;
    private String username;
    private Integer score;
    private String comment;
    private LocalDateTime createdAt;
}
