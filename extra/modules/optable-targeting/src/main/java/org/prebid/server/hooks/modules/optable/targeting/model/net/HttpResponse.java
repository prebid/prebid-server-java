package org.prebid.server.hooks.modules.optable.targeting.model.net;

import io.vertx.core.MultiMap;
import lombok.Value;

@Value(staticConstructor = "of")
public class HttpResponse {

    int statusCode;

    MultiMap headers;

    String body;
}
