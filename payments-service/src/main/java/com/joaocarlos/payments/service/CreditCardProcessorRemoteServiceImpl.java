package com.joaocarlos.payments.service;

import com.joaocarlos.core.dto.CreditCardProcessRequest;
import com.joaocarlos.core.exceptions.CreditCardProcessorUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.BigInteger;

@Service
public class CreditCardProcessorRemoteServiceImpl implements CreditCardProcessorRemoteService {
    private final String ccpRemoteServiceUrl;
    private final RestClient restClient;
    private static final Logger logger = LoggerFactory.getLogger(CreditCardProcessorRemoteServiceImpl.class);

    public CreditCardProcessorRemoteServiceImpl(
            @Value("${remote.ccp.url}") String ccpRemoteServiceUrl,
            RestClient restClient) {
        this.ccpRemoteServiceUrl = ccpRemoteServiceUrl;
        this.restClient = restClient;
    }

    @Override
    public void process(BigInteger cardNumber, BigDecimal paymentAmount) {
        try {
            logger.info("Sending credit card payment processing request for amount: {} to remote service at: {}", paymentAmount, ccpRemoteServiceUrl);
            var request = new CreditCardProcessRequest(cardNumber, paymentAmount);
            restClient.post()
                    .uri(ccpRemoteServiceUrl + "/ccp/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(CreditCardProcessRequest.class);
            logger.info("Credit card payment processing request of amount: {} succeeded", paymentAmount);
        } catch (ResourceAccessException e) {
            logger.error("Credit card processor remote service unavailable at {}: {}", ccpRemoteServiceUrl, e.getMessage(), e);
            throw new CreditCardProcessorUnavailableException(e);
        }
    }
}
