package org.prebid.server.functional.testcontainers

import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer
import org.prebid.server.functional.testcontainers.container.PrebidServerContainer
import org.prebid.server.functional.util.SystemProperties

import static org.prebid.server.functional.util.SystemProperties.USE_FIXED_CONTAINER_PORTS

// TODO make container instance into a POGO and add the ability for any given container to live through stopContainers()
class PbsServiceFactory {

    private static final Map<Map<String, String>, PrebidServerContainer> containers = [:]
    private static final int MAX_CONTAINER_COUNT = maxContainerCount

    private final NetworkServiceContainer networkServiceContainer

    PbsServiceFactory(NetworkServiceContainer networkServiceContainer) {
        this.networkServiceContainer = networkServiceContainer
    }

    static PrebidServerService getService(Map<String, String> config) {
        if (containers.containsKey(config)) {
            def container = containers.get(config)
            container.refresh()
            return new PrebidServerService(container)
        } else {
            if (containers.size() == 1 && MAX_CONTAINER_COUNT == 1) {
                def container = containers.entrySet().first()
                remove([(container.key): container.value])
            } else if (containers.size() >= MAX_CONTAINER_COUNT) {
                def container = containers.find { !it.key.isEmpty() }
                remove([(container.key): container.value])
            }
            def pbsContainer = new PrebidServerContainer(config)
            pbsContainer.start()
            containers.put(config, pbsContainer)
            return new PrebidServerService(pbsContainer)
        }
    }

    private static PrebidServerContainer getContainer(Map<String, String> config) {
        containers.get(config)
    }

    static void stopContainers() {
        def containers = containers.findAll { it.key != [:] }
        remove(containers)
    }

    static void removeContainer(Map<String, String> config) {
        def container = containers.get(config)
        container.stop()
        containers.remove(config)
    }

    private static void remove(Map<Map<String, String>, PrebidServerContainer> map) {
        map.each { key, value ->
            value.stop()
            containers.remove(key)
        }
    }

    private static int getMaxContainerCount() {
        USE_FIXED_CONTAINER_PORTS
                ? 1
                : SystemProperties.getPropertyOrDefault("tests.max-container-count", 5)
    }
}
