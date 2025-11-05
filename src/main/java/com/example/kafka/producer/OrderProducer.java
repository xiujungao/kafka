package com.example.kafka.producer;

import com.example.kafka.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class OrderProducer {

    private static final Logger logger = LoggerFactory.getLogger(OrderProducer.class);
    private static final String TOPIC = "orders";

    private final KafkaTemplate<String, Order> kafkaTemplate;

    public OrderProducer(KafkaTemplate<String, Order> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendOrder(Order order) {
        logger.info("Producing order: {}", order);
        
        CompletableFuture<SendResult<String, Order>> future = kafkaTemplate.send(TOPIC, order.getOrderId(), order);
        
        future.whenComplete((result, exception) -> {
            if (exception == null) {
                logger.info("Successfully sent order with key=[{}] to partition=[{}] with offset=[{}]",
                        order.getOrderId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                logger.error("Failed to send order with key=[{}], error: {}", order.getOrderId(), exception.getMessage());
            }
        });
    }
}

