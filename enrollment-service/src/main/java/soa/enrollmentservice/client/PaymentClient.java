package soa.enrollmentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import soa.enrollmentservice.dto.PaymentClientRequest;
import soa.enrollmentservice.dto.PaymentClientResponse;

@FeignClient(name = "payment-service")
public interface PaymentClient {

    @PostMapping("/payments")
    PaymentClientResponse createPayment(@RequestBody PaymentClientRequest request);
}
