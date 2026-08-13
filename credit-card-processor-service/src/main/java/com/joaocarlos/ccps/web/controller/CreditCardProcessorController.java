package com.joaocarlos.ccps.web.controller;

import com.joaocarlos.core.dto.CreditCardProcessRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("ccp")
public class CreditCardProcessorController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreditCardProcessorController.class);

    @PostMapping("/process")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void processCreditCard(@RequestBody @Valid CreditCardProcessRequest request) {
        LOGGER.info("REST request to process credit card payment for amount: {}", request.getPaymentAmount());
        LOGGER.info("Credit card payment processing accepted for amount: {}", request.getPaymentAmount());
    }
}
