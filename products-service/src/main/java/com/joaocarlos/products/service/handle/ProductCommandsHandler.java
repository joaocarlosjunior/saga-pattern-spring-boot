package com.joaocarlos.products.service.handle;

import com.joaocarlos.core.dto.Product;
import com.joaocarlos.core.dto.commands.ReserveProductCommand;
import com.joaocarlos.core.dto.events.ProductReservationFailedEvent;
import com.joaocarlos.core.dto.events.ProductReservedEvent;
import com.joaocarlos.products.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@KafkaListener(topics = {"${products.commands.topic.name}"})
public class ProductCommandsHandler {
    private final ProductService productService;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String productEventsTopicName;

    public ProductCommandsHandler(ProductService productService,
                                  KafkaTemplate<String, Object> kafkaTemplate,
                                  @Value("${products.events.topic.name}") String productEventsTopicName) {
        this.productService = productService;
        this.kafkaTemplate = kafkaTemplate;
        this.productEventsTopicName = productEventsTopicName;
    }

    @KafkaHandler
    public void handleCommand(@Payload ReserveProductCommand reserveProductCommand) {
        try {
            Product product = new Product(reserveProductCommand.getProductId(), reserveProductCommand.getProductQuantity());
            Product reservedProduct = productService.reserve(product, reserveProductCommand.getOrderId());

            ProductReservedEvent productReservedEvent = new ProductReservedEvent(
                    reserveProductCommand.getOrderId(),
                    reserveProductCommand.getProductId(),
                    reservedProduct.getQuantity(),
                    reservedProduct.getPrice()
            );

            kafkaTemplate.send(productEventsTopicName, productReservedEvent);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            ProductReservationFailedEvent productReservationFailedEvent = new ProductReservationFailedEvent(
                    reserveProductCommand.getOrderId(),
                    reserveProductCommand.getProductId(),
                    reserveProductCommand.getProductQuantity()
            );
            kafkaTemplate.send(productEventsTopicName, productReservationFailedEvent);
        }


    }
}
