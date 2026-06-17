package com.saga.engine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final StringRedisTemplate redisTemplate;

    @Value("${saga.instance-lock-prefix:saga:lock:}")
    private String lockPrefix;

    private final ThreadLocal<String> lockValueHolder = new ThreadLocal<>();

    public boolean tryLock(String lockKey, long timeoutSeconds) {
        String key = lockPrefix + lockKey;
        String value = UUID.randomUUID().toString();
        
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, value, timeoutSeconds, TimeUnit.SECONDS);
        
        if (Boolean.TRUE.equals(success)) {
            lockValueHolder.set(value);
            log.debug("Acquired lock: {}", key);
            return true;
        }
        log.debug("Failed to acquire lock: {}", key);
        return false;
    }

    public void unlock(String lockKey) {
        String key = lockPrefix + lockKey;
        String value = lockValueHolder.get();
        
        if (value != null) {
            String currentValue = redisTemplate.opsForValue().get(key);
            if (value.equals(currentValue)) {
                redisTemplate.delete(key);
                log.debug("Released lock: {}", key);
            }
            lockValueHolder.remove();
        }
    }

    public boolean isLocked(String lockKey) {
        String key = lockPrefix + lockKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public boolean extendLock(String lockKey, long timeoutSeconds) {
        String key = lockPrefix + lockKey;
        String value = lockValueHolder.get();
        
        if (value == null) {
            return false;
        }
        
        String currentValue = redisTemplate.opsForValue().get(key);
        if (value.equals(currentValue)) {
            redisTemplate.expire(key, timeoutSeconds, TimeUnit.SECONDS);
            return true;
        }
        return false;
    }
}
