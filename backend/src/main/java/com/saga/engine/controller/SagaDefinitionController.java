package com.saga.engine.controller;

import com.saga.engine.dto.ApiResponse;
import com.saga.engine.dto.CreateSagaDefinitionRequest;
import com.saga.engine.dto.SagaDefinitionDTO;
import com.saga.engine.dto.UpdateSagaDefinitionRequest;
import com.saga.engine.service.SagaDefinitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/saga-definitions")
@RequiredArgsConstructor
public class SagaDefinitionController {

    private final SagaDefinitionService sagaDefinitionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SagaDefinitionDTO>>> getAllDefinitions(
            @RequestParam(required = false) String keyword) {
        List<SagaDefinitionDTO> definitions = sagaDefinitionService.searchDefinitions(keyword);
        return ResponseEntity.ok(ApiResponse.success(definitions));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SagaDefinitionDTO>> getDefinition(@PathVariable Long id) {
        SagaDefinitionDTO definition = sagaDefinitionService.getDefinition(id);
        return ResponseEntity.ok(ApiResponse.success(definition));
    }

    @GetMapping("/name/{name}/latest")
    public ResponseEntity<ApiResponse<SagaDefinitionDTO>> getLatestDefinition(@PathVariable String name) {
        SagaDefinitionDTO definition = sagaDefinitionService.getLatestDefinition(name);
        return ResponseEntity.ok(ApiResponse.success(definition));
    }

    @GetMapping("/name/{name}/versions")
    public ResponseEntity<ApiResponse<List<SagaDefinitionDTO>>> getDefinitionVersions(@PathVariable String name) {
        List<SagaDefinitionDTO> versions = sagaDefinitionService.getDefinitionVersions(name);
        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SagaDefinitionDTO>> createDefinition(
            @Valid @RequestBody CreateSagaDefinitionRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        SagaDefinitionDTO definition = sagaDefinitionService.createDefinition(request, username);
        return ResponseEntity.ok(ApiResponse.success(definition));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SagaDefinitionDTO>> updateDefinition(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSagaDefinitionRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        SagaDefinitionDTO definition = sagaDefinitionService.updateDefinition(id, request, username);
        return ResponseEntity.ok(ApiResponse.success(definition));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDefinition(@PathVariable Long id) {
        sagaDefinitionService.deleteDefinition(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
