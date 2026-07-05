package com.einvoice.pipeline.controller;

import com.einvoice.pipeline.model.Invoice;
import com.einvoice.pipeline.service.FacturXGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final FacturXGenerationService generationService;

    public InvoiceController(FacturXGenerationService generationService) {
        this.generationService = generationService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> generateInvoice(@Valid @RequestBody Invoice invoice) {
        byte[] facturX = generationService.generateFacturX(invoice);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeFilename(invoice.invoiceNumber()) + ".pdf\"")
                .body(facturX);
    }

    /** Invoice numbers are client input; never let them inject header characters or paths. */
    private static String safeFilename(String invoiceNumber) {
        return invoiceNumber.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
