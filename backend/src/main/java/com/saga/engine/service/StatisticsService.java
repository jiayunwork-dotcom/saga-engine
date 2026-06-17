package com.saga.engine.service;

import com.saga.engine.entity.StepExecution;
import com.saga.engine.enums.SagaStatus;
import com.saga.engine.repository.SagaInstanceRepository;
import com.saga.engine.repository.StepExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final StepExecutionRepository stepExecutionRepository;

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalInstances = sagaInstanceRepository.count();
        stats.put("totalInstances", totalInstances);

        long completedCount = sagaInstanceRepository.countByStatus(SagaStatus.COMPLETED);
        long failedCount = sagaInstanceRepository.countByStatus(SagaStatus.FAILED);
        long runningCount = sagaInstanceRepository.countByStatus(SagaStatus.RUNNING);
        long needInterventionCount = sagaInstanceRepository.countByStatus(SagaStatus.NEED_INTERVENTION);

        stats.put("completedCount", completedCount);
        stats.put("failedCount", failedCount);
        stats.put("runningCount", runningCount);
        stats.put("needInterventionCount", needInterventionCount);

        double successRate = totalInstances > 0 ? 
                (double) completedCount / totalInstances * 100 : 0;
        stats.put("successRate", Math.round(successRate * 100.0) / 100.0);

        return stats;
    }

    public List<Map<String, Object>> getTopFailedSteps(int limit) {
        List<Object[]> results = stepExecutionRepository.findTopFailedSteps();
        List<Map<String, Object>> topSteps = new ArrayList<>();

        int count = 0;
        for (Object[] result : results) {
            if (count >= limit) break;
            
            Map<String, Object> step = new HashMap<>();
            step.put("stepName", result[0]);
            step.put("failureCount", result[1]);
            topSteps.add(step);
            count++;
        }

        return topSteps;
    }

    public List<Map<String, Object>> getHourlyTrend(int hours) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(hours);

        List<Object[]> results = sagaInstanceRepository.countPerHour(startTime, endTime);
        List<Map<String, Object>> trend = new ArrayList<>();

        for (Object[] result : results) {
            Map<String, Object> hourData = new HashMap<>();
            hourData.put("hour", result[0].toString());
            hourData.put("count", result[1]);
            trend.add(hourData);
        }

        return trend;
    }

    public Map<String, Object> getSagaDefinitionStats() {
        Map<String, Object> stats = new HashMap<>();

        LocalDateTime startTime = LocalDateTime.now().minusDays(7);
        LocalDateTime endTime = LocalDateTime.now();

        List<Object[]> results = sagaInstanceRepository.countByDefinitionNameInTimeRange(startTime, endTime);
        
        List<Map<String, Object>> definitionStats = new ArrayList<>();
        long total = 0;
        
        for (Object[] result : results) {
            Map<String, Object> defStat = new HashMap<>();
            defStat.put("name", result[0]);
            defStat.put("count", result[1]);
            definitionStats.add(defStat);
            total += ((Number) result[1]).longValue();
        }

        stats.put("definitions", definitionStats);
        stats.put("totalLast7Days", total);

        return stats;
    }

    public Map<String, Object> getAverageDuration() {
        Map<String, Object> result = new HashMap<>();
        
        List<StepExecution> completedSteps = stepExecutionRepository.findAll().stream()
                .filter(s -> s.getStatus() == com.saga.engine.enums.StepStatus.COMPLETED 
                        && s.getStartedAt() != null && s.getCompletedAt() != null)
                .toList();

        if (!completedSteps.isEmpty()) {
            long totalDuration = 0;
            for (StepExecution step : completedSteps) {
                long duration = java.time.Duration.between(
                        step.getStartedAt(), step.getCompletedAt()).toMillis();
                totalDuration += duration;
            }
            double avgDuration = (double) totalDuration / completedSteps.size();
            result.put("averageStepDurationMs", Math.round(avgDuration));
        } else {
            result.put("averageStepDurationMs", 0);
        }

        return result;
    }
}
