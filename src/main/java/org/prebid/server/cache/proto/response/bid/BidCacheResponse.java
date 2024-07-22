package org.prebid.server.cache.proto.response.bid;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class BidCacheResponse {

    List<CacheObject> responses;
}
