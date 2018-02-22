package org.prebid.server.cache.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class BidCacheResult {

    String cacheId;

    String cacheUrl;
}
