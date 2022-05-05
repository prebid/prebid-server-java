package org.prebid.server.functional.testcontainers

import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer
import org.prebid.server.functional.testcontainers.container.PrebidServerContainer
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.prebid.server.functional.util.SystemProperties

// TODO make container instance into a POGO and add the ability for any given container to live through stopContainers()
class PbsServiceFactory {

    private static final Map<Map<String, String>, PrebidServerContainer> containers = [:]
    private static final int MAX_CONTAINER_COUNT = SystemProperties.getPropertyOrDefault("tests.max-container-count", 2)

    private final ObjectMapperWrapper mapper
    private final NetworkServiceContainer networkServiceContainer

    PbsServiceFactory(NetworkServiceContainer networkServiceContainer, ObjectMapperWrapper mapper) {
        this.networkServiceContainer = networkServiceContainer
        this.mapper = mapper
    }

    PrebidServerService getService(Map<String, String> config) {
        if (containers.containsKey(config)) {
            return new PrebidServerService(containers.get(config), mapper)
        } else {
            if (containers.size() >= MAX_CONTAINER_COUNT) {
                def container = containers.find { !it.key.isEmpty() }
                remove([(container.key): container.value])
            }
            def pbsContainer = new PrebidServerContainer(config)
            pbsContainer.start()
            containers.put(config, pbsContainer)
            return new PrebidServerService(pbsContainer, mapper)
        }
    }

    private static PrebidServerContainer getContainer(Map<String, String> config) {
        containers.get(config)
    }

    static void stopContainers() {
        def containers = containers.findAll { it.key != [:] }
        remove(containers)
    }

    private static void remove(Map<Map<String, String>, PrebidServerContainer> map) {
        map.each { key, value ->
            value.stop()
            containers.remove(key)
        }
    }
}
