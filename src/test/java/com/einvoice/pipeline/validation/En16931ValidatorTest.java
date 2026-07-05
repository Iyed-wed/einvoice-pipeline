package com.einvoice.pipeline.validation;

import com.einvoice.pipeline.model.Invoice;
import com.einvoice.pipeline.model.InvoiceFixtures;
import com.einvoice.pipeline.model.InvoiceLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the EN 16931 arithmetic rules (BR-CO-13/14/15), including
 * per-VAT-category rounding.
 */
class En16931ValidatorTest {

    private final En16931Validator validator = new En16931Validator();

    @Test
    void coherentInvoicePasses() {
        assertThat(validator.validate(InvoiceFixtures.validInvoice())).isEmpty();
    }

    @Test
    void lineSumMismatchViolatesBrCo13() {
        // Lines sum to 250.00 but 999.99 is declared
        Invoice invoice = withTotals("999.99", "50.00", "1049.99");

        List<ValidationError> errors = validator.validate(invoice);

        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().rule()).isEqualTo("BR-CO-13");
        assertThat(errors.getFirst().field()).isEqualTo("totalWithoutVat");
        assertThat(errors.getFirst().message()).contains("999.99").contains("250.00");
    }

    @Test
    void vatMismatchViolatesBrCo14() {
        // Correct base 250.00 at 20% gives 50.00 VAT, not 37.00
        Invoice invoice = withTotals("250.00", "37.00", "287.00");

        List<ValidationError> errors = validator.validate(invoice);

        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().rule()).isEqualTo("BR-CO-14");
        assertThat(errors.getFirst().message()).contains("37.00").contains("50.00");
    }

    @Test
    void inconsistentGrandTotalViolatesBrCo15() {
        // 250.00 + 50.00 is 300.00, not 310.00
        Invoice invoice = withTotals("250.00", "50.00", "310.00");

        List<ValidationError> errors = validator.validate(invoice);

        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().rule()).isEqualTo("BR-CO-15");
        assertThat(errors.getFirst().message()).contains("310.00").contains("300.00");
    }

    @Test
    void everyViolationIsReportedAtOnce() {
        Invoice invoice = withTotals("1.00", "2.00", "999.00");

        assertThat(validator.validate(invoice))
                .extracting(ValidationError::rule)
                .containsExactlyInAnyOrder("BR-CO-13", "BR-CO-14", "BR-CO-15");
    }

    @Test
    void vatIsRoundedPerRateCategoryNotPerLine() {
        // 3 × 33.33 = 99.99 at 20% -> 19.998 rounds to 20.00 (category-level, BR-CO-17)
        // 1 × 50.00 at 10% -> 5.00
        List<InvoiceLine> lines = List.of(
                new InvoiceLine("Licences", new BigDecimal("3"), new BigDecimal("33.33"), new BigDecimal("20.00")),
                new InvoiceLine("Manuel", new BigDecimal("1"), new BigDecimal("50.00"), new BigDecimal("10.00")));
        Invoice invoice = new Invoice("FA-2026-0002", LocalDate.of(2026, 7, 5), null, "EUR",
                InvoiceFixtures.seller(), InvoiceFixtures.buyer(), lines,
                new BigDecimal("149.99"), new BigDecimal("25.00"), new BigDecimal("174.99"));

        assertThat(validator.validate(invoice)).isEmpty();
    }

    private static Invoice withTotals(String withoutVat, String vat, String withVat) {
        Invoice base = InvoiceFixtures.validInvoice();
        return new Invoice(base.invoiceNumber(), base.issueDate(), base.dueDate(), base.currencyCode(),
                base.seller(), base.buyer(), base.lines(),
                new BigDecimal(withoutVat), new BigDecimal(vat), new BigDecimal(withVat));
    }
}
