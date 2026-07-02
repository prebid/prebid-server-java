package org.prebid.server.functional.testcontainers

import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer
import org.prebid.server.functional.testcontainers.container.PrebidServerContainer
import org.prebid.server.functional.util.SystemProperties
import org.testcontainers.images.builder.Transferable

import static org.prebid.server.functional.testcontainers.PbsServiceFactory.ContainerPolicy.GLOBAL
import static org.prebid.server.functional.testcontainers.PbsServiceFactory.ContainerPolicy.LRU
import static org.prebid.server.functional.util.SystemProperties.USE_FIXED_CONTAINER_PORTS

// TODO make container instance into a POGO and add the ability for any given container to live through stopContainers()
class PbsServiceFactory {

    private static final Map<Map<String, String>, PrebidServerContainer> globalContainers = [:]
    private static final LinkedHashMap<Map<String, String>, PrebidServerContainer> containers = new LinkedHashMap<>(16, 0.75f, true)
    private static final int MAX_CONTAINER_COUNT = maxContainerCount
    private static int created
    private static int reused
    private static int evicted
    private static final Map<Object, Integer> hits = [:].withDefault { 0 }
    private static final Map<String, Integer> keyFrequency = [:].withDefault { 0 }


    private final NetworkServiceContainer networkServiceContainer

    PbsServiceFactory(NetworkServiceContainer networkServiceContainer) {
        this.networkServiceContainer = networkServiceContainer
    }

    static {
        Runtime.runtime.addShutdownHook(new Thread({
            println """
            PBS container stats
            +++++
            created   = $created
            reused    = $reused
            evicted   = $evicted
            cacheSize = ${containers.size()}
            top containers = ${hits.sort { -it.value }.take(20)}
            key frequency = ${keyFrequency.sort { -it.value }}
            +++++
            """
        }))
    }

    static PrebidServerService getService(Map<String, String> config,
                                          Map<String, Transferable> additionalFiles = [:],
                                          ContainerPolicy policy = LRU) {
        hits[config] = (hits[config] ?: 0) + 1
        def existingContainer = containers.get(config) ?: globalContainers.get(config)
        if (existingContainer) {
            reused++
            existingContainer.refresh()
            return new PrebidServerService(existingContainer)
        }
        created++
        evictIfNeeded()
        def newContainer = new PrebidServerContainer(config)
        additionalFiles.each { k, v ->
            newContainer.withCopyToContainer(v, k)
        }
        newContainer.start()
        if (policy == GLOBAL) {
            if (globalContainers.size() < MAX_CONTAINER_COUNT) {
                globalContainers.put(config, newContainer)
            } else {
                throw new IllegalStateException("Number of global containers is overloaded")
            }
        } else {
            containers.put(config, newContainer)
        }
        config.keySet().each { key ->
            keyFrequency[key] = (keyFrequency[key] ?: 0) + 1
        }
        return new PrebidServerService(newContainer)
    }

    private static void evictIfNeeded() {
        while ((containers.size() + globalContainers.size() >= MAX_CONTAINER_COUNT) && !containers.isEmpty()) {
            def eldest = containers.entrySet().iterator().next()
            eldest.value.stop()
            containers.remove(eldest.key)
            evicted ++
        }
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

    enum ContainerPolicy {
        GLOBAL,
        LRU
    }
}
