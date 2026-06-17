package com.saga.engine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RetryService {

    @Value("${saga.default-retry-strategy:exponential}")
    private String defaultStrategy;

    @Value("${saga.default-retry-base-delay-ms:1000}")
    private long baseDelayMs;

    @Value("${saga.default-retry-max-delay-ms:30000}")
    private long maxDelayMs;

    public long calculateDelay(int retryCount, String strategy, long baseDelay, long maxDelay) {
        if (retryCount <= 0) {
            return 0;
        }

        long actualBaseDelay = baseDelay > 0 ? baseDelay : baseDelayMs;
        long actualMaxDelay = maxDelay > 0 ? maxDelay : maxDelayMs;
        String actualStrategy = strategy != null ? strategy : defaultStrategy;

        if ("fixed".equalsIgnoreCase(actualStrategy)) {
            return Math.min(actualBaseDelay, actualMaxDelay);
        } else {
            long delay = actualBaseDelay * (long) Math.pow(2, retryCount - 1);
            return Math.min(delay, actualMaxDelay);
        }
    }

    public long calculateExponentialDelay(int retryCount, long baseDelay, long maxDelay) {
        return calculateDelay(retryCount, "exponential", baseDelay, maxDelay);
    }

    public long calculateFixedDelay(int retryCount, long baseDelay, long maxDelay) {
        return calculateDelay(retryCount, "fixed", baseDelay, maxDelay);
    }

    public void sleep(long delayMs) {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Retry sleep interrupted", e);
            }
        }
    }
}
