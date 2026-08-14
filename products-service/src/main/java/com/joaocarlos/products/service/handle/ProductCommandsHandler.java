package com.joaocarlos.products.service.handle;

import com.joaocarlos.core.dto.Product;
import com.joaocarlos.core.dto.commands.CancelProductReservationCommand;
import com.joaocarlos.core.dto.commands.ReserveProductCommand;
import com.joaocarlos.core.dto.events.ProductReservationCancelledEvent;
import com.joaocarlos.core.dto.events.ProductReservationFailedEvent;
import com.joaocarlos.core.dto.events.ProductReservedEvent;
import com.joaocarlos.products.dao.jpa.repository.ProductRepository;
import com.joaocarlos.products.service.OutboxService;
import com.joaocarlos.products.service.ProductService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@KafkaListener(topics = {"${products.commands.topic.name}"})
public class ProductCommandsHandler {
    private final ProductService productService;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final OutboxService outboxService;
    private final String productEventsTopicName;
    private final ProductRepository productRepository;

    public ProductCommandsHandler(ProductService productService,
                                  OutboxService outboxService,
                                  @Value("${products.events.topic.name}") String productEventsTopicName,
                                  ProductRepository productRepository) {
        this.productService = productService;
        this.outboxService = outboxService;
        this.productEventsTopicName = productEventsTopicName;
        this.productRepository = productRepository;
    }

    @KafkaHandler
    @Transactional
    public void handleCommand(@Payload ReserveProductCommand reserveProductCommand) {
        logger.info("Received ReserveProductCommand for orderId: {}, productId: {}, quantity: {}",
                reserveProductCommand.getOrderId(), reserveProductCommand.getProductId(), reserveProductCommand.getProductQuantity());
        try {
            Product product = new Product(reserveProductCommand.getProductId(), reserveProductCommand.getProductQuantity());
            Product reservedProduct = productService.reserve(product, reserveProductCommand.getOrderId());

            ProductReservedEvent productReservedEvent = new ProductReservedEvent(
                    reserveProductCommand.getOrderId(),
                    reserveProductCommand.getProductId(),
                    reservedProduct.getQuantity(),
                    reservedProduct.getPrice()
            );

            outboxService.publishEvent("PRODUCT", reserveProductCommand.getOrderId().toString(), ProductReservedEvent.class.getName(), productEventsTopicName, productReservedEvent);
            logger.info("Saved ProductReservedEvent to Outbox for orderId: {}", reserveProductCommand.getOrderId());
        } catch (Exception e) {
            logger.error("Failed to reserve product for orderId: {}: {}", reserveProductCommand.getOrderId(), e.getLocalizedMessage(), e);
            ProductReservationFailedEvent productReservationFailedEvent = new ProductReservationFailedEvent(
                    reserveProductCommand.getOrderId(),
                    reserveProductCommand.getProductId(),
                    reserveProductCommand.getProductQuantity()
            );
            outboxService.publishEvent("PRODUCT", reserveProductCommand.getOrderId().toString(), ProductReservationFailedEvent.class.getName(), productEventsTopicName, productReservationFailedEvent);
            logger.info("Saved ProductReservationFailedEvent to Outbox for orderId: {}", reserveProductCommand.getOrderId());
        }
    }

    @KafkaHandler
    @Transactional
    public void handleCommand(@Payload CancelProductReservationCommand cancelProductReservationCommand) {
        logger.info("Received CancelProductReservationCommand for orderId: {}, productId: {}, quantity: {}",
                cancelProductReservationCommand.getOrderId(), cancelProductReservationCommand.getProductId(), cancelProductReservationCommand.getProductQuantity());
        Product productToCancel = new Product(
                cancelProductReservationCommand.getProductId(),
                cancelProductReservationCommand.getProductQuantity()
        );

        productService.cancelReservation(productToCancel, cancelProductReservationCommand.getOrderId());

        ProductReservationCancelledEvent event = new ProductReservationCancelledEvent(
                cancelProductReservationCommand.getProductId(),
                cancelProductReservationCommand.getOrderId()
        );

        outboxService.publishEvent("PRODUCT", cancelProductReservationCommand.getOrderId().toString(), ProductReservationCancelledEvent.class.getName(), productEventsTopicName, event);
        logger.info("Saved ProductReservationCancelledEvent to Outbox for orderId: {}", cancelProductReservationCommand.getOrderId());
    }
}

