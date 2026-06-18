package com.saga.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class ImportTemplateRequest {

    @NotNull(message = "模板ID不能为空")
    private Long templateId;

    @NotNull(message = "URL映射不能为空")
    private Map<String, String> urlMappings;
}
