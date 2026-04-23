package com.joaocarlos.orders.service;

import com.joaocarlos.core.types.OrderStatus;
import com.joaocarlos.orders.dto.OrderHistory;

import java.util.List;
import java.util.UUID;

public interface OrderHistoryService {
    void add(UUID orderId, OrderStatus orderStatus);

    List<OrderHistory> findByOrderId(UUID orderId);
}
