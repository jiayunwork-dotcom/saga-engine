package com.saga.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class UpdateSagaDefinitionRequest {

    private String description;

    @NotNull(message = "Definition steps are required")
    private List<Map<String, Object>> definition;

    private Integer globalTimeoutSeconds;
}
