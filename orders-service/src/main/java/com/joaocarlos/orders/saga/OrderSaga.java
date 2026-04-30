package com.joaocarlos.orders.saga;

import com.joaocarlos.core.dto.commands.ApproveOrderCommand;
import com.joaocarlos.core.dto.commands.CancelProductReservationCommand;
import com.joaocarlos.core.dto.commands.ProcessPaymentCommand;
import com.joaocarlos.core.dto.commands.ReserveProductCommand;
import com.joaocarlos.core.dto.events.OrderCreatedEvent;
import com.joaocarlos.core.dto.events.PaymentProcessedEvent;
import com.joaocarlos.core.dto.events.ProductReservedEvent;
import com.joaocarlos.core.types.OrderStatus;
import com.joaocarlos.orders.service.OrderHistoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import com.joaocarlos.core.dto.events.OrderApprovedEvent;
import com.joaocarlos.core.dto.events.PaymentFailedEvent;
import com.joaocarlos.core.dto.events.ProductReservationCancelledEvent;
import com.joaocarlos.core.dto.commands.RejectOrderCommand;

@Component
@KafkaListener(topics = {
        "${orders.events.topic.name}",
        "${products.events.topic.name}",
        "${payments.events.topic.name}"
})
public class OrderSaga {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String productsCommandsTopicName;
    private final OrderHistoryService orderHistoryService;
    private final String paymentsCommandsTopicName;
    private final String orderCommandsTopicName;

    public OrderSaga(KafkaTemplate<String, Object> kafkaTemplate,
                     @Value("${products.commands.topic.name}") String productsCommandsTopicName,
                     OrderHistoryService orderHistoryService,
                     @Value("${payments.commands.topic.name}") String paymentsCommandsTopicName,
                     @Value("${order.commands.topic.name}") String orderCommandsTopicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.productsCommandsTopicName = productsCommandsTopicName;
        this.orderHistoryService = orderHistoryService;
        this.paymentsCommandsTopicName = paymentsCommandsTopicName;
        this.orderCommandsTopicName = orderCommandsTopicName;
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

    @KafkaHandler
    public void handleEvent(@Payload PaymentProcessedEvent paymentProcessedEvent) {
        ApproveOrderCommand approveOrderCommand = new ApproveOrderCommand(paymentProcessedEvent.getOrderId());

        kafkaTemplate.send(orderCommandsTopicName, approveOrderCommand);
    }

    @KafkaHandler
    public void handleEvent(@Payload OrderApprovedEvent orderApprovedEvent) {
        orderHistoryService.add(orderApprovedEvent.getOrderId(), OrderStatus.APPROVED);
    }

    @KafkaHandler
    public void handleEvent(@Payload PaymentFailedEvent paymentFailedEvent) {
        CancelProductReservationCommand cancelProductReservationCommand = new CancelProductReservationCommand(
                paymentFailedEvent.getProductId(),
                paymentFailedEvent.getOrderId(),
                paymentFailedEvent.getProductQuantity()
        );

        kafkaTemplate.send(productsCommandsTopicName, cancelProductReservationCommand);
    }

    @KafkaHandler
    public void handleEvent(@Payload ProductReservationCancelledEvent event) {
        RejectOrderCommand command = new RejectOrderCommand(event.getOrderId());

        kafkaTemplate.send(orderCommandsTopicName, command);

        orderHistoryService.add(event.getOrderId(), OrderStatus.REJECTED);
    }
}
