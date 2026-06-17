package com.saga.engine.service;

import com.saga.engine.entity.EventLog;
import com.saga.engine.enums.EventType;
import com.saga.engine.event.SagaEvent;
import com.saga.engine.event.EventListener;
import com.saga.engine.repository.EventLogRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventBusService {

    private final EventLogRepository eventLogRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final Map<String, List<EventListener>> subscribers = new ConcurrentHashMap<>();
    private final List<EventListener> globalListeners = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        log.info("EventBusService initialized");
    }

    @Transactional
    public void publishEvent(SagaEvent event) {
        try {
            persistEvent(event);
            sendToRedis(event);
            notifySubscribers(event);
            log.debug("Published event: {} for instance: {}", event.getEventType(), event.getSagaInstanceId());
        } catch (Exception e) {
            log.error("Failed to publish event: {}", event.getEventId(), e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }

    @Async
    @Transactional
    public void publishEventAsync(SagaEvent event) {
        publishEvent(event);
    }

    private void persistEvent(SagaEvent event) {
        EventLog eventLog = new EventLog();
        eventLog.setEventId(event.getEventId());
        eventLog.setEventType(event.getEventType());
        eventLog.setSagaDefinitionId(event.getSagaDefinitionId());
        eventLog.setSagaInstanceId(event.getSagaInstanceId());
        eventLog.setStepId(event.getStepId());
        eventLog.setPayload(event.getPayload());
        eventLog.setOccurredAt(event.getOccurredAt());
        eventLog.setProcessed(true);
        eventLog.setProcessedAt(LocalDateTime.now());
        eventLogRepository.save(eventLog);
    }

    private void sendToRedis(SagaEvent event) {
        try {
            String channel = "saga:events:" + event.getEventType().name().toLowerCase();
            String message = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(channel, message);
            
            String instanceChannel = "saga:instance:" + event.getSagaInstanceId();
            redisTemplate.convertAndSend(instanceChannel, message);
            
            if (event.getSagaDefinitionId() != null) {
                String definitionChannel = "saga:definition:" + event.getSagaDefinitionId();
                redisTemplate.convertAndSend(definitionChannel, message);
            }
        } catch (Exception e) {
            log.error("Failed to send event to Redis", e);
        }
    }

    private void notifySubscribers(SagaEvent event) {
        for (EventListener listener : globalListeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                log.error("EventListener error for global listener", e);
            }
        }

        List<EventListener> typeListeners = subscribers.get(event.getEventType().name());
        if (typeListeners != null) {
            for (EventListener listener : typeListeners) {
                try {
                    listener.onEvent(event);
                } catch (Exception e) {
                    log.error("EventListener error for event type: {}", event.getEventType(), e);
                }
            }
        }
    }

    public void subscribe(EventType eventType, EventListener listener) {
        subscribers.computeIfAbsent(eventType.name(), k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void subscribeGlobal(EventListener listener) {
        globalListeners.add(listener);
    }

    public void unsubscribe(EventType eventType, EventListener listener) {
        List<EventListener> listeners = subscribers.get(eventType.name());
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    public List<EventLog> getEventLogsByInstance(Long instanceId) {
        return eventLogRepository.findBySagaInstanceIdOrderByOccurredAt(instanceId);
    }

    public Optional<EventLog> getEventLogByEventId(String eventId) {
        return eventLogRepository.findByEventId(eventId);
    }

    public boolean isEventProcessed(String eventId) {
        return eventLogRepository.findByEventId(eventId)
                .map(EventLog::getProcessed)
                .orElse(false);
    }
}
