package org.prebid.server.hooks.modules.optable.targeting.model.net;

import io.vertx.core.MultiMap;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class HttpRequest {

    String uri;

    String query;

    MultiMap headers;
}
