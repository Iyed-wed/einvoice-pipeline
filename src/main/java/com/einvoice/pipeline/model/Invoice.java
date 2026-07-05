package com.einvoice.pipeline.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * An invoice as submitted to the pipeline, aligned with the EN 16931 semantic
 * model. Field constraints reference the corresponding Business Terms (BT-x)
 * of the standard so that validation errors speak the domain language.
 *
 * <p>Declared totals are part of the input on purpose: EN 16931 requires them
 * (BG-22) and their consistency against the line amounts is checked by the
 * business validation layer, not silently recomputed.</p>
 */
public record Invoice(

        @NotBlank(message = "Invoice number is mandatory (EN 16931 BT-1)")
        String invoiceNumber,

        @NotNull(message = "Issue date is mandatory (EN 16931 BT-2)")
        LocalDate issueDate,

        /** Optional payment due date (BT-9). */
        LocalDate dueDate,

        @NotNull(message = "Currency code is mandatory (EN 16931 BT-5)")
        @Pattern(regexp = "[A-Z]{3}", message = "Currency must be an ISO 4217 alphabetic code, e.g. EUR (EN 16931 BT-5)")
        String currencyCode,

        @NotNull(message = "Seller party is mandatory (EN 16931 BG-4)")
        @Valid
        Party seller,

        @NotNull(message = "Buyer party is mandatory (EN 16931 BG-7)")
        @Valid
        Party buyer,

        @NotEmpty(message = "An invoice must have at least one line (EN 16931 BG-25)")
        List<@Valid InvoiceLine> lines,

        @NotNull(message = "Total without VAT is mandatory (EN 16931 BT-109)")
        BigDecimal totalWithoutVat,

        @NotNull(message = "Total VAT amount is mandatory (EN 16931 BT-110)")
        BigDecimal totalVat,

        @NotNull(message = "Total with VAT is mandatory (EN 16931 BT-112)")
        BigDecimal totalWithVat
) {
}
