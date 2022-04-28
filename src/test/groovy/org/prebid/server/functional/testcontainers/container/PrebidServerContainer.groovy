package org.prebid.server.functional.testcontainers.container

import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.testcontainers.PbsConfig
import org.prebid.server.functional.util.SystemProperties
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

import static org.prebid.server.functional.testcontainers.PbsConfig.DEFAULT_ENV

class PrebidServerContainer extends GenericContainer<PrebidServerContainer> {

    private static final String DEFAULT_PORT = "8080"
    private static final String DEFAULT_DEBUG_PORT = "8000"
    private static final String DEFAULT_ADMIN_PORT = "8060"
    private static final String ADMIN_ENDPOINT_USERNAME = "admin"
    private static final String ADMIN_ENDPOINT_PASSWORD = "admin"
    private static final String APP_WORKDIR = "/app/prebid-server/"
    private static final int PORT = Integer.parseInt(
            SystemProperties.getPropertyOrDefault("port", DEFAULT_PORT));
    private static final int DEBUG_PORT = Integer.parseInt(
            SystemProperties.getPropertyOrDefault("debug.port", DEFAULT_DEBUG_PORT));
    private static final int ADMIN_PORT = Integer.parseInt(
            SystemProperties.getPropertyOrDefault("admin.port", DEFAULT_ADMIN_PORT));
    private static final boolean FIXED_EXPOSED_PORT = Boolean.parseBoolean(
            SystemProperties.getPropertyOrDefault("fixed.exposed.port", false))

    PrebidServerContainer(Map<String, String> config) {
        this("prebid/prebid-server:latest", config)
    }

    PrebidServerContainer(String dockerImage, Map<String, String> customConfig) {
        super(dockerImage)
        withExposedPorts(PORT, DEBUG_PORT, ADMIN_PORT)
        withExposedPortsDebug()
        waitingFor(Wait.forHttp("/status")
                       .forPort(PORT)
                       .forStatusCode(200))
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

    private void withExposedPortsDebug() {
        if (FIXED_EXPOSED_PORT) {
            addFixedExposedPort(PORT, PORT)
            addFixedExposedPort(DEBUG_PORT, DEBUG_PORT)
            addFixedExposedPort(ADMIN_PORT, ADMIN_PORT)
        }
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

    String getRootUri() {
        return "http://$host:$port"
    }

    String getAdminRootUri() {
        return "http://$host:$adminPort"
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
