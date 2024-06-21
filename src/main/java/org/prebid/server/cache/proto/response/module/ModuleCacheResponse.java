package org.prebid.server.cache.proto.response.module;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.cache.proto.request.module.ModuleCacheType;

@Value
@Builder(toBuilder = true)
public class ModuleCacheResponse {

    String key;

    ModuleCacheType type;

    String value;

    public static ModuleCacheResponse empty() {
        return ModuleCacheResponse.of(null, null, null);
    }

    public static ModuleCacheResponse of(String key, ModuleCacheType type, String value) {
        return ModuleCacheResponse.builder()
                .key(key)
                .type(type)
                .value(value)
                .build();
    }
}
