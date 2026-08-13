package com.joaocarlos.payments.service;

import com.joaocarlos.payments.dao.jpa.entity.PaymentEntity;
import com.joaocarlos.payments.dao.jpa.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import com.joaocarlos.core.dto.Payment;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PaymentServiceImpl implements PaymentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentServiceImpl.class);

    public static final String SAMPLE_CREDIT_CARD_NUMBER = "374245455400126";
    private final PaymentRepository paymentRepository;
    private final CreditCardProcessorRemoteService ccpRemoteService;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              CreditCardProcessorRemoteService ccpRemoteService) {
        this.paymentRepository = paymentRepository;
        this.ccpRemoteService = ccpRemoteService;
    }

    @Override
    public Payment process(Payment payment) {
        LOGGER.info("Processing payment for orderId: {}, productId: {}, price: {}, quantity: {}",
                payment.getOrderId(), payment.getProductId(), payment.getProductPrice(), payment.getProductQuantity());
        BigDecimal totalPrice = payment.getProductPrice()
                .multiply(new BigDecimal(payment.getProductQuantity()));
        LOGGER.info("Calculated total payment amount: {} for orderId: {}", totalPrice, payment.getOrderId());
        ccpRemoteService.process(new BigInteger(SAMPLE_CREDIT_CARD_NUMBER), totalPrice);
        PaymentEntity paymentEntity = new PaymentEntity();
        BeanUtils.copyProperties(payment, paymentEntity);
        paymentRepository.save(paymentEntity);
        LOGGER.info("Payment saved in DB with paymentId: {} for orderId: {}", paymentEntity.getId(), payment.getOrderId());

        var processedPayment = new Payment();
        BeanUtils.copyProperties(payment, processedPayment);
        processedPayment.setId(paymentEntity.getId());
        return processedPayment;
    }

    @Override
    public List<Payment> findAll() {
        LOGGER.info("Fetching all payments from repository");
        List<Payment> payments = paymentRepository.findAll().stream().map(entity -> new Payment(entity.getId(), entity.getOrderId(), entity.getProductId(), entity.getProductPrice(), entity.getProductQuantity())
        ).collect(Collectors.toList());
        LOGGER.info("Retrieved {} payment record(s)", payments.size());
        return payments;
    }
}
