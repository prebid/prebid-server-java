package org.prebid.server.functional.model.mock.services.prebidcache.response

import groovy.transform.ToString
import org.prebid.server.functional.model.ResponseModel

@ToString(includeNames = true, ignoreNulls = true)
class PrebidCacheResponse implements ResponseModel {

    List<CacheObject> responses

    static PrebidCacheResponse getDefaultCacheResponse() {
        def response = new PrebidCacheResponse()
        response.addResponse(CacheObject.defaultCacheObject)
        response
    }

    void addResponse(CacheObject cacheResponse) {
        if (this.responses == null) {
            this.responses = []
        }
        this.responses.add(cacheResponse)
    }
}
