package com.einvoice.pipeline.controller;

import com.einvoice.pipeline.model.Invoice;
import com.einvoice.pipeline.service.InvoiceProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Invoices", description = "Generate and validate Factur-X / EN 16931 electronic invoices")
public class InvoiceController {

    private final InvoiceProcessingService processingService;

    public InvoiceController(InvoiceProcessingService processingService) {
        this.processingService = processingService;
    }

    @Operation(
            summary = "Generate a Factur-X invoice",
            description = """
                    Validates the submitted invoice against the EN 16931 business rules, then \
                    returns a Factur-X file (PDF/A-3 with embedded CII XML). A conformant invoice \
                    is persisted as GENERATED; a non-conformant one is recorded as REJECTED and \
                    the reasons are returned as an RFC 7807 problem document.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Factur-X PDF/A-3 file",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "Malformed request: a mandatory field is missing or wrongly formatted",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
            @ApiResponse(responseCode = "422", description = "Well-formed invoice that violates EN 16931 business rules (amount/VAT consistency)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> generateInvoice(@Valid @RequestBody Invoice invoice) {
        byte[] facturX = processingService.process(invoice);
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
