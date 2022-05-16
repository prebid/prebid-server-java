package org.prebid.server.metric.prometheus;

import io.prometheus.client.Collector;
import io.prometheus.client.dropwizard.samplebuilder.SampleBuilder;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class NamespaceSubsystemSampleBuilder implements SampleBuilder {

    private static final String VALID_PREFIX_REGEX = "[a-zA-Z_:]?[a-zA-Z0-9_:]*";

    private final SampleBuilder delegate;
    private final String prefix;

    public NamespaceSubsystemSampleBuilder(SampleBuilder sampleBuilder, String namespace, String subsystem) {
        delegate = Objects.requireNonNull(sampleBuilder);
        prefix = toPrefix(namespace) + toPrefix(subsystem);

        final Pattern prefixPattern = Pattern.compile(VALID_PREFIX_REGEX);
        if (!prefixPattern.matcher(prefix).matches()) {
            throw new IllegalArgumentException(String.format(
                    "Invalid prefix: %s, namespace and subsystem should match regex: %s", prefix, VALID_PREFIX_REGEX));
        }
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
