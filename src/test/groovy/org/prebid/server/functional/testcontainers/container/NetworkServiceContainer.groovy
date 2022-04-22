package org.prebid.server.functional.testcontainers.container

import org.testcontainers.containers.MockServerContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName

class NetworkServiceContainer extends MockServerContainer {

    NetworkServiceContainer(String version) {
        super(DockerImageName.parse("mockserver/mockserver:mockserver-$version"))
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
