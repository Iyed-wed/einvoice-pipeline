package com.einvoice.pipeline.service;

import com.einvoice.pipeline.model.Invoice;
import com.einvoice.pipeline.model.InvoiceLine;
import com.einvoice.pipeline.model.Party;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.encoding.WinAnsiEncoding;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.xml.XmpSerializer;
import org.springframework.stereotype.Component;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Renders the human-readable visual of an invoice as a PDF/A-1b document.
 *
 * <p>PDF/A-1b conformance matters here: Mustang's {@code ZUGFeRDExporterFromA1}
 * validates its input before converting it to PDF/A-3, so a non-conformant
 * visual would make Factur-X generation fail. The three requirements handled
 * below are: fully embedded fonts, an sRGB output intent, and XMP metadata
 * declaring PDF/A-1 conformance level B.</p>
 *
 * <p>Fonts: the Mustang library already bundles the Source Sans Pro family
 * (SIL OFL licensed) for its own visualizer; we embed those instead of
 * shipping font binaries in this repository.</p>
 */
@Component
public class InvoicePdfRenderer {

    private static final String FONT_REGULAR = "/fonts/SourceSansPro-Regular.ttf";
    private static final String FONT_BOLD = "/fonts/SourceSansPro-Bold.ttf";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final float MARGIN = 50;
    private static final float CONTENT_BOTTOM = 90;

    // Table column X positions (A4 width is 595pt)
    private static final float COL_DESCRIPTION = MARGIN;
    private static final float COL_QUANTITY = 320;
    private static final float COL_UNIT_PRICE = 390;
    private static final float COL_VAT_RATE = 460;
    private static final float COL_NET = 545; // right edge for right-aligned amounts

    public byte[] render(Invoice invoice) {
        try (PDDocument document = new PDDocument()) {
            PDFont regular = loadFont(document, FONT_REGULAR);
            PDFont bold = loadFont(document, FONT_BOLD);

            new PageWriter(document, regular, bold).writeInvoice(invoice);

            addPdfA1bMetadata(document);
            addSrgbOutputIntent(document);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // PDF/A-1 is based on PDF 1.4: cross-reference streams (the PDFBox 3
            // compressed default) are forbidden, so save with a classic xref table.
            document.setVersion(1.4f);
            document.save(out, CompressParameters.NO_COMPRESSION);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render invoice PDF", e);
        }
    }

    private static PDFont loadFont(PDDocument document, String resource) throws IOException {
        InputStream stream = InvoicePdfRenderer.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("Bundled font not found on classpath: " + resource);
        }
        try (stream) {
            return PDTrueTypeFont.load(document, stream, WinAnsiEncoding.INSTANCE);
        }
    }

    /** XMP metadata declaring PDF/A-1 conformance level B — required by ISO 19005-1. */
    private static void addPdfA1bMetadata(PDDocument document) throws IOException {
        try {
            XMPMetadata xmp = XMPMetadata.createXMPMetadata();
            PDFAIdentificationSchema pdfaId = xmp.createAndAddPDFAIdentificationSchema();
            pdfaId.setPart(1);
            pdfaId.setConformance("B");

            ByteArrayOutputStream xmpBytes = new ByteArrayOutputStream();
            new XmpSerializer().serialize(xmp, xmpBytes, true);

            PDMetadata metadata = new PDMetadata(document);
            metadata.importXMPMetadata(xmpBytes.toByteArray());
            document.getDocumentCatalog().setMetadata(metadata);
        } catch (Exception e) {
            throw new IOException("Failed to write PDF/A XMP metadata", e);
        }
    }

    /** Output intent defining the color space — required by PDF/A. The sRGB ICC profile ships with the JDK. */
    private static void addSrgbOutputIntent(PDDocument document) throws IOException {
        byte[] icc = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
        PDOutputIntent intent = new PDOutputIntent(document, new ByteArrayInputStream(icc));
        intent.setInfo("sRGB IEC61966-2.1");
        intent.setOutputCondition("sRGB IEC61966-2.1");
        intent.setOutputConditionIdentifier("sRGB IEC61966-2.1");
        intent.setRegistryName("http://www.color.org");
        document.getDocumentCatalog().addOutputIntent(intent);
    }

    /**
     * Stateful page writer: tracks the cursor position and opens a new page
     * (repeating the table header) when the current one is full.
     */
    private static final class PageWriter {

        private final PDDocument document;
        private final PDFont regular;
        private final PDFont bold;
        private PDPageContentStream content;
        private float y;

        PageWriter(PDDocument document, PDFont regular, PDFont bold) {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
        }

        void writeInvoice(Invoice invoice) throws IOException {
            newPage();

            text(bold, 16, MARGIN, y, "FACTURE / INVOICE " + invoice.invoiceNumber());
            y -= 26;
            text(regular, 10, MARGIN, y, "Issue date: " + format(invoice.issueDate())
                    + (invoice.dueDate() != null ? "    Due date: " + format(invoice.dueDate()) : "")
                    + "    Currency: " + invoice.currencyCode());
            y -= 30;

            float partyTop = y;
            writeParty("Seller / Vendeur", invoice.seller(), MARGIN);
            float sellerBottom = y;
            y = partyTop;
            writeParty("Buyer / Acheteur", invoice.buyer(), 320);
            y = Math.min(sellerBottom, y) - 20;

            writeTableHeader();
            for (InvoiceLine line : invoice.lines()) {
                ensureRoom(16);
                text(regular, 9, COL_DESCRIPTION, y, line.description());
                text(regular, 9, COL_QUANTITY, y, plain(line.quantity()));
                textRight(regular, 9, COL_VAT_RATE - 10, y, money(line.unitPrice()));
                textRight(regular, 9, COL_NET - 45, y, plain(line.vatRate()) + " %");
                textRight(regular, 9, COL_NET, y, money(line.netAmount()));
                y -= 16;
            }

            y -= 10;
            ensureRoom(60);
            String currency = " " + invoice.currencyCode();
            totalLine(regular, "Total excl. VAT / Total HT", money(invoice.totalWithoutVat()) + currency);
            totalLine(regular, "VAT / TVA", money(invoice.totalVat()) + currency);
            totalLine(bold, "Total incl. VAT / Total TTC", money(invoice.totalWithVat()) + currency);

            close();
        }

        private void writeParty(String title, Party party, float x) throws IOException {
            text(bold, 10, x, y, title);
            y -= 14;
            text(regular, 10, x, y, party.name());
            y -= 12;
            if (party.address().street() != null) {
                text(regular, 10, x, y, party.address().street());
                y -= 12;
            }
            String cityLine = ((party.address().postalCode() != null ? party.address().postalCode() + " " : "")
                    + (party.address().city() != null ? party.address().city() : "")).trim();
            if (!cityLine.isEmpty()) {
                text(regular, 10, x, y, cityLine);
                y -= 12;
            }
            text(regular, 10, x, y, party.address().countryCode());
            y -= 12;
            if (party.vatId() != null) {
                text(regular, 10, x, y, "VAT: " + party.vatId());
                y -= 12;
            }
        }

        private void writeTableHeader() throws IOException {
            text(bold, 9, COL_DESCRIPTION, y, "Description");
            text(bold, 9, COL_QUANTITY, y, "Qty");
            textRight(bold, 9, COL_VAT_RATE - 10, y, "Unit price");
            textRight(bold, 9, COL_NET - 45, y, "VAT");
            textRight(bold, 9, COL_NET, y, "Net");
            y -= 6;
            content.moveTo(MARGIN, y);
            content.lineTo(COL_NET, y);
            content.stroke();
            y -= 14;
        }

        private void totalLine(PDFont font, String label, String value) throws IOException {
            textRight(font, 10, COL_NET - 120, y, label);
            textRight(font, 10, COL_NET, y, value);
            y -= 16;
        }

        private void ensureRoom(float needed) throws IOException {
            if (y - needed < CONTENT_BOTTOM) {
                close();
                newPage();
                writeTableHeader();
            }
        }

        private void newPage() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void close() throws IOException {
            if (content != null) {
                content.close();
                content = null;
            }
        }

        private void text(PDFont font, float size, float x, float atY, String value) throws IOException {
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, atY);
            content.showText(encodable(font, value));
            content.endText();
        }

        private void textRight(PDFont font, float size, float rightX, float atY, String value) throws IOException {
            String safe = encodable(font, value);
            float width = font.getStringWidth(safe) / 1000 * size;
            text(font, size, rightX - width, atY, safe);
        }

        /** Replaces characters the WinAnsi-encoded font cannot represent, so rendering never fails on exotic input. */
        private static String encodable(PDFont font, String value) {
            StringBuilder result = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                try {
                    font.encode(String.valueOf(c));
                    result.append(c);
                } catch (IOException | IllegalArgumentException e) {
                    result.append('?');
                }
            }
            return result.toString();
        }

        private static String format(LocalDate date) {
            return DATE.format(date);
        }

        private static String money(BigDecimal amount) {
            return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }

        private static String plain(BigDecimal amount) {
            return amount.stripTrailingZeros().toPlainString();
        }
    }
}
