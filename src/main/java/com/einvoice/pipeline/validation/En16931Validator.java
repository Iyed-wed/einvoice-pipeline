package com.einvoice.pipeline.validation;

import com.einvoice.pipeline.model.Invoice;
import com.einvoice.pipeline.model.InvoiceLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Arithmetic consistency checks from the EN 16931 calculation rules (BR-CO).
 * Declared totals are verified against the line data — never silently
 * recomputed: a compliance engine reports discrepancies, it does not hide them.
 *
 * <p>This model has no document-level allowances/charges (BT-107/BT-108), so
 * the sum of line net amounts must equal the tax exclusive amount directly.</p>
 */
@Component
public class En16931Validator {

    /** EN 16931 amounts are compared at 2 decimals (BR-DEC rules). */
    private static final int SCALE = 2;

    public List<ValidationError> validate(Invoice invoice) {
        List<ValidationError> errors = new ArrayList<>();

        BigDecimal lineSum = invoice.lines().stream()
                .map(InvoiceLine::netAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, RoundingMode.HALF_UP);

        // BR-CO-13: tax exclusive amount = sum of line net amounts
        if (lineSum.compareTo(round(invoice.totalWithoutVat())) != 0) {
            errors.add(new ValidationError("BR-CO-13", "totalWithoutVat",
                    "Declared total without VAT (%s) does not match the sum of line net amounts (%s)"
                            .formatted(plain(invoice.totalWithoutVat()), plain(lineSum))));
        }

        // BR-CO-14: total VAT = sum of VAT amounts per rate category,
        // each rounded at 2 decimals (BR-CO-17)
        BigDecimal computedVat = vatByRateCategory(invoice.lines()).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (computedVat.compareTo(round(invoice.totalVat())) != 0) {
            errors.add(new ValidationError("BR-CO-14", "totalVat",
                    "Declared VAT total (%s) does not match the VAT computed from the lines (%s)"
                            .formatted(plain(invoice.totalVat()), plain(computedVat))));
        }

        // BR-CO-15: tax inclusive amount = tax exclusive amount + VAT total
        BigDecimal expectedWithVat = round(invoice.totalWithoutVat()).add(round(invoice.totalVat()));
        if (expectedWithVat.compareTo(round(invoice.totalWithVat())) != 0) {
            errors.add(new ValidationError("BR-CO-15", "totalWithVat",
                    "Declared total with VAT (%s) does not equal total without VAT + VAT (%s)"
                            .formatted(plain(invoice.totalWithVat()), plain(expectedWithVat))));
        }

        return errors;
    }

    /** Groups line net amounts by VAT rate and computes the VAT per category, rounded per category. */
    private static Map<BigDecimal, BigDecimal> vatByRateCategory(List<InvoiceLine> lines) {
        Map<BigDecimal, BigDecimal> baseByRate = new TreeMap<>();
        for (InvoiceLine line : lines) {
            baseByRate.merge(line.vatRate().stripTrailingZeros(), line.netAmount(), BigDecimal::add);
        }
        Map<BigDecimal, BigDecimal> vatByRate = new TreeMap<>();
        baseByRate.forEach((rate, base) -> vatByRate.put(rate,
                base.multiply(rate).divide(new BigDecimal(100)).setScale(SCALE, RoundingMode.HALF_UP)));
        return vatByRate;
    }

    private static BigDecimal round(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.HALF_UP).toPlainString();
    }
}
