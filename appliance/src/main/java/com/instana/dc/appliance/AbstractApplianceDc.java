/*
 * (c) Copyright IBM Corp. 2023
 * (c) Copyright Instana Inc.
 */
package com.instana.dc.appliance;

import com.instana.dc.AbstractDc;
import com.instana.dc.IDc;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.instana.dc.DcUtil.*;

public abstract class AbstractApplianceDc extends AbstractDc implements IDc {
    private static final Logger logger = Logger.getLogger(AbstractApplianceDc.class.getName());
    
    private final String otelBackendUrl;
    private final boolean otelUsingHttp;
    private final int pollInterval;
    private final int callbackInterval;
    private final String serviceName;
    public final static String INSTRUMENTATION_SCOPE_PREFIX = "otelcol/hostmetricsreceiver/";

    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

    public AbstractApplianceDc(Map<String, String> properties, String applianceSystem) {
        super(new ApplianceRawMetricRegistry().getMap());

        String pollInt = properties.get(POLLING_INTERVAL);
        pollInterval = pollInt == null ? DEFAULT_POLL_INTERVAL : Integer.parseInt(pollInt);
        String callbackInt = properties.get(CALLBACK_INTERVAL);
        callbackInterval = callbackInt == null ? DEFAULT_CALLBACK_INTERVAL : Integer.parseInt(callbackInt);
        otelBackendUrl = properties.get(OTEL_BACKEND_URL);
        otelUsingHttp = "true".equalsIgnoreCase(properties.get(OTEL_BACKEND_USING_HTTP));
        String svcName = properties.get(OTEL_SERVICE_NAME);
        serviceName = svcName == null ? applianceSystem : svcName;
    }

    public String getServiceName() {
        return serviceName;
    }

    @Override
    public void initDC() throws Exception {
        Resource resource = getResourceAttributes();
        SdkMeterProvider sdkMeterProvider = this.getDefaultSdkMeterProvider(resource, otelBackendUrl, callbackInterval, otelUsingHttp, 10);
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder().setMeterProvider(sdkMeterProvider).build();
        initMeters(openTelemetry);
        registerMetrics();
    }

    private void initMeter(OpenTelemetry openTelemetry, String name) {
        Meter meter1 = openTelemetry.meterBuilder(INSTRUMENTATION_SCOPE_PREFIX + name).setInstrumentationVersion("1.0.0").build();
        getMeters().put(name, meter1);
    }

    /* The purpose to overwrite this method is to comply with the "hostmetrics" receiver of
     * OpenTelemetry Contrib Collector.
     **/
    @Override
    public void initMeters(OpenTelemetry openTelemetry) {
        initMeter(openTelemetry, ApplianceDcUtil.MeterName.CPU);
        initMeter(openTelemetry, ApplianceDcUtil.MeterName.MEMORY);
        initMeter(openTelemetry, ApplianceDcUtil.MeterName.NETWORK);
        initMeter(openTelemetry, ApplianceDcUtil.MeterName.LOAD);
        initMeter(openTelemetry, ApplianceDcUtil.MeterName.DISK);
        initMeter(openTelemetry, ApplianceDcUtil.MeterName.FILESYSTEM);
        initMeter(openTelemetry, ApplianceDcUtil.MeterName.PROCESSES);
        initMeter(openTelemetry, ApplianceDcUtil.MeterName.PAGING);
    }


    @Override
    public void start() {
        exec.scheduleWithFixedDelay(this::collectData, 1, pollInterval, TimeUnit.SECONDS);
    }
}