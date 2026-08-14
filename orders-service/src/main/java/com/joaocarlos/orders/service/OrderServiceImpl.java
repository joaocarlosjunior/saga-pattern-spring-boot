package com.joaocarlos.orders.service;

import com.joaocarlos.core.dto.Order;
import com.joaocarlos.core.dto.events.OrderApprovedEvent;
import com.joaocarlos.core.dto.events.OrderCreatedEvent;
import com.joaocarlos.core.types.OrderStatus;
import com.joaocarlos.orders.dao.jpa.entity.OrderEntity;
import com.joaocarlos.orders.dao.jpa.repository.OrderRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;
    private final String ordersEventsTopicName;
    private final OrderHistoryService orderHistoryService;

    public OrderServiceImpl(OrderRepository orderRepository,
                            OutboxService outboxService,
                            @Value("${orders.events.topic.name}") String ordersEventsTopicName,
                            OrderHistoryService orderHistoryService) {
        this.orderRepository = orderRepository;
        this.outboxService = outboxService;
        this.ordersEventsTopicName = ordersEventsTopicName;
        this.orderHistoryService = orderHistoryService;
    }

    @Override
    @Transactional
    public Order placeOrder(Order order) {
        LOGGER.info("Placing new order for customerId: {}, productId: {}, quantity: {}",
                order.getCustomerId(), order.getProductId(), order.getProductQuantity());
        OrderEntity entity = new OrderEntity();
        entity.setCustomerId(order.getCustomerId());
        entity.setProductId(order.getProductId());
        entity.setProductQuantity(order.getProductQuantity());
        entity.setStatus(OrderStatus.CREATED);
        orderRepository.save(entity);
        LOGGER.info("Saved OrderEntity with id: {} and status: {}", entity.getId(), entity.getStatus());

        orderHistoryService.add(entity.getId(), OrderStatus.CREATED);

        OrderCreatedEvent orderCreatedEvent = new OrderCreatedEvent(
                entity.getId(),
                entity.getCustomerId(),
                entity.getProductId(),
                entity.getProductQuantity()
        );

        outboxService.publishEvent("ORDER", entity.getId().toString(), OrderCreatedEvent.class.getName(), this.ordersEventsTopicName, orderCreatedEvent);
        LOGGER.info("Saved OrderCreatedEvent to Outbox for orderId: {}", entity.getId());

        return new Order(
                entity.getId(),
                entity.getCustomerId(),
                entity.getProductId(),
                entity.getProductQuantity(),
                entity.getStatus());
    }

    @Override
    @Transactional
    public void approveOrder(UUID orderId) {
        LOGGER.info("Approving order with orderId: {}", orderId);
        OrderEntity orderEntity = orderRepository.findById(orderId).orElse(null);
        Assert.notNull(orderEntity, "Nenhum pedido encontrado com id: " + orderId);

        orderEntity.setStatus(OrderStatus.APPROVED);

        orderRepository.save(orderEntity);

        orderHistoryService.add(orderId, OrderStatus.APPROVED);

        OrderApprovedEvent orderApprovedEvent = new OrderApprovedEvent(orderId);

        outboxService.publishEvent("ORDER", orderId.toString(), OrderApprovedEvent.class.getName(), ordersEventsTopicName, orderApprovedEvent);
        LOGGER.info("Order with orderId: {} successfully APPROVED and OrderApprovedEvent saved to Outbox.", orderId);
    }

    @Override
    @Transactional
    public void rejectOrder(UUID orderId) {
        LOGGER.info("Rejecting order with orderId: {}", orderId);
        OrderEntity orderEntity = orderRepository.findById(orderId).orElse(null);
        Assert.notNull(orderEntity, "Nenhum pedido encontrado com id: " + orderId);

        orderEntity.setStatus(OrderStatus.REJECTED);

        orderRepository.save(orderEntity);

        orderHistoryService.add(orderId, OrderStatus.REJECTED);
        LOGGER.info("Order with orderId: {} REJECTED.", orderId);
    }

}
