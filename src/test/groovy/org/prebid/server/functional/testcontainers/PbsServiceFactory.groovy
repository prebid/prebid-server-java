package org.prebid.server.functional.testcontainers

import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer
import org.prebid.server.functional.testcontainers.container.PrebidServerContainer
import org.prebid.server.functional.util.ObjectMapperWrapper

class PbsServiceFactory {

    private static final Map<Map<String, String>, PrebidServerContainer> containers = [:]
    private static final int MAX_CONTAINERS_COUNT = 5

    private final ObjectMapperWrapper mapper
    private final NetworkServiceContainer networkServiceContainer

    PbsServiceFactory(NetworkServiceContainer networkServiceContainer, ObjectMapperWrapper mapper) {
        this.networkServiceContainer = networkServiceContainer
        this.mapper = mapper
    }

    PrebidServerService getService(Map<String, String> config) {
        if (containers.size() >= MAX_CONTAINERS_COUNT) {
            def container = containers.find { !it.key.isEmpty() }
            remove([(container.key): container.value])
        }
        if (containers.containsKey(config)) {
            return new PrebidServerService(containers.get(config), mapper)
        } else {
            def pbsContainer = new PrebidServerContainer(config)
            pbsContainer.start()
            containers.put(config, pbsContainer)
            return new PrebidServerService(pbsContainer, mapper)
        }
    }

    static void stopContainers() {
        def containers = containers.findAll { it.key != [:] }
        remove(containers)
    }

    Map<String, String> pubstackAnalyticsConfig(String scopeId) {
        ["analytics.pubstack.enabled"                       : "true",
         "analytics.pubstack.endpoint"                      : networkServiceContainer.rootUri,
         "analytics.pubstack.scopeid"                       : scopeId,
         "analytics.pubstack.configuration-refresh-delay-ms": "1000",
         "analytics.pubstack.buffers.size-bytes"            : "1",
         "analytics.pubstack.timeout-ms"                    : "100"]
    }

    Map<String, String> httpSettings() {
        ["settings.http.endpoint"      : "$networkServiceContainer.rootUri/stored-requests".toString(),
         "settings.http.amp-endpoint"  : "$networkServiceContainer.rootUri/amp-stored-requests".toString(),
         "settings.http.video-endpoint": "$networkServiceContainer.rootUri/video-stored-requests".toString()]
    }

    private static void remove(Map<Map<String, String>, PrebidServerContainer> map) {
        map.each { key, value ->
            value.stop()
            containers.remove(key)
        }
    }
}


