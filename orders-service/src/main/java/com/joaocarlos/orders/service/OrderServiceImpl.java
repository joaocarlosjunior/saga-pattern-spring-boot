package com.joaocarlos.orders.service;

import com.joaocarlos.core.dto.Order;
import com.joaocarlos.core.dto.events.OrderCreatedEvent;
import com.joaocarlos.core.types.OrderStatus;
import com.joaocarlos.orders.dao.jpa.entity.OrderEntity;
import com.joaocarlos.orders.dao.jpa.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import com.joaocarlos.core.dto.events.OrderApprovedEvent;

import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String ordersEventsTopicName;
    private final OrderHistoryService orderHistoryService;

    public OrderServiceImpl(OrderRepository orderRepository,
                            KafkaTemplate<String, Object> kafkaTemplate,
                            @Value("${orders.events.topic.name}") String ordersEventsTopicName,
                            OrderHistoryService orderHistoryService) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.ordersEventsTopicName = ordersEventsTopicName;
        this.orderHistoryService = orderHistoryService;
    }

    @Override
    public Order placeOrder(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.setCustomerId(order.getCustomerId());
        entity.setProductId(order.getProductId());
        entity.setProductQuantity(order.getProductQuantity());
        entity.setStatus(OrderStatus.CREATED);
        orderRepository.save(entity);

        orderHistoryService.add(entity.getId(), OrderStatus.CREATED);

        OrderCreatedEvent orderCreatedEvent = new OrderCreatedEvent(
                entity.getId(),
                entity.getCustomerId(),
                entity.getProductId(),
                entity.getProductQuantity()
        );

        kafkaTemplate.send(this.ordersEventsTopicName, orderCreatedEvent);

        return new Order(
                entity.getId(),
                entity.getCustomerId(),
                entity.getProductId(),
                entity.getProductQuantity(),
                entity.getStatus());
    }

    @Override
    public void approveOrder(UUID orderId) {
        OrderEntity orderEntity = orderRepository.findById(orderId).orElse(null);
        Assert.notNull(orderEntity, "Nenhum pedido encontrado com id: " + orderId);

        orderEntity.setStatus(OrderStatus.APPROVED);

        orderRepository.save(orderEntity);

        orderHistoryService.add(orderId, OrderStatus.APPROVED);

        OrderApprovedEvent orderApprovedEvent = new OrderApprovedEvent(orderId);

        kafkaTemplate.send(ordersEventsTopicName, orderApprovedEvent);
    }

    @Override
    public void rejectOrder(UUID orderId) {
        OrderEntity orderEntity = orderRepository.findById(orderId).orElse(null);
        Assert.notNull(orderEntity, "Nenhum pedido encontrado com id: " + orderId);

        orderEntity.setStatus(OrderStatus.REJECTED);

        orderRepository.save(orderEntity);

        orderHistoryService.add(orderId, OrderStatus.REJECTED);
    }

}
