package org.prebid.server.analytics.model;

import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.util.HttpUtil;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represents Vert.x {@link io.vertx.ext.web.RoutingContext} as a simplified POJO model.
 */
@Builder
@Value
public class HttpContext {

    String uri;

    Map<String, String> queryParams;

    Map<String, String> headers;

    Map<String, String> cookies;

    public static HttpContext from(RoutingContext context) {
        return HttpContext.builder()
                .uri(context.request().uri())
                .queryParams(queryParams(context))
                .headers(headers(context))
                .cookies(cookies(context))
                .build();
    }

    private static Map<String, String> queryParams(RoutingContext context) {
        final MultiMap params = context.request().params();
        return params.names().stream()
                .collect(Collectors.toMap(Function.identity(), params::get));
    }

    private static Map<String, String> headers(RoutingContext context) {
        final MultiMap headers = context.request().headers();
        return headers.names().stream()
                .collect(Collectors.toMap(Function.identity(), headers::get));
    }

    private static Map<String, String> cookies(RoutingContext context) {
        return HttpUtil.cookiesAsMap(context);
    }
}
