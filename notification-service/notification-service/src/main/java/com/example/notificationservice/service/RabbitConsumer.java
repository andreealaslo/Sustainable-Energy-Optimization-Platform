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
 * This class lives in the Notification Service.
 * It listens to RabbitMQ for alerts sent by the Recommendation Service
 * and broadcasts them to any connected frontend via WebSockets.
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
     * This method is triggered whenever a message is published to 'alert-exchange'
     * with the routing key 'alert.red'.
     * * It automatically creates the 'alert-queue' and binds it to the exchange if they don't exist.
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "alert-queue", durable = "true"),
            exchange = @Exchange(value = "alert-exchange", type = "topic"),
            key = "alert.red"
    ))
    public void handleRedAlert(Map<String, Object> alertData) {
        log.info("Notification Service received RED alert from RabbitMQ: {}", alertData);

        // --- THE WEBSOCKET PUSH ---
        // This sends the data to the "/topic/notifications" destination.
        // The React frontend will be 'subscribed' to this destination.
        try {
            messagingTemplate.convertAndSend("/topic/notifications", alertData);
            log.info("Successfully broadcast alert to WebSocket subscribers.");
        } catch (Exception e) {
            log.error("Failed to broadcast WebSocket message: {}", e.getMessage());
        }
    }
}