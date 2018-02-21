package org.rtb.vexing.cache.model.response;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public final class BidCacheResponse {

    List<CacheObject> responses;
}
