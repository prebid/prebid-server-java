package org.prebid.server.functional.testcontainers.container

import org.testcontainers.containers.MockServerContainer
import org.testcontainers.containers.Network

class NetworkServiceContainer extends MockServerContainer {

    NetworkServiceContainer(String version) {
        super(version)
    }

    String getHostAndPort() {
        "${networkAliases.first()}:${exposedPorts.first()}"
    }

    String getRootUri() {
        "http://${getHostAndPort()}"
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
