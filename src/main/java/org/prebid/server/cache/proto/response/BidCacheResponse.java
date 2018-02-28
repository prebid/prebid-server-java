package org.prebid.server.cache.proto.response;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class BidCacheResponse {

    List<CacheObject> responses;
}
