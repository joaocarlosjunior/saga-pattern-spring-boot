package com.joaocarlos.payments.service;


import java.util.List;
import com.joaocarlos.core.dto.Payment;

public interface PaymentService {
    List<Payment> findAll();

    Payment process(Payment payment);
}
