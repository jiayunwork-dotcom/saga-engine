package com.saga.engine.controller;

import com.saga.engine.dto.ApiResponse;
import com.saga.engine.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardStats() {
        Map<String, Object> stats = statisticsService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/top-failed-steps")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTopFailedSteps(
            @RequestParam(defaultValue = "10") int limit) {
        List<Map<String, Object>> topSteps = statisticsService.getTopFailedSteps(limit);
        return ResponseEntity.ok(ApiResponse.success(topSteps));
    }

    @GetMapping("/hourly-trend")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getHourlyTrend(
            @RequestParam(defaultValue = "24") int hours) {
        List<Map<String, Object>> trend = statisticsService.getHourlyTrend(hours);
        return ResponseEntity.ok(ApiResponse.success(trend));
    }

    @GetMapping("/saga-definitions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSagaDefinitionStats() {
        Map<String, Object> stats = statisticsService.getSagaDefinitionStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/average-duration")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAverageDuration() {
        Map<String, Object> result = statisticsService.getAverageDuration();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
