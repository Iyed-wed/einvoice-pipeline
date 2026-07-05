package com.einvoice.pipeline.controller;

import com.einvoice.pipeline.model.Invoice;
import com.einvoice.pipeline.model.InvoiceFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end HTTP test: JSON in, Factur-X PDF out, over a real servlet stack
 * (serialization, Bean Validation and content negotiation included).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InvoiceControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void validJsonProducesAPdfResponse() {
        ResponseEntity<byte[]> response = post(InvoiceFixtures.validInvoice());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("FA-2026-0001.pdf");
        assertThat(new String(response.getBody(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void structurallyInvalidInvoiceIsRejectedWith400() {
        Invoice base = InvoiceFixtures.validInvoice();
        Invoice missingNumber = new Invoice(null, base.issueDate(), base.dueDate(), base.currencyCode(),
                base.seller(), base.buyer(), base.lines(),
                base.totalWithoutVat(), base.totalVat(), base.totalWithVat());

        ResponseEntity<byte[]> response = post(missingNumber);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<byte[]> post(Invoice invoice) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("/api/invoices", new HttpEntity<>(invoice, headers), byte[].class);
    }
}
