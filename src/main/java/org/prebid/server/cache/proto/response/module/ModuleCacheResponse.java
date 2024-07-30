package org.prebid.server.cache.proto.response.module;

import lombok.Value;
import org.prebid.server.cache.proto.request.module.StorageDataType;

@Value(staticConstructor = "of")
public class ModuleCacheResponse {

    String key;

    StorageDataType type;

    String value;

    public static ModuleCacheResponse empty() {
        return ModuleCacheResponse.of(null, null, null);
    }
}
