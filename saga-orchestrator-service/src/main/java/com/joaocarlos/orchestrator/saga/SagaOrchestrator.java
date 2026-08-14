package com.joaocarlos.orchestrator.saga;

import com.joaocarlos.core.dto.commands.ApproveOrderCommand;
import com.joaocarlos.core.dto.commands.CancelProductReservationCommand;
import com.joaocarlos.core.dto.commands.ProcessPaymentCommand;
import com.joaocarlos.core.dto.commands.RejectOrderCommand;
import com.joaocarlos.core.dto.commands.ReserveProductCommand;
import com.joaocarlos.core.dto.events.OrderApprovedEvent;
import com.joaocarlos.core.dto.events.OrderCreatedEvent;
import com.joaocarlos.core.dto.events.PaymentFailedEvent;
import com.joaocarlos.core.dto.events.PaymentProcessedEvent;
import com.joaocarlos.core.dto.events.ProductReservationCancelledEvent;
import com.joaocarlos.core.dto.events.ProductReservationFailedEvent;
import com.joaocarlos.core.dto.events.ProductReservedEvent;
import com.joaocarlos.orchestrator.service.OutboxService;
import org.slf4j.Logger;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@KafkaListener(topics = {
        "${orders.events.topic.name}",
        "${products.events.topic.name}",
        "${payments.events.topic.name}"
})
public class SagaOrchestrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final OutboxService outboxService;
    private final String productsCommandsTopicName;
    private final String paymentsCommandsTopicName;
    private final String orderCommandsTopicName;

    public SagaOrchestrator(OutboxService outboxService,
                            @Value("${products.commands.topic.name}") String productsCommandsTopicName,
                            @Value("${payments.commands.topic.name}") String paymentsCommandsTopicName,
                            @Value("${order.commands.topic.name}") String orderCommandsTopicName) {
        this.outboxService = outboxService;
        this.productsCommandsTopicName = productsCommandsTopicName;
        this.paymentsCommandsTopicName = paymentsCommandsTopicName;
        this.orderCommandsTopicName = orderCommandsTopicName;
    }

    @KafkaHandler
    @Transactional
    public void handleEvent(@Payload OrderCreatedEvent orderCreatedEvent) {
        LOGGER.info("Received OrderCreatedEvent for orderId: {}", orderCreatedEvent.getOrderId());
        ReserveProductCommand reserveProductCommand = new ReserveProductCommand();
        reserveProductCommand.setOrderId(orderCreatedEvent.getOrderId());
        reserveProductCommand.setProductQuantity(orderCreatedEvent.getProductQuantity());
        reserveProductCommand.setProductId(orderCreatedEvent.getProductId());

        outboxService.publishEvent("SAGA", orderCreatedEvent.getOrderId().toString(), ReserveProductCommand.class.getName(), productsCommandsTopicName, reserveProductCommand);
        LOGGER.info("Saved ReserveProductCommand to Outbox for orderId: {}", orderCreatedEvent.getOrderId());
    }

    @KafkaHandler
    @Transactional
    public void handleEvent(@Payload ProductReservedEvent productReservedEvent) {
        LOGGER.info("Received ProductReservedEvent for orderId: {}", productReservedEvent.getOrderId());
        ProcessPaymentCommand processPaymentCommand = new ProcessPaymentCommand(
                productReservedEvent.getOrderId(),
                productReservedEvent.getProductId(),
                productReservedEvent.getProductPrice(),
                productReservedEvent.getProductQuantity()
        );

        outboxService.publishEvent("SAGA", productReservedEvent.getOrderId().toString(), ProcessPaymentCommand.class.getName(), paymentsCommandsTopicName, processPaymentCommand);
        LOGGER.info("Saved ProcessPaymentCommand to Outbox for orderId: {}", productReservedEvent.getOrderId());
    }

    @KafkaHandler
    @Transactional
    public void handleEvent(@Payload PaymentProcessedEvent paymentProcessedEvent) {
        LOGGER.info("Received PaymentProcessedEvent for orderId: {}", paymentProcessedEvent.getOrderId());
        ApproveOrderCommand approveOrderCommand = new ApproveOrderCommand(paymentProcessedEvent.getOrderId());

        outboxService.publishEvent("SAGA", paymentProcessedEvent.getOrderId().toString(), ApproveOrderCommand.class.getName(), orderCommandsTopicName, approveOrderCommand);
        LOGGER.info("Saved ApproveOrderCommand to Outbox for orderId: {}", paymentProcessedEvent.getOrderId());
    }

    @KafkaHandler
    public void handleEvent(@Payload OrderApprovedEvent orderApprovedEvent) {
        LOGGER.info("Received OrderApprovedEvent for orderId: {}. Saga completed successfully.", orderApprovedEvent.getOrderId());
    }

    @KafkaHandler
    @Transactional
    public void handleEvent(@Payload PaymentFailedEvent paymentFailedEvent) {
        LOGGER.info("Received PaymentFailedEvent for orderId: {}", paymentFailedEvent.getOrderId());
        CancelProductReservationCommand cancelProductReservationCommand = new CancelProductReservationCommand(
                paymentFailedEvent.getProductId(),
                paymentFailedEvent.getOrderId(),
                paymentFailedEvent.getProductQuantity()
        );

        outboxService.publishEvent("SAGA", paymentFailedEvent.getOrderId().toString(), CancelProductReservationCommand.class.getName(), productsCommandsTopicName, cancelProductReservationCommand);
        LOGGER.info("Saved CancelProductReservationCommand to Outbox for orderId: {}", paymentFailedEvent.getOrderId());
    }

    @KafkaHandler
    @Transactional
    public void handleEvent(@Payload ProductReservationCancelledEvent event) {
        LOGGER.info("Received ProductReservationCancelledEvent for orderId: {}", event.getOrderId());
        RejectOrderCommand command = new RejectOrderCommand(event.getOrderId());

        outboxService.publishEvent("SAGA", event.getOrderId().toString(), RejectOrderCommand.class.getName(), orderCommandsTopicName, command);
        LOGGER.info("Saved RejectOrderCommand to Outbox for orderId: {}", event.getOrderId());
    }

    @KafkaHandler
    @Transactional
    public void handleEvent(@Payload ProductReservationFailedEvent event) {
        LOGGER.info("Received ProductReservationFailedEvent for orderId: {}", event.getOrderId());
        RejectOrderCommand command = new RejectOrderCommand(event.getOrderId());

        outboxService.publishEvent("SAGA", event.getOrderId().toString(), RejectOrderCommand.class.getName(), orderCommandsTopicName, command);
        LOGGER.info("Saved RejectOrderCommand to Outbox for orderId: {}", event.getOrderId());
    }
}

