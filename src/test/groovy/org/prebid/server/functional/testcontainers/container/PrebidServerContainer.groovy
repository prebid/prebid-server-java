package org.prebid.server.functional.testcontainers.container

import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.testcontainers.PbsConfig
import org.prebid.server.functional.util.SystemProperties
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable

import static org.prebid.server.functional.model.config.Endpoint.STATUS
import static org.prebid.server.functional.testcontainers.PbsConfig.DEFAULT_ENV

class PrebidServerContainer extends GenericContainer<PrebidServerContainer> {

    static final String ADMIN_ENDPOINT_USERNAME = "admin"
    static final String ADMIN_ENDPOINT_PASSWORD = "admin"
    static final String APP_WORKDIR = "/app/prebid-server/"
    static final int PROMETHEUS_PORT = 8070

    private static final int PORT = SystemProperties.getPropertyOrDefault("port", 8080)
    private static final int ADMIN_PORT = SystemProperties.getPropertyOrDefault("admin.port", 8060)
    private static final int DEBUG_PORT = SystemProperties.getPropertyOrDefault("debug.port", 8000)

    private static final String PBS_DOCKER_IMAGE_NAME = "prebid/prebid-server:latest"

    private final boolean debugEnabled

    PrebidServerContainer(PbsContainerConfig config, boolean debugEnabled = false) {
        super(PBS_DOCKER_IMAGE_NAME)
        this.debugEnabled = debugEnabled
        def ports = [PORT, ADMIN_PORT, PROMETHEUS_PORT]
        if (debugEnabled) {
            ports << DEBUG_PORT
        }
        withExposedPorts(*ports)
        withFixedPorts()
        withStartupAttempts(3)
        waitingFor(Wait.forHttp(STATUS.value)
                .forPort(PORT)
                .forStatusCode(200))
        withNetwork(Dependencies.network)
        def commonConfig = [:] << DEFAULT_ENV
                << PbsConfig.defaultBiddersConfig
                << PbsConfig.metricConfig
                << PbsConfig.adminEndpointConfig
                << PbsConfig.bidderConfig
                << PbsConfig.bidderAliasConfig
                << PbsConfig.prebidCacheConfig
                << PbsConfig.mySqlConfig
                << PbsConfig.targetingConfig
        withConfig(commonConfig)
        withConfig(config.extraProperties)

        config.additionalFiles.each { path, file ->
            withCopyToContainer(file, path)
        }

        if (debugEnabled) {
            withDebug()
        }
    }

    private void withFixedPorts() {
        if (!SystemProperties.USE_FIXED_CONTAINER_PORTS) {
            return
        }
        addFixedExposedPort(PORT, PORT)
        addFixedExposedPort(ADMIN_PORT, ADMIN_PORT)
        if (debugEnabled) {
            addFixedExposedPort(DEBUG_PORT, DEBUG_PORT)
        }
    }

    private void withDebug() {
        def options = [
                envMap.JAVA_TOOL_OPTIONS,
                "-agentlib:jdwp=transport=dt_socket,address=*:$DEBUG_PORT,server=y,suspend=n"
        ].findAll().join(' ')
        withEnv("JAVA_TOOL_OPTIONS", options)
    }

    void withConfig(Map<String, String> config) {
        withEnv(config.collectEntries { key, value ->
            [(normalizeProperty(key)): value]
        })
    }

    private static String normalizeProperty(String property) {
        property.replace(".", "_")
                .replace("[", "_")
                .replace("]", "_")
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

    String getDebugAddress() {
        debugEnabled ? "$host:${getMappedPort(DEBUG_PORT)}" : null
    }

    String getInfluxUri() {
        "http://$host:${Dependencies.influxdbContainer.firstMappedPort}"
    }

    String getRootUri() {
        "http://$host:$port"
    }

    String getAdminRootUri() {
        "http://$host:$adminPort"
    }

    String getPrometheusRootUri() {
        "http://$host:$prometheusPort"
    }

    PrebidServerContainer withFolder(String containerPath) {
        withCopyToContainer(
                Transferable.of(new byte[0], 010755),
                "$containerPath/.keep"
        )
        return this
    }

    void refresh() {
        if (!running) {
            stop()
            start()
        }
    }
}
