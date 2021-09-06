package org.prebid.server.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class HttpRequestContext {

    String absoluteUri;

    CaseInsensitiveMultiMap queryParams;

    CaseInsensitiveMultiMap headers;

    String body;

    String scheme;

    String remoteHost;
}
