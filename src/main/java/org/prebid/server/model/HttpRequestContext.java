package org.prebid.server.model;

import io.vertx.core.MultiMap;
import io.vertx.core.http.Cookie;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Builder
@Value
public class HttpRequestContext {

    String absoluteUri;

    MultiMap queryParams;

    MultiMap headers;

    Map<String, Cookie> cookies;

    String body;

    String scheme;

    String remoteHost;
}
