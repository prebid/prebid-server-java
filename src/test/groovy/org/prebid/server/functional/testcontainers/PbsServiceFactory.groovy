package org.prebid.server.functional.testcontainers

import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer
import org.prebid.server.functional.util.ObjectMapperWrapper

class PbsServiceFactory {

    private final ContainerFactory containerFactory
    private final NetworkServiceContainer networkServiceContainer
    private final ObjectMapperWrapper mapper

    PbsServiceFactory(NetworkServiceContainer networkServiceContainer,
                      ContainerFactory containerFactory,
                      ObjectMapperWrapper mapper) {
        this.containerFactory = containerFactory
        this.networkServiceContainer = networkServiceContainer
        this.mapper = mapper
    }

    PrebidServerService getService(Map<String, String> config) {
        containerFactory.acquireContainer(config)
    }
}
