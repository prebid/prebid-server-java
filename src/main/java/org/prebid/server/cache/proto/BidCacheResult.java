package org.prebid.server.cache.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class BidCacheResult {

    String cacheId;

    String cacheUrl;
}
