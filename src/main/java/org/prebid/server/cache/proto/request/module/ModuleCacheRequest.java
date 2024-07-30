package org.prebid.server.cache.proto.request.module;

import lombok.Value;

@Value(staticConstructor = "of")
public class ModuleCacheRequest {

    String key;

    StorageDataType type;

    String value;

    String application;

    Integer ttlseconds;
}
