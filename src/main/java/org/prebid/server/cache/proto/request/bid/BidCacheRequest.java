package org.prebid.server.cache.proto.request.bid;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class BidCacheRequest {

    List<BidPutObject> puts;
}
