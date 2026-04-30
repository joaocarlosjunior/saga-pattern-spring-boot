package com.joaocarlos.orders.service.handler;

import com.joaocarlos.core.dto.commands.ApproveOrderCommand;
import com.joaocarlos.orders.service.OrderService;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import com.joaocarlos.core.dto.commands.RejectOrderCommand;

@Component
@KafkaListener(topics = {"${order.commands.topic.name}"})
public class OrderCommandsHandler {
    private final OrderService orderService;

    public OrderCommandsHandler(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaHandler
    public void handleCommands(@Payload ApproveOrderCommand approveOrderCommand) {
        orderService.approveOrder(approveOrderCommand.getOrderId());
    }

    @KafkaHandler
    public void handleCommand(@Payload RejectOrderCommand command) {
        orderService.rejectOrder(command.getOrderId());
    }
}
