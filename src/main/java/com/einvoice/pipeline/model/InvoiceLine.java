package com.einvoice.pipeline.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * A single invoice line (EN 16931 BG-25).
 */
public record InvoiceLine(

        @NotBlank(message = "Line description is mandatory (EN 16931 BT-153)")
        String description,

        @NotNull(message = "Quantity is mandatory (EN 16931 BT-129)")
        @Positive(message = "Quantity must be strictly positive (EN 16931 BT-129)")
        BigDecimal quantity,

        @NotNull(message = "Unit price is mandatory (EN 16931 BT-146)")
        @DecimalMin(value = "0", message = "Unit price cannot be negative (EN 16931 BT-146)")
        BigDecimal unitPrice,

        /** VAT rate in percent, e.g. 20.00 for standard French VAT (BT-152). */
        @NotNull(message = "VAT rate is mandatory (EN 16931 BT-152)")
        @DecimalMin(value = "0", message = "VAT rate cannot be negative (EN 16931 BT-152)")
        @DecimalMax(value = "100", message = "VAT rate is a percentage and cannot exceed 100 (EN 16931 BT-152)")
        BigDecimal vatRate
) {

    /** Net line amount (BT-131): quantity × unit price. */
    public BigDecimal netAmount() {
        return quantity.multiply(unitPrice);
    }
}
