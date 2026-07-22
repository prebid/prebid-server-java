package org.prebid.server.functional.testcontainers.container

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName

class NetworkServiceContainer extends GenericContainer<NetworkServiceContainer> {

    NetworkServiceContainer() {
        super(DockerImageName.parse("wiremock/wiremock:3.3.1"))
        def aliasWithTopLevelDomain = "${getNetworkAliases().first()}.com".toString()
        withCreateContainerCmdModifier { it.withHostName(aliasWithTopLevelDomain) }
        setNetworkAliases([aliasWithTopLevelDomain])
        withCommand("--disable-gzip")
        withExposedPorts(8080)
    }

    String getHostAndPort() {
        "${networkAliases.first()}:${exposedPorts.first()}"
    }

    String getRootUri() {
        "http://${hostAndPort}"
    }

    @Override
    NetworkServiceContainer withNetwork(Network network) {
        return super.withNetwork(network) as NetworkServiceContainer
    }

    @Override
    void close() {
        super.close()
    }
}
