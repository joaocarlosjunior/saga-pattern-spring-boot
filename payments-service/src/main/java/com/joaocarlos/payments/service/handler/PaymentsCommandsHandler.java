package com.joaocarlos.payments.service.handler;

import com.joaocarlos.core.dto.Payment;
import com.joaocarlos.core.dto.commands.ProcessPaymentCommand;
import com.joaocarlos.core.dto.events.PaymentFailedEvent;
import com.joaocarlos.core.dto.events.PaymentProcessedEvent;
import com.joaocarlos.core.exceptions.CreditCardProcessorUnavailableException;
import com.joaocarlos.payments.service.OutboxService;
import com.joaocarlos.payments.service.PaymentService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@KafkaListener(topics = "${payments.commands.topic.name}")
public class PaymentsCommandsHandler {
    private final PaymentService paymentService;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final String paymentsEventsTopicName;
    private final OutboxService outboxService;

    public PaymentsCommandsHandler(PaymentService paymentService,
                                   @Value("${payments.events.topic.name}") String paymentsEventsTopicName,
                                   OutboxService outboxService) {
        this.paymentService = paymentService;
        this.paymentsEventsTopicName = paymentsEventsTopicName;
        this.outboxService = outboxService;
    }

    @KafkaHandler
    @Transactional
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

            outboxService.publishEvent("PAYMENT", processedPayment.getOrderId().toString(), PaymentProcessedEvent.class.getName(), paymentsEventsTopicName, paymentProcessedEvent);
            logger.info("Saved PaymentProcessedEvent to Outbox for orderId: {}, paymentId: {}",
                    processedPayment.getOrderId(), processedPayment.getId());
        } catch (CreditCardProcessorUnavailableException e) {
            logger.error("Payment failed for orderId: {}: {}", processPaymentCommand.getOrderId(), e.getLocalizedMessage(), e);
            PaymentFailedEvent paymentFailedEvent = new PaymentFailedEvent(
                    processPaymentCommand.getOrderId(),
                    processPaymentCommand.getProductId(),
                    processPaymentCommand.getProductQuantity()
            );

            outboxService.publishEvent("PAYMENT", processPaymentCommand.getOrderId().toString(), PaymentFailedEvent.class.getName(), paymentsEventsTopicName, paymentFailedEvent);
            logger.info("Saved PaymentFailedEvent to Outbox for orderId: {}", processPaymentCommand.getOrderId());
        }
    }
}

