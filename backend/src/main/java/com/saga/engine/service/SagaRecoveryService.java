package com.saga.engine.service;

import com.saga.engine.entity.SagaInstance;
import com.saga.engine.enums.SagaStatus;
import com.saga.engine.repository.SagaInstanceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaRecoveryService {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final SagaExecutionService sagaExecutionService;
    private final CompensationService compensationService;

    @PostConstruct
    public void init() {
        log.info("SagaRecoveryService initialized - starting recovery of running instances");
        recoverRunningInstances();
    }

    @Scheduled(fixedDelay = 60000)
    public void recoverRunningInstances() {
        try {
            List<SagaStatus> activeStatuses = Arrays.asList(
                    SagaStatus.RUNNING,
                    SagaStatus.CREATED,
                    SagaStatus.PAUSED
            );

            List<SagaInstance> activeInstances = sagaInstanceRepository.findByStatusIn(activeStatuses);
            
            log.info("Found {} active saga instances to recover", activeInstances.size());

            for (SagaInstance instance : activeInstances) {
                try {
                    if (instance.getStatus() == SagaStatus.CREATED || instance.getStatus() == SagaStatus.RUNNING) {
                        log.info("Recovering running instance: {}", instance.getId());
                        sagaExecutionService.executeSagaAsync(instance.getId());
                    }
                } catch (Exception e) {
                    log.error("Failed to recover instance: {}", instance.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error during instance recovery", e);
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void recoverCompensatingInstances() {
        try {
            List<SagaInstance> compensatingInstances = 
                    sagaInstanceRepository.findByStatus(SagaStatus.COMPENSATING);
            
            if (!compensatingInstances.isEmpty()) {
                log.info("Found {} compensating saga instances to recover", compensatingInstances.size());
            }

            for (SagaInstance instance : compensatingInstances) {
                try {
                    log.info("Recovering compensating instance: {}", instance.getId());
                    compensationService.startCompensation(instance.getId());
                } catch (Exception e) {
                    log.error("Failed to recover compensation for instance: {}", instance.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error during compensation recovery", e);
        }
    }
}
