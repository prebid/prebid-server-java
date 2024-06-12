package org.prebid.server.functional.model.request.cache

import groovy.transform.ToString
import org.prebid.server.functional.model.mock.services.prebidcache.request.Type
import org.prebid.server.functional.util.ObjectMapperWrapper

@ToString(includeNames = true, ignoreNulls = true)
class BidCachePut implements ObjectMapperWrapper {

    Type type
    CacheBid value
    Integer ttlseconds
    String bidid
    String bidder
    Long timestamp
    String aid
}
