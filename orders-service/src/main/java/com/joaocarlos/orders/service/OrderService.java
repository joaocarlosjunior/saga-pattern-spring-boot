package com.joaocarlos.orders.service;

import com.joaocarlos.core.dto.Order;

public interface OrderService {
    Order placeOrder(Order order);
}
