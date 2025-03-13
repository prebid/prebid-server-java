package org.prebid.server.hooks.modules.optable.targeting.model.net;

import io.vertx.core.MultiMap;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class HttpResponse {

    int statusCode;

    MultiMap headers;

    String body;
}
