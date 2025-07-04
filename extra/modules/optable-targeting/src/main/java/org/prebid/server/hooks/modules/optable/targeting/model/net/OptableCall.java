package org.prebid.server.hooks.modules.optable.targeting.model.net;

import lombok.Value;

@Value(staticConstructor = "succeededHttp")
public class OptableCall {

    HttpRequest request;

    HttpResponse response;
}
