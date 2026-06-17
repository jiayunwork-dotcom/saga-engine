package com.saga.engine.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saga.engine.event.EventListener;
import com.saga.engine.event.SagaEvent;
import com.saga.engine.service.EventBusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaWebSocketHandler extends TextWebSocketHandler {

    private final EventBusService eventBusService;
    private final ObjectMapper objectMapper;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionSubscriptions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("WebSocket connection established: {}", sessionId);

        EventListener listener = event -> {
            try {
                if (session.isOpen()) {
                    String subscription = sessionSubscriptions.get(sessionId);
                    if (subscription == null || matchesSubscription(event, subscription)) {
                        String message = objectMapper.writeValueAsString(event);
                        session.sendMessage(new TextMessage(message));
                    }
                }
            } catch (IOException e) {
                log.error("Error sending WebSocket message", e);
            }
        };

        session.getAttributes().put("listener", listener);
        eventBusService.subscribeGlobal(listener);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String payload = message.getPayload();
        
        log.debug("Received WebSocket message from {}: {}", sessionId, payload);

        try {
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String action = (String) msg.get("action");
            
            if ("subscribe".equals(action)) {
                String subscription = (String) msg.get("subscription");
                sessionSubscriptions.put(sessionId, subscription);
                log.info("Session {} subscribed to: {}", sessionId, subscription);
            } else if ("unsubscribe".equals(action)) {
                sessionSubscriptions.remove(sessionId);
                log.info("Session {} unsubscribed", sessionId);
            }
        } catch (Exception e) {
            log.error("Error parsing WebSocket message", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        sessionSubscriptions.remove(sessionId);

        EventListener listener = (EventListener) session.getAttributes().get("listener");
        if (listener != null) {
            eventBusService.unsubscribe(null, listener);
        }

        log.info("WebSocket connection closed: {} - {}", sessionId, status);
    }

    private boolean matchesSubscription(SagaEvent event, String subscription) {
        if (subscription == null || subscription.isEmpty()) {
            return true;
        }
        
        if (subscription.startsWith("instance:")) {
            String instanceId = subscription.substring("instance:".length());
            return event.getSagaInstanceId() != null 
                    && String.valueOf(event.getSagaInstanceId()).equals(instanceId);
        }
        
        if (subscription.startsWith("definition:")) {
            String definitionId = subscription.substring("definition:".length());
            return event.getSagaDefinitionId() != null 
                    && String.valueOf(event.getSagaDefinitionId()).equals(definitionId);
        }
        
        return true;
    }
}
