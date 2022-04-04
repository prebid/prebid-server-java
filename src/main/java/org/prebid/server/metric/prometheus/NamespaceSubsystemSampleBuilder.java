package org.prebid.server.metric.prometheus;

import io.prometheus.client.Collector;
import io.prometheus.client.dropwizard.samplebuilder.SampleBuilder;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;

public class NamespaceSubsystemSampleBuilder implements SampleBuilder {

    private final SampleBuilder delegate;
    private final String prefix;

    public NamespaceSubsystemSampleBuilder(SampleBuilder sampleBuilder, String namespace, String subsystem) {
        delegate = Objects.requireNonNull(sampleBuilder);
        prefix = toPrefix(namespace) + toPrefix(subsystem);
    }

    @Override
    public Collector.MetricFamilySamples.Sample createSample(String dropwizardName,
                                                             String nameSuffix,
                                                             List<String> additionalLabelNames,
                                                             List<String> additionalLabelValues,
                                                             double value) {

        return delegate.createSample(
                prefix + dropwizardName,
                nameSuffix,
                additionalLabelNames,
                additionalLabelValues,
                value);
    }

    private static String toPrefix(String value) {
        return StringUtils.isNotEmpty(value) ? value + "_" : "";
    }
}
