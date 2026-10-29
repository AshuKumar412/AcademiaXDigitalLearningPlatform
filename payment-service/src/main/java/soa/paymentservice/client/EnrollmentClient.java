package soa.paymentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import soa.paymentservice.dto.EnrollmentClientResponse;

@FeignClient(name = "enrollment-service")
public interface EnrollmentClient {

    @GetMapping("/enrollments/{id}/exists")
    ResponseEntity<Boolean> existsById(@PathVariable("id") Long id);

    @GetMapping("/enrollments/{id}")
    EnrollmentClientResponse getEnrollmentById(@PathVariable("id") Long id);
}
