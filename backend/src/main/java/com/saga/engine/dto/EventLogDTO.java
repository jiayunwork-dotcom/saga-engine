package com.saga.engine.dto;

import com.saga.engine.enums.EventType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class EventLogDTO {

    private Long id;
    private String eventId;
    private EventType eventType;
    private Long sagaDefinitionId;
    private Long sagaInstanceId;
    private String stepId;
    private Map<String, Object> payload;
    private LocalDateTime occurredAt;
    private Boolean processed;
    private LocalDateTime processedAt;
}
