package com.einvoice.pipeline.service;

import com.einvoice.pipeline.model.Address;
import com.einvoice.pipeline.model.Invoice;
import com.einvoice.pipeline.model.InvoiceAmounts;
import com.einvoice.pipeline.model.InvoiceLine;
import com.einvoice.pipeline.model.Party;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic CSV entry point — the "ERP other than SAP" case. Maps a flat CSV
 * export into the canonical {@link Invoice}. One row per invoice line; the
 * invoice-header columns repeat on every row (the usual shape of a tabular ERP
 * export). All rows are assumed to belong to a single invoice.
 *
 * <p>The ERP export carries line data but not the EN 16931 declared totals, so
 * they are computed here with {@link InvoiceAmounts}; the resulting invoice is
 * then validated by the same engine as any other input.</p>
 *
 * <p>Expected header (order-independent, matched by name):</p>
 * <pre>
 * invoiceNumber,issueDate,dueDate,currency,
 * sellerName,sellerVat,sellerStreet,sellerPostalCode,sellerCity,sellerCountry,
 * buyerName,buyerVat,buyerStreet,buyerPostalCode,buyerCity,buyerCountry,
 * lineDescription,lineQuantity,lineUnitPrice,lineVatRate
 * </pre>
 */
@Component
public class CsvInvoiceParser {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .setTrim(true)
            .build();

    public Invoice parse(String csv) {
        List<CSVRecord> rows = readRows(csv);
        if (rows.isEmpty()) {
            throw new CsvImportException("CSV contains no invoice line rows");
        }

        CSVRecord header = rows.getFirst();
        List<InvoiceLine> lines = new ArrayList<>();
        for (CSVRecord row : rows) {
            lines.add(new InvoiceLine(
                    row.get("lineDescription"),
                    decimal(row, "lineQuantity"),
                    decimal(row, "lineUnitPrice"),
                    decimal(row, "lineVatRate")));
        }

        InvoiceAmounts totals = InvoiceAmounts.fromLines(lines);

        return new Invoice(
                header.get("invoiceNumber"),
                date(header, "issueDate"),
                optionalDate(header, "dueDate"),
                header.get("currency"),
                party(header, "seller"),
                party(header, "buyer"),
                lines,
                totals.totalWithoutVat(),
                totals.totalVat(),
                totals.totalWithVat());
    }

    private static List<CSVRecord> readRows(String csv) {
        try (CSVParser parser = CSVParser.parse(new StringReader(csv), FORMAT)) {
            return parser.getRecords();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read CSV", e);
        } catch (IllegalArgumentException e) {
            throw new CsvImportException("Malformed CSV: " + e.getMessage());
        }
    }

    private static Party party(CSVRecord row, String prefix) {
        return new Party(
                row.get(prefix + "Name"),
                blankToNull(row.get(prefix + "Vat")),
                new Address(
                        blankToNull(row.get(prefix + "Street")),
                        blankToNull(row.get(prefix + "PostalCode")),
                        blankToNull(row.get(prefix + "City")),
                        row.get(prefix + "Country")));
    }

    private static BigDecimal decimal(CSVRecord row, String column) {
        String raw = row.get(column);
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new CsvImportException("Column '%s' is not a number: '%s'".formatted(column, raw));
        }
    }

    private static LocalDate date(CSVRecord row, String column) {
        try {
            return LocalDate.parse(row.get(column).trim());
        } catch (RuntimeException e) {
            throw new CsvImportException("Column '%s' is not an ISO date (yyyy-MM-dd)".formatted(column));
        }
    }

    private static LocalDate optionalDate(CSVRecord row, String column) {
        if (!row.isMapped(column)) {
            return null;
        }
        String value = row.get(column);
        return (value == null || value.isBlank()) ? null : date(row, column);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
