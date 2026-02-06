package org.prebid.server.functional.testcontainers.container

import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import org.wiremock.integrations.testcontainers.WireMockContainer

class NetworkServiceContainer extends WireMockContainer {

    NetworkServiceContainer(String version) {
        super(DockerImageName.parse("wiremock/wiremock:3.3.1"))
        def aliasWithTopLevelDomain = "${getNetworkAliases().first()}.com".toString()
        withCreateContainerCmdModifier { it.withHostName(aliasWithTopLevelDomain) }
        setNetworkAliases([aliasWithTopLevelDomain])
        withCopyFileToContainer(
                MountableFile.forHostPath("/home/administrator/wiremock-docker/extensions/wiremock-grpc-extension-standalone-0.5.0.jar"),
                "/var/wiremock/extensions"
        )
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
