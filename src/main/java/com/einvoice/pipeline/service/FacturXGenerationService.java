package com.einvoice.pipeline.service;

import com.einvoice.pipeline.model.Invoice;
import com.einvoice.pipeline.model.InvoiceLine;
import com.einvoice.pipeline.model.Party;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.IZUGFeRDExporter;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA1;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * Generates a Factur-X invoice: a PDF/A-3 file with the EN 16931 CII XML
 * embedded as an attachment, readable both by humans and by machines.
 *
 * <p>The format logic (XML writing, PDF/A-3 conversion, file attachment
 * relationships) is fully delegated to Mustang Project — the reference
 * open-source implementation — rather than reimplemented.</p>
 */
@Service
public class FacturXGenerationService {

    /** EN 16931 profile (also known as "Comfort"): the European-norm subset of Factur-X. */
    private static final String FACTURX_PROFILE = "EN16931";

    private final InvoicePdfRenderer pdfRenderer;

    public FacturXGenerationService(InvoicePdfRenderer pdfRenderer) {
        this.pdfRenderer = pdfRenderer;
    }

    public byte[] generateFacturX(Invoice invoice) {
        byte[] visualPdf = pdfRenderer.render(invoice);
        try {
            IZUGFeRDExporter exporter = new ZUGFeRDExporterFromA1()
                    .setProducer("einvoice-pipeline")
                    .setCreator("einvoice-pipeline")
                    .setProfile(Profiles.getByName(FACTURX_PROFILE))
                    .load(visualPdf);
            exporter.setTransaction(toMustangInvoice(invoice));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            exporter.export(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new FacturXGenerationException(
                    "Factur-X export failed for invoice " + invoice.invoiceNumber(), e);
        }
    }

    private static org.mustangproject.Invoice toMustangInvoice(Invoice invoice) {
        org.mustangproject.Invoice mustangInvoice = new org.mustangproject.Invoice()
                .setNumber(invoice.invoiceNumber())
                .setIssueDate(toDate(invoice.issueDate()))
                // EN 16931 has no separate delivery date in our model; Factur-X expects
                // an occurrence date, so the issue date is used by convention.
                .setDeliveryDate(toDate(invoice.issueDate()))
                .setCurrency(invoice.currencyCode())
                .setSender(toTradeParty(invoice.seller()))
                .setRecipient(toTradeParty(invoice.buyer()));
        if (invoice.dueDate() != null) {
            mustangInvoice.setDueDate(toDate(invoice.dueDate()));
        }
        for (InvoiceLine line : invoice.lines()) {
            // "C62" is the UN/ECE Recommendation 20 unit code for "piece/unit"
            Product product = new Product(line.description(), "", "C62", line.vatRate());
            mustangInvoice.addItem(new Item(product, line.unitPrice(), line.quantity()));
        }
        return mustangInvoice;
    }

    private static TradeParty toTradeParty(Party party) {
        TradeParty tradeParty = new TradeParty(
                party.name(),
                orEmpty(party.address().street()),
                orEmpty(party.address().postalCode()),
                orEmpty(party.address().city()),
                party.address().countryCode());
        if (party.vatId() != null) {
            tradeParty.addVATID(party.vatId());
        }
        return tradeParty;
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }

    private static Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
