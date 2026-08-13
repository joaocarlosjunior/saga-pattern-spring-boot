package com.joaocarlos.orders.service;

import com.joaocarlos.core.types.OrderStatus;
import com.joaocarlos.orders.dao.jpa.entity.OrderHistoryEntity;
import com.joaocarlos.orders.dao.jpa.repository.OrderHistoryRepository;
import com.joaocarlos.orders.dto.OrderHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class OrderHistoryServiceImpl implements OrderHistoryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderHistoryServiceImpl.class);

    private final OrderHistoryRepository orderHistoryRepository;

    public OrderHistoryServiceImpl(OrderHistoryRepository orderHistoryRepository) {
        this.orderHistoryRepository = orderHistoryRepository;
    }

    @Override
    public void add(UUID orderId, OrderStatus orderStatus) {
        LOGGER.info("Adding history record for orderId: {} with status: {}", orderId, orderStatus);
        OrderHistoryEntity entity = new OrderHistoryEntity();
        entity.setOrderId(orderId);
        entity.setStatus(orderStatus);
        entity.setCreatedAt(new Timestamp(new Date().getTime()));
        orderHistoryRepository.save(entity);
        LOGGER.info("Successfully added history record for orderId: {}", orderId);
    }

    @Override
    public List<OrderHistory> findByOrderId(UUID orderId) {
        LOGGER.info("Searching order history for orderId: {}", orderId);
        var entities = orderHistoryRepository.findByOrderId(orderId);
        List<OrderHistory> result = entities.stream().map(entity -> {
            OrderHistory orderHistory = new OrderHistory();
            BeanUtils.copyProperties(entity, orderHistory);
            return orderHistory;
        }).toList();
        LOGGER.info("Found {} history records for orderId: {}", result.size(), orderId);
        return result;
    }
}
