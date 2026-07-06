package com.einvoice.pipeline.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API-level OpenAPI metadata. Per-endpoint documentation lives as annotations
 * next to the controller methods, so the spec stays in sync with the code.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI einvoicePipelineOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("einvoice-pipeline API")
                        .version("0.1.0")
                        .description("""
                                Generation and validation engine for European electronic invoices \
                                (Factur-X / EN 16931). Submit an invoice as JSON and receive a \
                                Factur-X file (PDF/A-3 with embedded CII XML), or a structured \
                                RFC 7807 error describing which business rules were violated.""")
                        .contact(new Contact().name("einvoice-pipeline").url("https://github.com/Iyed-wed/einvoice-pipeline"))
                        .license(new License().name("Demonstration project")));
    }
}
