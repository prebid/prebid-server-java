package org.prebid.server.cache.proto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CacheErrorResponse {

    String error;

    Integer status;

    String path;

    String message;

    Long timestamp;
}
