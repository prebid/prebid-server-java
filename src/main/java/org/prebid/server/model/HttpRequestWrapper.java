package org.prebid.server.model;

import io.vertx.core.MultiMap;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class HttpRequestWrapper {

    String absoluteUri;

    MultiMap queryParams;

    MultiMap headers;

    String body;

    String scheme;

    String remoteHost;
}
