package org.prebid.server.hooks.modules.optable.targeting.model.net;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OptableCall {

    HttpRequest request;

    HttpResponse response;

    OptableError error;

    public static OptableCall succeededHttp(HttpRequest request,
                                                  HttpResponse response,
                                                  OptableError error) {

        return new OptableCall(request, response, error);
    }

    public static OptableCall failedHttp(HttpRequest request, OptableError error) {
        return new OptableCall(request, null, error);
    }
}
