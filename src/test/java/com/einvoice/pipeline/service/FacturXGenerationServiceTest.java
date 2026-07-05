package com.einvoice.pipeline.service;

import com.einvoice.pipeline.model.InvoiceFixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mustangproject.ZUGFeRD.ZUGFeRDImporter;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the generated file is a genuine Factur-X: a PDF containing an
 * embedded EN 16931 CII XML that a receiving system can re-extract and read.
 * The round-trip (generate → re-import with Mustang) is the strongest
 * verification available without an external validator service.
 */
class FacturXGenerationServiceTest {

    private static byte[] facturX;

    @BeforeAll
    static void generateOnce() {
        FacturXGenerationService service = new FacturXGenerationService(new InvoicePdfRenderer());
        facturX = service.generateFacturX(InvoiceFixtures.validInvoice());
    }

    @Test
    void producesAPdfFile() {
        assertThat(facturX).isNotEmpty();
        assertThat(new String(facturX, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void embeddedXmlIsExtractableAndCarriesInvoiceData() {
        ZUGFeRDImporter importer = new ZUGFeRDImporter(new ByteArrayInputStream(facturX));
        String xml = importer.getUTF8();

        assertThat(xml)
                .as("embedded CII XML")
                .contains("CrossIndustryInvoice")
                .contains("FA-2026-0001")
                .contains("ACME Services SARL")
                .contains("Client Corp SA");
    }

    @Test
    void embeddedXmlTotalsMatchTheFixture() {
        ZUGFeRDImporter importer = new ZUGFeRDImporter(new ByteArrayInputStream(facturX));

        // Mustang recomputes document totals from the lines: 2×100.00 + 1×50.00
        // = 250.00 net, +20% VAT = 300.00 — matching the fixture's declared totals.
        assertThat(importer.getAmount()).isEqualTo("300.00");
    }
}
