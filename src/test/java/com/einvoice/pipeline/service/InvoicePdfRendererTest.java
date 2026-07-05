package com.einvoice.pipeline.service;

import com.einvoice.pipeline.model.InvoiceFixtures;
import org.apache.pdfbox.preflight.ValidationResult;
import org.apache.pdfbox.preflight.parser.PreflightParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rendered visual must be a conformant PDF/A-1b document: Mustang refuses
 * non-conformant input, so this is a hard functional requirement, verified
 * here with PDFBox Preflight (the same validator Mustang uses internally).
 */
class InvoicePdfRendererTest {

    @Test
    void rendersAConformantPdfA1b() throws Exception {
        byte[] pdf = new InvoicePdfRenderer().render(InvoiceFixtures.validInvoice());

        File tmp = File.createTempFile("einvoice-visual", ".pdf");
        try {
            Files.write(tmp.toPath(), pdf);
            ValidationResult result = PreflightParser.validate(tmp);

            assertThat(result.getErrorsList()).isEmpty();
            assertThat(result.isValid()).isTrue();
        } finally {
            tmp.delete();
        }
    }
}
