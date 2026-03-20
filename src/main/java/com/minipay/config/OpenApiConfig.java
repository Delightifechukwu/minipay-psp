package com.minipay.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI miniPayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MiniPay PSP API")
                        .description("""
                            Production-ready Payment Service Provider backend.
                            
                            **Authentication:** Use `POST /api/auth/login` to obtain a JWT token,
                            then click **Authorize** and enter `Bearer <your-token>`.
                            
                            **Idempotency:** Supply `Idempotency-Key` header on `POST /api/payments` for safe retries.
                            
                            **Roles:** ADMIN > MAKER/CHECKER > MERCHANT_USER.
                            Seed admin: `admin / Admin@123`
                            """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("MiniPay Engineering")
                                .email("engineering@minipay.com"))
                        .license(new License().name("MIT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter JWT token obtained from /api/auth/login")));
    }
}
