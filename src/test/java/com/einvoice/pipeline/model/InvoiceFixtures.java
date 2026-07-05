package com.einvoice.pipeline.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Reusable invoice test data. The valid invoice is arithmetically coherent
 * (lines: 2×100.00 + 1×50.00 = 250.00 net, 20% VAT = 50.00, gross 300.00)
 * so later tasks can reuse it for generation and business validation tests.
 */
public final class InvoiceFixtures {

    private InvoiceFixtures() {
    }

    public static Address frenchAddress() {
        return new Address("12 rue de la Paix", "75002", "Paris", "FR");
    }

    public static Party seller() {
        return new Party("ACME Services SARL", "FR12345678901", frenchAddress());
    }

    public static Party buyer() {
        return new Party("Client Corp SA", "FR98765432109", frenchAddress());
    }

    public static List<InvoiceLine> lines() {
        return List.of(
                new InvoiceLine("Consulting services", new BigDecimal("2"), new BigDecimal("100.00"), new BigDecimal("20.00")),
                new InvoiceLine("Support package", new BigDecimal("1"), new BigDecimal("50.00"), new BigDecimal("20.00"))
        );
    }

    public static Invoice validInvoice() {
        return new Invoice(
                "FA-2026-0001",
                LocalDate.of(2026, 7, 5),
                LocalDate.of(2026, 8, 4),
                "EUR",
                seller(),
                buyer(),
                lines(),
                new BigDecimal("250.00"),
                new BigDecimal("50.00"),
                new BigDecimal("300.00")
        );
    }
}
