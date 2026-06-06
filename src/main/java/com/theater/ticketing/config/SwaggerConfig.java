package com.theater.ticketing.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI theaterTicketingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Theater Ticketing System API")
                        .description("Redis-based voucher management with JWT authentication")
                        .version("1.0.0")
                        .contact(new Contact().name("Theater Team").email("support@theater.com")))
                .externalDocs(new ExternalDocumentation()
                        .description("Case Study Documentation")
                        .url("https://theater.com/docs"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
