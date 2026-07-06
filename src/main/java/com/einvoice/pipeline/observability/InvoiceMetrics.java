package com.einvoice.pipeline.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Business metrics for the invoice pipeline, expressed only against the
 * Micrometer abstraction — no Prometheus type appears here. Which backend
 * scrapes these meters (Prometheus, and through it Grafana) is an
 * infrastructure concern decided by the registry on the classpath, not by
 * the business code.
 *
 * <p>Exposed meters:</p>
 * <ul>
 *   <li>{@code einvoice.invoices.generated} — counter of successful generations</li>
 *   <li>{@code einvoice.invoices.rejected} — counter of rejections, tagged by the
 *       violated business rule ({@code reason}) so a dashboard can break down
 *       <em>why</em> invoices fail</li>
 *   <li>{@code einvoice.processing.duration} — timer of end-to-end processing,
 *       giving throughput and average/percentile latency</li>
 * </ul>
 */
@Component
public class InvoiceMetrics {

    private static final String GENERATED = "einvoice.invoices.generated";
    private static final String REJECTED = "einvoice.invoices.rejected";
    private static final String DURATION = "einvoice.processing.duration";

    private final MeterRegistry registry;
    private final Counter generated;
    private final Timer processingTimer;

    public InvoiceMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.generated = Counter.builder(GENERATED)
                .description("Invoices successfully generated as Factur-X")
                .register(registry);
        this.processingTimer = Timer.builder(DURATION)
                .description("End-to-end invoice processing time")
                .publishPercentiles(0.5, 0.95)
                .register(registry);
    }

    public void recordGenerated() {
        generated.increment();
    }

    /**
     * Increments the rejection counter for one violated rule. Called once per
     * distinct rule so a single multi-violation invoice is counted under each
     * reason it failed.
     */
    public void recordRejected(String rule) {
        Counter.builder(REJECTED)
                .description("Invoices rejected, broken down by violated EN 16931 business rule")
                .tag("reason", rule)
                .register(registry)
                .increment();
    }

    public Timer.Sample startSample() {
        return Timer.start(registry);
    }

    public void stopSample(Timer.Sample sample) {
        sample.stop(processingTimer);
    }
}
