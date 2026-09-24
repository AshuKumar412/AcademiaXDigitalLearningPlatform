package soa.apigateway.config;

import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;

@Configuration
public class GatewayConfig {

    @Bean
    public RouterFunction<ServerResponse> gatewayRoutes() {

        return GatewayRouterFunctions.route("auth-service")
                .route(
                        request -> request.path().startsWith("/auth"),
                        HandlerFunctions.http()
                )
                .filter(lb("AUTH-SERVICE"))
                .build()

                .and(
                        GatewayRouterFunctions.route("user-service")
                                .route(
                                        request -> request.path().startsWith("/users"),
                                        HandlerFunctions.http()
                                )
                                .filter(lb("USER-SERVICE"))
                                .build()
                )

                .and(
                        GatewayRouterFunctions.route("course-service")
                                .route(
                                        request -> request.path().startsWith("/courses"),
                                        HandlerFunctions.http()
                                )
                                .filter(lb("COURSE-SERVICE"))
                                .build()
                )

                .and(
                        GatewayRouterFunctions.route("enrollment-service")
                                .route(
                                        request -> request.path().startsWith("/enrollments"),
                                        HandlerFunctions.http()
                                )
                                .filter(lb("ENROLLMENT-SERVICE"))
                                .build()
                )

                .and(
                        GatewayRouterFunctions.route("payment-service")
                                .route(
                                        request -> request.path().startsWith("/payments"),
                                        HandlerFunctions.http()
                                )
                                .filter(lb("PAYMENT-SERVICE"))
                                .build()
                );
    }
}