package com.saga.engine.controller;

import com.saga.engine.dto.ApiResponse;
import com.saga.engine.dto.SagaInstanceDTO;
import com.saga.engine.dto.StepExecutionDTO;
import com.saga.engine.dto.TriggerSagaRequest;
import com.saga.engine.entity.SagaInstance;
import com.saga.engine.enums.SagaStatus;
import com.saga.engine.service.SagaExecutionService;
import com.saga.engine.service.SagaInstanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/saga-instances")
@RequiredArgsConstructor
public class SagaInstanceController {

    private final SagaExecutionService sagaExecutionService;
    private final SagaInstanceService sagaInstanceService;

    @PostMapping("/trigger")
    public ResponseEntity<ApiResponse<SagaInstanceDTO>> triggerSaga(
            @Valid @RequestBody TriggerSagaRequest request,
            Authentication authentication) {
        SagaInstance instance = sagaExecutionService.triggerSaga(request);
        SagaInstanceDTO dto = sagaInstanceService.getInstance(instance.getId());
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SagaInstanceDTO>>> getInstances(
            @RequestParam(required = false) SagaStatus status,
            @RequestParam(required = false) String sagaName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SagaInstanceDTO> instances = sagaInstanceService.getInstances(status, sagaName, pageable);
        return ResponseEntity.ok(ApiResponse.success(instances));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SagaInstanceDTO>> getInstance(@PathVariable Long id) {
        SagaInstanceDTO instance = sagaInstanceService.getInstance(id);
        return ResponseEntity.ok(ApiResponse.success(instance));
    }

    @GetMapping("/{id}/steps")
    public ResponseEntity<ApiResponse<List<StepExecutionDTO>>> getStepExecutions(@PathVariable Long id) {
        List<StepExecutionDTO> steps = sagaInstanceService.getStepExecutions(id);
        return ResponseEntity.ok(ApiResponse.success(steps));
    }

    @GetMapping("/correlation/{correlationId}")
    public ResponseEntity<ApiResponse<SagaInstanceDTO>> getInstanceByCorrelationId(
            @PathVariable String correlationId) {
        SagaInstanceDTO instance = sagaInstanceService.getInstanceByCorrelationId(correlationId);
        return ResponseEntity.ok(ApiResponse.success(instance));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<ApiResponse<SagaInstanceDTO>> pauseInstance(@PathVariable Long id) {
        SagaInstanceDTO instance = sagaInstanceService.pauseInstance(id);
        return ResponseEntity.ok(ApiResponse.success(instance));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<ApiResponse<SagaInstanceDTO>> resumeInstance(@PathVariable Long id) {
        SagaInstanceDTO instance = sagaInstanceService.resumeInstance(id);
        return ResponseEntity.ok(ApiResponse.success(instance));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<SagaInstanceDTO>> retryFailedStep(
            @PathVariable Long id,
            @RequestParam String stepId) {
        SagaInstanceDTO instance = sagaInstanceService.retryFailedStep(id, stepId);
        return ResponseEntity.ok(ApiResponse.success(instance));
    }

    @PostMapping("/{id}/compensate")
    public ResponseEntity<ApiResponse<SagaInstanceDTO>> manualCompensate(@PathVariable Long id) {
        SagaInstanceDTO instance = sagaInstanceService.manualCompensate(id);
        return ResponseEntity.ok(ApiResponse.success(instance));
    }
}
