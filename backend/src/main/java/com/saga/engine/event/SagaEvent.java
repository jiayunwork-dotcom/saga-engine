package com.saga.engine.event;

import com.saga.engine.enums.EventType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class SagaEvent implements Serializable {

    private String eventId;
    private EventType eventType;
    private Long sagaDefinitionId;
    private Long sagaInstanceId;
    private String stepId;
    private Map<String, Object> payload;
    private LocalDateTime occurredAt;

    public SagaEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = LocalDateTime.now();
    }

    public SagaEvent(EventType eventType, Long sagaInstanceId) {
        this();
        this.eventType = eventType;
        this.sagaInstanceId = sagaInstanceId;
    }

    public SagaEvent(EventType eventType, Long sagaInstanceId, String stepId) {
        this(eventType, sagaInstanceId);
        this.stepId = stepId;
    }

    public SagaEvent(EventType eventType, Long sagaInstanceId, String stepId, Map<String, Object> payload) {
        this(eventType, sagaInstanceId, stepId);
        this.payload = payload;
    }
}
