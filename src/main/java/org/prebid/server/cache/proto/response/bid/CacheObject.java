package org.prebid.server.cache.proto.response.bid;

import lombok.Value;

@Value(staticConstructor = "of")
public class CacheObject {

    String uuid;
}
