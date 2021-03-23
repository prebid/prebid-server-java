package org.prebid.server.model;

import io.vertx.core.MultiMap;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class HttpRequestWrapper {

    MultiMap queryParams;

    MultiMap headers;

    String body;
}
