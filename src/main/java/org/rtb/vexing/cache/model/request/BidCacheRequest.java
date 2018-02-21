package org.rtb.vexing.cache.model.request;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public final class BidCacheRequest {

    List<PutObject> puts;
}
