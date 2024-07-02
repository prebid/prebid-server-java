package org.prebid.server.cache.model;

import lombok.Value;
import org.prebid.server.cache.proto.request.bid.BidPutObject;

@Value(staticConstructor = "of")
public class CachedCreative {

    BidPutObject payload;

    int size;
}
