package org.prebid.server.functional.tests.prometheus

import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.testcontainers.container.PrebidServerContainer
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.prometheus.PrometheusLabelsConfigHelper
import org.testcontainers.containers.wait.strategy.Wait
import spock.lang.Shared

import static org.prebid.server.functional.testcontainers.container.PrebidServerContainer.PROMETHEUS_PORT

class PrometheusSpec extends BaseSpec {

    private static final String PROMETHEUS_METRIC_NAME_REGEX = "[a-zA-Z_]?[a-zA-Z0-9_]*"

    @Shared
    PrometheusLabelsConfigHelper prometheusLabelsConfigHelper = new PrometheusLabelsConfigHelper()

    def "PBS should add custom labels to Prometheus metrics when custom labels are enabled in config"() {
        given: "PBS config with set up Prometheus and enabled Promethues custom labels"
        def prometheusPbsService = pbsServiceFactory.getService(basePrometheusConfig +
                ["metrics.prometheus.custom-labels-enabled": "true"])

        and: "Labels config metric matcher and an appropriate PBS metric to it are specified"
        def pbsMetricMatcher = "requests.*.*"
        def matchedPbsMetric = "requests.ok.openrtb2-web"

        and: "PBS auction request is made to capture 'requests.ok.openrtb2-web' metric"
        prometheusPbsService.sendAuctionRequest(BidRequest.defaultBidRequest)

        when: "Requesting Prometheus metrics from PBS"
        def prometheusMetrics = prometheusPbsService.sendPrometheusMetricsRequest()

        then: "Prometheus metrics response contains matched PBS metric"
        assert prometheusMetrics.contains("(metric=$matchedPbsMetric")

        and: "Prometheus metrics response contains label config mapper name"
        def normalizedMapperName = normalizeMetricName(prometheusLabelsConfigHelper.getResolvedMapperName(pbsMetricMatcher, matchedPbsMetric))
        assert prometheusMetrics.contains("# TYPE $normalizedMapperName")

        and: "Prometheus metrics response contains config mapper labels"
        def metricLabels = prometheusLabelsConfigHelper.getMetricLabelsString(pbsMetricMatcher, matchedPbsMetric)
        def expectedLabelsString = "$normalizedMapperName$metricLabels"

        assert prometheusMetrics.contains(expectedLabelsString)
    }

    def "PBS should add namespace, subsystem and custom labels info to Prometheus metrics when those are set in config"() {
        given: "PBS config with set up Prometheus with enabled custom labels"
        def namespace = "namespace_01"
        def subsystem = "subsystem_01"
        def prometheusPbsService = pbsServiceFactory.getService(basePrometheusConfig +
                getNamespaceSubsystemConfig(namespace, subsystem) +
                ["metrics.prometheus.custom-labels-enabled": "true"])

        and: "Labels config metric matcher and an appropriate PBS metric to it are specified"
        def pbsMetricMatcher = "requests.*.*"
        def matchedPbsMetric = "requests.ok.openrtb2-web"

        and: "PBS auction request is made to capture 'requests.ok.openrtb2-web' metric"
        prometheusPbsService.sendAuctionRequest(BidRequest.defaultBidRequest)

        when: "Requesting Prometheus metrics from PBS"
        def prometheusMetrics = prometheusPbsService.sendPrometheusMetricsRequest()

        then: "Prometheus metrics response contains matched PBS metric"
        assert prometheusMetrics.contains("(metric=$matchedPbsMetric")

        and: "Prometheus metrics response contains label config mapper name"
        def normalizedMapperName = normalizeMetricName(prometheusLabelsConfigHelper.getResolvedMapperName(pbsMetricMatcher, matchedPbsMetric))
        def namespaceSubsystemMapperName = "${namespace}_${subsystem}_$normalizedMapperName"
        assert prometheusMetrics.contains("# TYPE $namespaceSubsystemMapperName")

        and: "Prometheus metrics response contains config mapper labels"
        def metricLabels = prometheusLabelsConfigHelper.getMetricLabelsString(pbsMetricMatcher, matchedPbsMetric)
        def expectedLabelsString = "$namespaceSubsystemMapperName$metricLabels"

        assert prometheusMetrics.contains(expectedLabelsString)
    }

    def "PBS should add namespace and subsystem parts to Prometheus metric names when those are config provided"() {
        given: "PBS config with set up Prometheus"
        def namespace = "namespace_01_Rubicon"
        def subsystem = "subsystem_01_Rubicon"
        def config = basePrometheusConfig + getNamespaceSubsystemConfig(namespace, subsystem)

        and: "PBS is started"
        def prometheusPbsService = pbsServiceFactory.getService(config)

        and: "PBS auction request is made to capture more metrics"
        prometheusPbsService.sendAuctionRequest(BidRequest.getDefaultBidRequest())

        and: "PBS metrics are collected and transformed to Prometheus format"
        def pbsMetrics = prometheusPbsService.sendCollectedMetricsRequest()
        def normalizedPbsMetricNames = pbsMetrics.collect { normalizeMetricName(it.key) }
        def prometheusFormatPbsMetricNames = normalizedPbsMetricNames.collect { "${namespace}_${subsystem}_$it" }

        when: "Requesting Prometheus metrics from PBS"
        def prometheusMetrics = prometheusPbsService.sendPrometheusMetricsRequest()

        then: "Prometheus metrics response contains each of PBS metric with added namespace and subsystem"
        prometheusFormatPbsMetricNames.each { assert prometheusMetrics.contains(it) }
    }

    def "PBS service fails to start when invalid symbols by namespace and subsystem in Prometheus config are present"() {
        given: "PBS config with invalid symbols in namespace, subsystem Prometheus config"
        def namespace = "namespace_01_Rubi%con"
        def subsystem = "sub@system_01_Rubicon"
        def config = basePrometheusConfig + getNamespaceSubsystemConfig(namespace, subsystem)

        and: "PBS container is prepared"
        def pbsContainer = new PrebidServerContainer(config)
        pbsContainer.setWaitStrategy(Wait.defaultWaitStrategy())

        when: "PBS is started"
        pbsContainer.start()

        then: "PBS is failed to start"
        def serviceFailedToStartTimeoutMs = 10_000
        PBSUtils.waitUntil({ pbsContainer.logs.contains("Invalid prefix: ${namespace}_${subsystem}_, namespace and subsystem should match regex: $PROMETHEUS_METRIC_NAME_REGEX") },
                serviceFailedToStartTimeoutMs)
    }

    private Map<String, String> getBasePrometheusConfig() {
        ["metrics.prometheus.enabled": "true",
         "metrics.prometheus.port"   : PROMETHEUS_PORT as String]
    }

    private Map<String, String> getNamespaceSubsystemConfig(String namespace, String subsystem) {
        ["metrics.prometheus.namespace": namespace,
         "metrics.prometheus.subsystem": subsystem]
    }

    private String normalizeMetricName(String metricName) {
        metricName.replace(".", "_")
                  .replace("-", "_")
    }
}
