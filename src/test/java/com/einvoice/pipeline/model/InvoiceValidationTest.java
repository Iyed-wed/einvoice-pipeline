package com.einvoice.pipeline.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests (no Spring context) proving that Bean Validation enforces
 * the structurally mandatory fields of the invoice model.
 */
class InvoiceValidationTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<ConstraintViolation<Invoice>> validate(Invoice invoice) {
        return VALIDATOR.validate(invoice);
    }

    private static void assertViolationOn(Set<ConstraintViolation<Invoice>> violations, String path) {
        assertThat(violations)
                .as("expected a violation on '%s'", path)
                .anyMatch(v -> v.getPropertyPath().toString().equals(path));
    }

    @Test
    void validInvoiceHasNoViolations() {
        assertThat(validate(InvoiceFixtures.validInvoice())).isEmpty();
    }

    @Test
    void invoiceNumberIsMandatory() {
        Invoice invoice = withInvoiceNumber("  ");
        Set<ConstraintViolation<Invoice>> violations = validate(invoice);

        assertViolationOn(violations, "invoiceNumber");
        assertThat(violations.iterator().next().getMessage()).contains("BT-1");
    }

    @Test
    void issueDateIsMandatory() {
        Invoice base = InvoiceFixtures.validInvoice();
        Invoice invoice = new Invoice(base.invoiceNumber(), null, base.dueDate(), base.currencyCode(),
                base.seller(), base.buyer(), base.lines(),
                base.totalWithoutVat(), base.totalVat(), base.totalWithVat());

        assertViolationOn(validate(invoice), "issueDate");
    }

    @Test
    void currencyMustBeIso4217Alphabetic() {
        Invoice base = InvoiceFixtures.validInvoice();
        Invoice invoice = new Invoice(base.invoiceNumber(), base.issueDate(), base.dueDate(), "euro",
                base.seller(), base.buyer(), base.lines(),
                base.totalWithoutVat(), base.totalVat(), base.totalWithVat());

        assertViolationOn(validate(invoice), "currencyCode");
    }

    @Test
    void sellerNameIsValidatedThroughCascade() {
        Invoice base = InvoiceFixtures.validInvoice();
        Party sellerWithoutName = new Party(null, "FR12345678901", InvoiceFixtures.frenchAddress());
        Invoice invoice = new Invoice(base.invoiceNumber(), base.issueDate(), base.dueDate(), base.currencyCode(),
                sellerWithoutName, base.buyer(), base.lines(),
                base.totalWithoutVat(), base.totalVat(), base.totalWithVat());

        assertViolationOn(validate(invoice), "seller.name");
    }

    @Test
    void countryCodeIsValidatedThroughNestedCascade() {
        Invoice base = InvoiceFixtures.validInvoice();
        Party buyerWithBadCountry = new Party("Client Corp SA", null,
                new Address(null, null, "Tunis", "Tunisie"));
        Invoice invoice = new Invoice(base.invoiceNumber(), base.issueDate(), base.dueDate(), base.currencyCode(),
                base.seller(), buyerWithBadCountry, base.lines(),
                base.totalWithoutVat(), base.totalVat(), base.totalWithVat());

        assertViolationOn(validate(invoice), "buyer.address.countryCode");
    }

    @Test
    void atLeastOneLineIsRequired() {
        Invoice base = InvoiceFixtures.validInvoice();
        Invoice invoice = new Invoice(base.invoiceNumber(), base.issueDate(), base.dueDate(), base.currencyCode(),
                base.seller(), base.buyer(), List.of(),
                base.totalWithoutVat(), base.totalVat(), base.totalWithVat());

        assertViolationOn(validate(invoice), "lines");
    }

    @Test
    void lineQuantityMustBePositive() {
        Invoice base = InvoiceFixtures.validInvoice();
        InvoiceLine badLine = new InvoiceLine("Consulting", new BigDecimal("-1"),
                new BigDecimal("100.00"), new BigDecimal("20.00"));
        Invoice invoice = new Invoice(base.invoiceNumber(), base.issueDate(), base.dueDate(), base.currencyCode(),
                base.seller(), base.buyer(), List.of(badLine),
                base.totalWithoutVat(), base.totalVat(), base.totalWithVat());

        assertViolationOn(validate(invoice), "lines[0].quantity");
    }

    @Test
    void declaredTotalsAreMandatory() {
        Invoice base = InvoiceFixtures.validInvoice();
        Invoice invoice = new Invoice(base.invoiceNumber(), base.issueDate(), base.dueDate(), base.currencyCode(),
                base.seller(), base.buyer(), base.lines(),
                null, null, null);
        Set<ConstraintViolation<Invoice>> violations = validate(invoice);

        assertViolationOn(violations, "totalWithoutVat");
        assertViolationOn(violations, "totalVat");
        assertViolationOn(violations, "totalWithVat");
    }

    private static Invoice withInvoiceNumber(String invoiceNumber) {
        Invoice base = InvoiceFixtures.validInvoice();
        return new Invoice(invoiceNumber, base.issueDate(), base.dueDate(), base.currencyCode(),
                base.seller(), base.buyer(), base.lines(),
                base.totalWithoutVat(), base.totalVat(), base.totalWithVat());
    }
}
