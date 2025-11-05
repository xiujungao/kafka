package com.example.kafka.consumer;

import com.example.kafka.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class OrderConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OrderConsumer.class);

    @KafkaListener(
            topics = "orders",
            groupId = "app-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrder(
            @Payload Order order,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        logger.info("Received order message - Key: {}, Partition: {}, Offset: {}", key, partition, offset);
        logger.info("Order details: {}", order);
        
        // Process the order (e.g., save to database, send notification, etc.)
        try {
            processOrder(order);
            logger.info("Successfully processed order: {}", order.getOrderId());
        } catch (Exception e) {
            logger.error("Error processing order: {}", order.getOrderId(), e);
            // In a real application, you might want to send to a dead letter queue
        }
        
        // Acknowledge the message
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }

    private void processOrder(Order order) {
        // Add your business logic here
        // For example: save to database, update inventory, send notifications, etc.
        logger.info("Processing order: {} for customer: {}", order.getOrderId(), order.getCustomerId());
        
        // Simulate some processing
        if (order.getStatus() == null || order.getStatus().equals("PENDING")) {
            logger.info("Order {} is pending processing", order.getOrderId());
        }
    }
}

