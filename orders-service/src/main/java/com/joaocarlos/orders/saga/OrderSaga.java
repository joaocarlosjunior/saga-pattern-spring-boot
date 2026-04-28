package com.joaocarlos.orders.saga;

import com.joaocarlos.core.types.OrderStatus;
import com.joaocarlos.orders.service.OrderHistoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import com.joaocarlos.core.dto.events.OrderCreatedEvent;
import org.springframework.stereotype.Component;
import com.joaocarlos.core.dto.commands.ReserveProductCommand;
import com.joaocarlos.core.dto.events.ProductReservedEvent;
import com.joaocarlos.core.dto.commands.ProcessPaymentCommand;

@Component
@KafkaListener(topics = {
        "${orders.events.topic.name}",
        "${products.events.topic.name}"
})
public class OrderSaga {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String productsCommandsTopicName;
    private final OrderHistoryService orderHistoryService;
    private final String paymentsCommandsTopicName;

    public OrderSaga(KafkaTemplate<String, Object> kafkaTemplate,
                     @Value("${products.commands.topic.name}") String productsCommandsTopicName,
                     OrderHistoryService orderHistoryService,
                     @Value("${payments.commands.topic.name}") String paymentsCommandsTopicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.productsCommandsTopicName = productsCommandsTopicName;
        this.orderHistoryService = orderHistoryService;
        this.paymentsCommandsTopicName = paymentsCommandsTopicName;
    }

    @KafkaHandler
    public void handleEvent(@Payload OrderCreatedEvent orderCreatedEvent) {
        ReserveProductCommand reserveProductCommand = new ReserveProductCommand();
        reserveProductCommand.setOrderId(orderCreatedEvent.getOrderId());
        reserveProductCommand.setProductQuantity(orderCreatedEvent.getProductQuantity());
        reserveProductCommand.setProductId(orderCreatedEvent.getProductId());

        kafkaTemplate.send(productsCommandsTopicName, reserveProductCommand);
        orderHistoryService.add(orderCreatedEvent.getOrderId(), OrderStatus.CREATED);
    }

    @KafkaHandler
    public void handleEvent(@Payload ProductReservedEvent productReservedEvent) {
        ProcessPaymentCommand processPaymentCommand = new ProcessPaymentCommand(
                productReservedEvent.getOrderId(),
                productReservedEvent.getProductId(),
                productReservedEvent.getProductPrice(),
                productReservedEvent.getProductQuantity()
        );

        kafkaTemplate.send(paymentsCommandsTopicName, processPaymentCommand);
    }
}
