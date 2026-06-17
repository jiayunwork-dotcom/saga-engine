package com.saga.engine.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class TriggerSagaRequest {

    @NotBlank(message = "Saga definition name is required")
    private String sagaName;

    private Integer version;

    @NotBlank(message = "Correlation ID is required")
    private String correlationId;

    private Map<String, Object> inputData;
}
