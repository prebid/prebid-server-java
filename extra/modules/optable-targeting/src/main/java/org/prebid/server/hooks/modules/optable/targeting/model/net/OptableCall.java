package org.prebid.server.hooks.modules.optable.targeting.model.net;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OptableCall {

    HttpRequest request;

    HttpResponse response;

    public static OptableCall succeededHttp(HttpRequest request, HttpResponse response) {
        return new OptableCall(request, response);
    }
}
