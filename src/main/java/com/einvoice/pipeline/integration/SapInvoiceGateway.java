package com.einvoice.pipeline.integration;

import com.einvoice.pipeline.model.Address;
import com.einvoice.pipeline.model.Invoice;
import com.einvoice.pipeline.model.InvoiceAmounts;
import com.einvoice.pipeline.model.InvoiceLine;
import com.einvoice.pipeline.model.Party;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SAP entry point — the "we already run SAP" case. This POC does <strong>not</strong>
 * open a real SAP connection; it documents the integration approach and provides
 * the mapping from an SAP billing document to the canonical {@link Invoice}, so
 * the SAP path feeds the very same validation/generation engine as the JSON and
 * CSV paths.
 *
 * <h2>How a production integration would fetch the data</h2>
 *
 * <p><strong>Option A — OData (S/4HANA, preferred).</strong> Consume the standard
 * Billing Document service {@code API_BILLING_DOCUMENT_SRV} exposed by the SAP
 * Gateway:</p>
 * <pre>
 *   GET /sap/opu/odata/sap/API_BILLING_DOCUMENT_SRV/A_BillingDocument('90001234')
 *       ?$expand=to_Item,to_Partner
 *   Authorization: (OAuth2 / principal propagation via SAP BTP Destination service)
 * </pre>
 * <p>A {@code RestClient}/{@code WebClient} call would deserialize the response
 * into {@link SapBillingDocument}. This is the modern, firewall-friendly path
 * (HTTPS, JSON/XML) and the one to prefer for S/4HANA and BTP landscapes.</p>
 *
 * <p><strong>Option B — BAPI/RFC (classic ECC).</strong> For older ECC systems
 * without the OData service, call the RFC-enabled function module
 * {@code BAPI_BILLINGDOC_GETDETAIL} (or {@code BAPI_BILLINGDOC_GETLIST} to
 * discover documents) through the SAP Java Connector (JCo):</p>
 * <pre>
 *   JCoDestination dest = JCoDestinationManager.getDestination("ECC_PROD");
 *   JCoFunction fn = dest.getRepository().getFunction("BAPI_BILLINGDOC_GETDETAIL");
 *   fn.getImportParameterList().setValue("BILLINGDOCUMENT", "0090001234");
 *   fn.execute(dest);
 *   // read header + item tables from fn.getTableParameterList()
 * </pre>
 * <p>JCo requires native libraries and licensed SAP connectivity, so it is
 * typically isolated behind this gateway and, in Kubernetes, behind SAP BTP
 * Connectivity / a Cloud Connector.</p>
 *
 * <p>Either way, only this class changes; the mapping below and everything
 * downstream stay identical.</p>
 */
@Component
public class SapInvoiceGateway {

    // "C62" (piece) is the default UN/ECE Rec. 20 unit; a real mapping would
    // translate SAP's unit of measure (VRKME) to the corresponding UN/ECE code.

    /**
     * Maps an SAP billing document to the canonical invoice. In production the
     * {@code document} would come from the OData/RFC call described above; here
     * it is supplied directly so the mapping is unit-testable offline.
     */
    public Invoice toCanonicalInvoice(SapBillingDocument document) {
        List<InvoiceLine> lines = document.items().stream()
                .map(item -> new InvoiceLine(
                        item.material(),
                        item.billedQuantity(),
                        item.netPrice(),
                        item.taxRate()))
                .toList();

        // SAP carries net values per item but not the EN 16931 declared totals,
        // so they are computed here, exactly as for the CSV path.
        InvoiceAmounts totals = InvoiceAmounts.fromLines(lines);

        return new Invoice(
                document.billingDocument(),
                document.billingDocumentDate(),
                null,
                document.transactionCurrency(),
                toParty(document.companyParty()),
                toParty(document.soldToParty()),
                lines,
                totals.totalWithoutVat(),
                totals.totalVat(),
                totals.totalWithVat());
    }

    private static Party toParty(SapBillingDocument.SapParty sapParty) {
        return new Party(
                sapParty.name(),
                sapParty.vatNumber(),
                new Address(sapParty.street(), sapParty.postalCode(), sapParty.city(), sapParty.country()));
    }
}
