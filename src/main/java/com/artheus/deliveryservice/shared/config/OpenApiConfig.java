package com.artheus.deliveryservice.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI deliveryServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Delivery Service API")
                        .description("Receives delivery requests from the webhook-service and performs the actual HTTP delivery to subscriber endpoints, asynchronously. Part of the Microservices Lab, a distributed systems study project.")
                        .version("v1")
                        .contact(new Contact()
                                .name("Artheus")
                                .url("https://github.com/arthsdev/delivery-service")));
    }
}