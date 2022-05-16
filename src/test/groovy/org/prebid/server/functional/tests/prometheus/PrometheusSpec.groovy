package org.prebid.server.functional.tests.prometheus

import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.testcontainers.container.PrebidServerContainer
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils
import org.testcontainers.containers.wait.strategy.Wait

import static org.prebid.server.functional.testcontainers.container.PrebidServerContainer.PROMETHEUS_PORT

class PrometheusSpec extends BaseSpec {

    private static final String PROMETHEUS_METRIC_NAME_REGEX = "[a-zA-Z_:]?[a-zA-Z0-9_:]*"

    def "PBS should add namespace and subsystem parts to Prometheus metric names when those are config provided"() {
        given: "PBS config with set up Prometheus"
        def namespace = "namespace_01:Rubicon"
        def subsystem = "subsystem_01:Rubicon"
        def config = ["metrics.prometheus.enabled"  : "true",
                      "metrics.prometheus.port"     : PROMETHEUS_PORT as String,
                      "metrics.prometheus.namespace": namespace,
                      "metrics.prometheus.subsystem": subsystem]

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
        def namespace = "namespace_01:Rubi%con"
        def subsystem = "sub@system_01:Rubicon"
        def config = ["metrics.prometheus.enabled"  : "true",
                      "metrics.prometheus.port"     : PROMETHEUS_PORT as String,
                      "metrics.prometheus.namespace": namespace,
                      "metrics.prometheus.subsystem": subsystem]

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

    private String normalizeMetricName(String metricName) {
        metricName.replace(".", "_")
                  .replace("-", "_")
    }
}
