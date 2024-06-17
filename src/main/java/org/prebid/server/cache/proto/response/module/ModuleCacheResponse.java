package org.prebid.server.cache.proto.response.module;

import lombok.Value;
import org.prebid.server.cache.proto.request.module.ModuleCacheType;

@Value(staticConstructor = "of")
public class ModuleCacheResponse {

    String key;

    ModuleCacheType type;

    String value;
}
