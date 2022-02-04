package org.prebid.server.functional.model.mock.services.prebidcache.response

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = false)
class CacheObject {

    String uuid

    static CacheObject getDefaultCacheObject() {
        new CacheObject(uuid: UUID.randomUUID())
    }
}
