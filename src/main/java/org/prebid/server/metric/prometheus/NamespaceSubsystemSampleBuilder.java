package org.prebid.server.metric.prometheus;

import io.prometheus.client.Collector;
import io.prometheus.client.dropwizard.samplebuilder.CustomMappingSampleBuilder;
import io.prometheus.client.dropwizard.samplebuilder.DefaultSampleBuilder;
import io.prometheus.client.dropwizard.samplebuilder.MapperConfig;
import io.prometheus.client.dropwizard.samplebuilder.SampleBuilder;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

public class NamespaceSubsystemSampleBuilder implements SampleBuilder {

    private static final String VALID_PREFIX_REGEX = "[a-zA-Z_]?[a-zA-Z0-9_]*";

    private final SampleBuilder delegate;
    private final String prefix;

    public NamespaceSubsystemSampleBuilder(String namespace, String subsystem, List<MapperConfig> mapperConfigs) {
        prefix = toPrefix(namespace) + toPrefix(subsystem);

        final Pattern prefixPattern = Pattern.compile(VALID_PREFIX_REGEX);
        if (!prefixPattern.matcher(prefix).matches()) {
            throw new IllegalArgumentException("Invalid prefix: %s, namespace and subsystem should match regex: %s"
                    .formatted(prefix, VALID_PREFIX_REGEX));
        }

        delegate = mapperConfigs.isEmpty()
                ? new DefaultSampleBuilder()
                : new CustomMappingSampleBuilder(enrichWithPrefix(mapperConfigs, prefix));
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

    private static List<MapperConfig> enrichWithPrefix(List<MapperConfig> mapperConfigs, String prefix) {
        return mapperConfigs.stream()
                .map(mapperConfig -> new MapperConfig(
                        prefix + mapperConfig.getMatch(),
                        prefix + mapperConfig.getName(),
                        mapperConfig.getLabels()))
                .toList();
    }
}
