package com.joaocarlos.payments.service.handler;

import com.joaocarlos.core.dto.Payment;
import com.joaocarlos.core.dto.commands.ProcessPaymentCommand;
import com.joaocarlos.core.dto.events.PaymentFailedEvent;
import com.joaocarlos.core.dto.events.PaymentProcessedEvent;
import com.joaocarlos.core.exceptions.CreditCardProcessorUnavailableException;
import com.joaocarlos.payments.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@KafkaListener(topics = "${payments.commands.topic.name}")
public class PaymentsCommandsHandler {
    private final PaymentService paymentService;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final String paymentsEventsTopicName;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentsCommandsHandler(PaymentService paymentService,
                                   @Value("${payments.events.topic.name}") String paymentsEventsTopicName,
                                   KafkaTemplate<String, Object> kafkaTemplate) {
        this.paymentService = paymentService;
        this.paymentsEventsTopicName = paymentsEventsTopicName;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaHandler
    public void handleCommand(@Payload ProcessPaymentCommand processPaymentCommand) {
        logger.info("Received ProcessPaymentCommand for orderId: {}, productId: {}, price: {}, quantity: {}",
                processPaymentCommand.getOrderId(), processPaymentCommand.getProductId(),
                processPaymentCommand.getProductPrice(), processPaymentCommand.getProductQuantity());
        try {
            Payment payment = new Payment(processPaymentCommand.getOrderId(), processPaymentCommand.getProductId(), processPaymentCommand.getProductPrice(), processPaymentCommand.getProductQuantity());

            Payment processedPayment = paymentService.process(payment);

            PaymentProcessedEvent paymentProcessedEvent = new PaymentProcessedEvent(
                    processedPayment.getOrderId(),
                    processedPayment.getId()
            );

            kafkaTemplate.send(paymentsEventsTopicName, paymentProcessedEvent);
            logger.info("Published PaymentProcessedEvent for orderId: {}, paymentId: {}",
                    processedPayment.getOrderId(), processedPayment.getId());
        } catch (CreditCardProcessorUnavailableException e) {
            logger.error("Payment failed for orderId: {}: {}", processPaymentCommand.getOrderId(), e.getLocalizedMessage(), e);
            PaymentFailedEvent paymentFailedEvent = new PaymentFailedEvent(
                    processPaymentCommand.getOrderId(),
                    processPaymentCommand.getProductId(),
                    processPaymentCommand.getProductQuantity()
            );

            kafkaTemplate.send(paymentsEventsTopicName, paymentFailedEvent);
            logger.info("Published PaymentFailedEvent for orderId: {}", processPaymentCommand.getOrderId());
        }
    }
}
