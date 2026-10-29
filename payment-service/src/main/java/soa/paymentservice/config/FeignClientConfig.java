package soa.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestInterceptor;

@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                String authHeader = attributes.getRequest().getHeader("Authorization");
                if (authHeader != null && !authHeader.isBlank()) {
                    requestTemplate.header("Authorization", authHeader);
                }
                String userId = attributes.getRequest().getHeader("X-User-Id");
                if (userId != null && !userId.isBlank()) {
                    requestTemplate.header("X-User-Id", userId);
                }
                String userRole = attributes.getRequest().getHeader("X-User-Role");
                if (userRole != null && !userRole.isBlank()) {
                    requestTemplate.header("X-User-Role", userRole);
                }
            }
        };
    }
}
