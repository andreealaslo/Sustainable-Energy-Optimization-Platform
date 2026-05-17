package com.example.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Listens to RabbitMQ for both alerts and chart refreshes,
 * and broadcasts them dynamically to the frontend via WebSockets.
 */
@Service
@Slf4j
public class RabbitConsumer {

    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public RabbitConsumer(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Listens to 'alert-exchange' for BOTH 'alert.red' and 'chart.refresh' routing keys.
     * Everything is processed through the same pipeline, letting the UI decide how to show it.
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "alert-queue", durable = "true"),
            exchange = @Exchange(value = "alert-exchange", type = "topic", durable = "true"),
            key = {"alert.red", "chart.refresh"} // <--- Binds both routing keys to this queue
    ))
    public void handleIncomingEvent(Map<String, Object> eventData) {
        String eventType = eventData.getOrDefault("type", "UNKNOWN").toString();
        log.info("Notification Service received event [{}] from RabbitMQ: {}", eventType, eventData);

        // --- THE DYNAMIC WEBSOCKET PUSH ---
        // We push everything to "/topic/notifications". 
        // The frontend inspects the payload's "type" property to differentiate.
        try {
            messagingTemplate.convertAndSend("/topic/notifications", eventData);
            log.info("Successfully broadcast event [{}] to WebSocket subscribers.", eventType);
        } catch (Exception e) {
            log.error("Failed to broadcast WebSocket message: {}", e.getMessage());
        }
    }
}