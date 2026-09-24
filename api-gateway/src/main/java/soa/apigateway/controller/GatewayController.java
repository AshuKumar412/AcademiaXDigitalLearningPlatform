package soa.apigateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GatewayController {

    @GetMapping("/gateway")
    public String gateway() {
        return "AcademiaX API Gateway is running";
    }

    @GetMapping("/gateway/status")
    public String status() {
        return "AcademiaX API Gateway is healthy";
    }
}
