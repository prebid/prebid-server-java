package org.prebid.server.functional.util.prometheus

import groovy.yaml.YamlSlurper

class PrometheusLabelsConfigHelper {

    private static final String PROMETHEUS_LABELS_RESOURCE_FILE_PATH = "/metrics-config/prometheus-labels.yaml"
    private static final String METRIC_NAME_DELIMITER_REGEX = "\\."
    private static final String METRIC_MATCHER_PLACEHOLDER = "*"

    private final YamlSlurper yamlSlurper
    private final Object prometheusLabelsConfig

    PrometheusLabelsConfigHelper() {
        this.yamlSlurper = new YamlSlurper()
        this.prometheusLabelsConfig = yamlSlurper.parse(prometheusLabelsFile)
    }

    Object getMapper(String mapperMatch) {
        Object mapperObject = prometheusLabelsConfig?.mappers?.find { it?.match == mapperMatch }
        if (!mapperObject) {
            throw new IllegalStateException("Mapper with '$mapperMatch' match is missing from parsed Prometheus labels file.\n" +
                    "File content: ${prometheusLabelsConfig.toString()}")
        }
        mapperObject
    }

    /**
     * @param metricMatcher metric mapper.match value (e.g. 'requests.*.*')
     * @param metricName expected PBS metric name to match to pattern (e.g. 'requests.ok.openrtb2-web')
     * @return mapper name with replaced placeholders
     */
    String getResolvedMapperName(String metricMatcher, String metricName) {
        List<String> metricNamePlaceholders = getResolvedMetricNamePlaceholders(metricMatcher, metricName)
        getReplacedPlaceholdersValue(getMapper(metricMatcher)?.name, metricNamePlaceholders)
    }

    /**
     * @param metricMatcher metric mapper.match value (e.g. 'requests.*.*')
     * @param metricName expected PBS metric name to match to pattern (e.g. 'requests.ok.openrtb2-web')
     * @return labels map in a Prometheus format as string (e.g. for ["status": "ok"] returns {status="ok",})
     */
    String getMetricLabelsString(String metricMatcher, String metricName) {
        List<String> metricNamePlaceholders = getResolvedMetricNamePlaceholders(metricMatcher, metricName)
        Map<String, String> metricLabels = getMapper(metricMatcher)?.labels

        new StringBuilder("{").tap { stringBuilder ->
            metricLabels.each { label ->
                def resolvedLabelValue = getReplacedPlaceholdersValue(label.value, metricNamePlaceholders)
                stringBuilder << "${label.key}=\"$resolvedLabelValue\","
            }
            stringBuilder << "}"
        }.toString()
    }

    /**
     * @param value any value from metric config with placeholders (e.g. 'label.${0}.${1}')
     * @param metricNamePlaceholders placeholders from {@link #getResolvedMetricNamePlaceholders(String, String)} (e.g. ['a', 'b'])
     * @return value with replaced ${0-9} placeholders (e.g. 'label.a.b')
     */
    private static String getReplacedPlaceholdersValue(String value, List<String> metricNamePlaceholders) {
        value.replaceAll('\\$\\{\\d}', { metricNamePlaceholders[it[2] as int] })
    }

    /**
     * @param metricMatcher metric mapper.match value (e.g. 'requests.*.*')
     * @param metricName matched metric name (e.g. 'requests.ok.openrtb2-web')
     * @return placeholder list of replaced mapper.match asterisks with values from metric name
     */
    private static List<String> getResolvedMetricNamePlaceholders(String metricMatcher, String metricName) {
        List<String> splitMetricMatcher = metricMatcher.split(METRIC_NAME_DELIMITER_REGEX)
        List<String> splitMetricName = metricName.split(METRIC_NAME_DELIMITER_REGEX)
        if (splitMetricMatcher.size() != splitMetricName.size()) {
            throw new IllegalStateException("Metric name '$metricName' doesn't match to metric matcher '$metricMatcher'")
        }
        splitMetricName[splitMetricMatcher.findIndexValues { it == METRIC_MATCHER_PLACEHOLDER }]
    }

    private InputStream getPrometheusLabelsFile() {
        final InputStream inputStream = getClass().getResourceAsStream(PROMETHEUS_LABELS_RESOURCE_FILE_PATH)
        if (!inputStream) {
            throw new IllegalStateException("$PROMETHEUS_LABELS_RESOURCE_FILE_PATH is missing from resources")
        }
        inputStream
    }
}
