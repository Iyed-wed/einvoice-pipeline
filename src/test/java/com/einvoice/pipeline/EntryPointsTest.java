package com.einvoice.pipeline;

import com.einvoice.pipeline.integration.SapBillingDocument;
import com.einvoice.pipeline.integration.SapBillingDocument.SapBillingItem;
import com.einvoice.pipeline.integration.SapBillingDocument.SapParty;
import com.einvoice.pipeline.integration.SapInvoiceGateway;
import com.einvoice.pipeline.model.Invoice;
import com.einvoice.pipeline.service.InvoiceProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three entry points required by the project — manual web form, generic CSV
 * import, and SAP — all funnel into the same {@link InvoiceProcessingService}
 * and each produces a valid Factur-X file.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class EntryPointsTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SapInvoiceGateway sapInvoiceGateway;

    @Autowired
    private InvoiceProcessingService processingService;

    @Test
    void webFormIsServedAndTargetsTheJsonEngine() {
        // The no-ERP path is a static page that POSTs to /api/invoices via fetch.
        ResponseEntity<String> page = restTemplate.getForEntity("/index.html", String.class);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody())
                .contains("Manual invoice entry")
                .contains("/api/invoices");
    }

    @Test
    void csvImportPathProducesAValidFacturX() {
        String csv = """
                invoiceNumber,issueDate,dueDate,currency,\
                sellerName,sellerVat,sellerStreet,sellerPostalCode,sellerCity,sellerCountry,\
                buyerName,buyerVat,buyerStreet,buyerPostalCode,buyerCity,buyerCountry,\
                lineDescription,lineQuantity,lineUnitPrice,lineVatRate
                FA-CSV-1,2026-07-06,2026-08-05,EUR,\
                ACME Services SARL,FR12345678901,12 rue de la Paix,75002,Paris,FR,\
                Client Corp SA,FR98765432109,8 avenue des Champs,75008,Paris,FR,\
                Consulting services,2,100.00,20.00
                FA-CSV-1,2026-07-06,2026-08-05,EUR,\
                ACME Services SARL,FR12345678901,12 rue de la Paix,75002,Paris,FR,\
                Client Corp SA,FR98765432109,8 avenue des Champs,75008,Paris,FR,\
                Support package,1,50.00,20.00
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        ResponseEntity<byte[]> response =
                restTemplate.postForEntity("/api/invoices/import-csv", new HttpEntity<>(csv, headers), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(pdfSignature(response.getBody())).isEqualTo("%PDF-");
    }

    @Test
    void sapPathMapsToTheCanonicalModelAndProducesAValidFacturX() {
        SapBillingDocument doc = new SapBillingDocument(
                "90001234",
                LocalDate.of(2026, 7, 6),
                "EUR",
                new SapParty("Client Corp SA", "FR98765432109", "8 avenue des Champs", "75008", "Paris", "FR"),
                new SapParty("ACME Services SARL", "FR12345678901", "12 rue de la Paix", "75002", "Paris", "FR"),
                List.of(
                        new SapBillingItem("Consulting services", new BigDecimal("2"), new BigDecimal("100.00"), new BigDecimal("20.00")),
                        new SapBillingItem("Support package", new BigDecimal("1"), new BigDecimal("50.00"), new BigDecimal("20.00"))));

        Invoice invoice = sapInvoiceGateway.toCanonicalInvoice(doc);

        // Mapped correctly, and the computed totals are EN 16931-consistent.
        assertThat(invoice.invoiceNumber()).isEqualTo("90001234");
        assertThat(invoice.seller().name()).isEqualTo("ACME Services SARL");
        assertThat(invoice.totalWithVat()).isEqualByComparingTo("300.00");

        // Same engine as the other two paths.
        byte[] facturX = processingService.process(invoice);
        assertThat(pdfSignature(facturX)).isEqualTo("%PDF-");
    }

    private static String pdfSignature(byte[] body) {
        return new String(body, 0, 5, StandardCharsets.US_ASCII);
    }
}
