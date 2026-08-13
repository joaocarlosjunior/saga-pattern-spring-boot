package com.joaocarlos.orders.web.controller;

import com.joaocarlos.core.dto.Order;
import com.joaocarlos.orders.dto.CreateOrderRequest;
import com.joaocarlos.orders.dto.CreateOrderResponse;
import com.joaocarlos.orders.dto.OrderHistoryResponse;
import com.joaocarlos.orders.service.OrderHistoryService;
import com.joaocarlos.orders.service.OrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrdersController {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrdersController.class);

    private final OrderService orderService;
    private final OrderHistoryService orderHistoryService;

    public OrdersController(OrderService orderService, OrderHistoryService orderHistoryService) {
        this.orderService = orderService;
        this.orderHistoryService = orderHistoryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CreateOrderResponse placeOrder(@RequestBody @Valid CreateOrderRequest request) {
        LOGGER.info("REST request to place order for customerId: {}, productId: {}, quantity: {}",
                request.getCustomerId(), request.getProductId(), request.getProductQuantity());
        var order = new Order();
        BeanUtils.copyProperties(request, order);
        Order createdOrder = orderService.placeOrder(order);

        var response = new CreateOrderResponse();
        BeanUtils.copyProperties(createdOrder, response);
        LOGGER.info("Order placed successfully with orderId: {}", response.getOrderId());
        return response;
    }

    @GetMapping("/{orderId}/history")
    @ResponseStatus(HttpStatus.OK)
    public List<OrderHistoryResponse> getOrderHistory(@PathVariable UUID orderId) {
        LOGGER.info("REST request to fetch order history for orderId: {}", orderId);
        List<OrderHistoryResponse> historyList = orderHistoryService.findByOrderId(orderId).stream().map(orderHistory -> {
            OrderHistoryResponse orderHistoryResponse = new OrderHistoryResponse();
            BeanUtils.copyProperties(orderHistory, orderHistoryResponse);
            return orderHistoryResponse;
        }).toList();
        LOGGER.info("Fetched {} history record(s) for orderId: {}", historyList.size(), orderId);
        return historyList;
    }
}
