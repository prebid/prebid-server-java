package org.prebid.server.functional.testcontainers.container

import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.testcontainers.PbsConfig
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

import static org.prebid.server.functional.testcontainers.PbsConfig.DEFAULT_ENV

class PrebidServerContainer extends GenericContainer<PrebidServerContainer> {

    public static final int PORT = 8080
    public static final int DEBUG_PORT = 8000
    public static final int ADMIN_PORT = 8060
    public static final int PROMETHEUS_PORT = 8070
    public static final String ADMIN_ENDPOINT_USERNAME = "admin"
    public static final String ADMIN_ENDPOINT_PASSWORD = "admin"
    public static final String APP_WORKDIR = "/app/prebid-server/"

    private static final String PBS_DOCKER_IMAGE_NAME = "prebid/prebid-server:latest"

    PrebidServerContainer(Map<String, String> customConfig) {
        super(PBS_DOCKER_IMAGE_NAME)
        withExposedPorts(PORT, DEBUG_PORT, ADMIN_PORT, PROMETHEUS_PORT)
        withDebug()
        withNetwork(Dependencies.network)
        def commonConfig = [:] << DEFAULT_ENV
                << PbsConfig.defaultBiddersConfig
                << PbsConfig.metricConfig
                << PbsConfig.adminEndpointConfig
                << PbsConfig.bidderConfig
                << PbsConfig.prebidCacheConfig
                << PbsConfig.mySqlConfig
        withConfig(commonConfig)
        withConfig(customConfig)
    }

    void withServiceStartWaiter() {
        waitingFor(Wait.forHttp("/status")
                       .forPort(PORT)
                       .forStatusCode(200))
    }

    void withDebug() {
        withEnv("JAVA_TOOL_OPTIONS", "-agentlib:jdwp=transport=dt_socket,address=*:$DEBUG_PORT,server=y,suspend=n")
    }

    void withConfig(Map<String, String> config) {
        withEnv(normalizeProperties(config))
    }

    int getPort() {
        getMappedPort(PORT)
    }

    int getAdminPort() {
        getMappedPort(ADMIN_PORT)
    }

    int getPrometheusPort() {
        getMappedPort(PROMETHEUS_PORT)
    }

    String getRootUri() {
        return "http://$host:$port"
    }

    String getAdminRootUri() {
        return "http://$host:$adminPort"
    }

    String getPrometheusRootUri() {
        return "http://$host:$prometheusPort"
    }

    private static Map<String, String> normalizeProperties(Map<String, String> properties) {
        properties.collectEntries { [normalizeProperty(it.key), it.value] } as Map<String, String>
    }

    private static String normalizeProperty(String property) {
        property.replace(".", "_")
                .replace("-", "")
                .replace("[", "_")
                .replace("]", "_")
    }

    @Override
    void close() {
        super.close()
    }
}
