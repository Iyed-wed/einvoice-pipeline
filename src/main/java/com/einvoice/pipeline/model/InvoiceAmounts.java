package com.einvoice.pipeline.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Computes the EN 16931 document totals from invoice lines, following the same
 * rounding rules the validator checks against (VAT summed per rate category,
 * each rounded to 2 decimals — BR-CO-17).
 *
 * <p>Used by the entry-point adapters (CSV, SAP) that receive line data without
 * pre-computed totals: they build a canonical, internally consistent invoice
 * which the engine then validates like any other.</p>
 *
 * @param totalWithoutVat sum of line net amounts (BT-109)
 * @param totalVat        total VAT (BT-110)
 * @param totalWithVat    grand total (BT-112)
 */
public record InvoiceAmounts(BigDecimal totalWithoutVat, BigDecimal totalVat, BigDecimal totalWithVat) {

    private static final int SCALE = 2;

    public static InvoiceAmounts fromLines(List<InvoiceLine> lines) {
        BigDecimal net = lines.stream()
                .map(InvoiceLine::netAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, RoundingMode.HALF_UP);

        Map<BigDecimal, BigDecimal> baseByRate = new TreeMap<>();
        for (InvoiceLine line : lines) {
            baseByRate.merge(line.vatRate().stripTrailingZeros(), line.netAmount(), BigDecimal::add);
        }
        BigDecimal vat = baseByRate.entrySet().stream()
                .map(e -> e.getValue().multiply(e.getKey())
                        .divide(new BigDecimal(100))
                        .setScale(SCALE, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new InvoiceAmounts(net, vat, net.add(vat));
    }
}
