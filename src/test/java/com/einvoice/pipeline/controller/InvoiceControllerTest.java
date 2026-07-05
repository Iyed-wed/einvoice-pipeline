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
    void structurallyInvalidInvoiceIsRejectedWith400AndStructuredBody() {
        Invoice base = InvoiceFixtures.validInvoice();
        Invoice missingNumber = new Invoice(null, base.issueDate(), base.dueDate(), base.currencyCode(),
                base.seller(), base.buyer(), base.lines(),
                base.totalWithoutVat(), base.totalVat(), base.totalWithVat());

        ResponseEntity<String> response = postForProblem(missingNumber);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody())
                .contains("Invoice structure is invalid")
                .contains("invoiceNumber")
                .contains("BT-1")
                .doesNotContain("Exception")
                .doesNotContain("at com.einvoice"); // no stack trace leaks to the client
    }

    @Test
    void businessRuleViolationIsRejectedWith422AndRuleReferences() {
        Invoice base = InvoiceFixtures.validInvoice();
        // Well-formed but arithmetically wrong: VAT and grand total do not add up
        Invoice incoherent = new Invoice(base.invoiceNumber(), base.issueDate(), base.dueDate(),
                base.currencyCode(), base.seller(), base.buyer(), base.lines(),
                base.totalWithoutVat(), new java.math.BigDecimal("37.00"), new java.math.BigDecimal("310.00"));

        ResponseEntity<String> response = postForProblem(incoherent);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody())
                .contains("EN 16931")
                .contains("BR-CO-14")
                .contains("BR-CO-15")
                .doesNotContain("at com.einvoice");
    }

    private ResponseEntity<byte[]> post(Invoice invoice) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("/api/invoices", new HttpEntity<>(invoice, headers), byte[].class);
    }

    private ResponseEntity<String> postForProblem(Invoice invoice) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("/api/invoices", new HttpEntity<>(invoice, headers), String.class);
    }
}
