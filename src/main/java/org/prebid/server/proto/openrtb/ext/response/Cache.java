package org.prebid.server.proto.openrtb.ext.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Builder
@Value
public class Cache {
    CacheInner vastXml;

    CacheInner bids;
}


