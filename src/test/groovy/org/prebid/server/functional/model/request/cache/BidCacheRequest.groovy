package org.prebid.server.functional.model.request.cache

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class BidCacheRequest {

    List<BidCachePut> puts
}
