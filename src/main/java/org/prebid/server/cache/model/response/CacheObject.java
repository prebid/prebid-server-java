package org.prebid.server.cache.model.response;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class CacheObject {

    String uuid;
}
