package com.saga.engine.controller;

import com.saga.engine.dto.ApiResponse;
import com.saga.engine.entity.DeadLetterQueue;
import com.saga.engine.enums.DeadLetterStatus;
import com.saga.engine.service.CompensationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dead-letter")
@RequiredArgsConstructor
public class DeadLetterController {

    private final CompensationService compensationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeadLetterQueue>>> getDeadLetterQueue(
            @RequestParam(required = false) DeadLetterStatus status) {
        List<DeadLetterQueue> dlq = compensationService.getDeadLetterQueue(status);
        return ResponseEntity.ok(ApiResponse.success(dlq));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<DeadLetterQueue>> retryDeadLetter(
            @PathVariable Long id,
            Authentication authentication) {
        String operator = authentication.getName();
        DeadLetterQueue dlq = compensationService.retryDeadLetter(id, operator);
        return ResponseEntity.ok(ApiResponse.success(dlq));
    }

    @PostMapping("/{id}/handle")
    public ResponseEntity<ApiResponse<DeadLetterQueue>> markAsHandled(
            @PathVariable Long id,
            Authentication authentication) {
        String operator = authentication.getName();
        DeadLetterQueue dlq = compensationService.markAsHandled(id, operator);
        return ResponseEntity.ok(ApiResponse.success(dlq));
    }

    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> getPendingCount() {
        long count = compensationService.countDeadLettersByStatus(DeadLetterStatus.PENDING);
        return ResponseEntity.ok(ApiResponse.success(count));
    }
}
