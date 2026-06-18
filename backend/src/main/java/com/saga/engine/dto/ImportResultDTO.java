package com.saga.engine.dto;

import lombok.Data;

import java.util.List;

@Data
public class ImportResultDTO {

    private SagaDefinitionDTO definition;
    private List<String> unmetDependencies;
}
