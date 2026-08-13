package com.joaocarlos.orders.service.handler;

import com.joaocarlos.core.dto.commands.ApproveOrderCommand;
import com.joaocarlos.orders.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import com.joaocarlos.core.dto.commands.RejectOrderCommand;

@Component
@KafkaListener(topics = {"${order.commands.topic.name}"})
public class OrderCommandsHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderCommandsHandler.class);

    private final OrderService orderService;

    public OrderCommandsHandler(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaHandler
    public void handleCommands(@Payload ApproveOrderCommand approveOrderCommand) {
        LOGGER.info("Received ApproveOrderCommand for orderId: {}", approveOrderCommand.getOrderId());
        orderService.approveOrder(approveOrderCommand.getOrderId());
    }

    @KafkaHandler
    public void handleCommand(@Payload RejectOrderCommand command) {
        LOGGER.info("Received RejectOrderCommand for orderId: {}", command.getOrderId());
        orderService.rejectOrder(command.getOrderId());
    }
}
