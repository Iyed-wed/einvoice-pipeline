package com.einvoice.pipeline.controller;

import com.einvoice.pipeline.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the API is self-documented: the OpenAPI spec is generated from the
 * controllers and describes the invoice endpoint with its response codes, and
 * the Swagger UI is served.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OpenApiDocumentationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void openApiSpecDocumentsTheInvoiceEndpoint() {
        ResponseEntity<String> spec = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(spec.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(spec.getBody())
                .contains("einvoice-pipeline API")
                .contains("/api/invoices")
                .contains("Generate a Factur-X invoice")
                .contains("\"422\"")   // business-rule violation documented
                .contains("\"400\"")   // structural error documented
                // request body schema derived from the Invoice model
                .contains("invoiceNumber")
                .contains("totalWithVat");
    }

    @Test
    void swaggerUiIsServed() {
        // /swagger-ui.html issues a redirect to the UI bundle; follow it.
        ResponseEntity<String> ui = restTemplate.getForEntity("/swagger-ui/index.html", String.class);

        assertThat(ui.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ui.getBody()).containsIgnoringCase("swagger");
    }
}
