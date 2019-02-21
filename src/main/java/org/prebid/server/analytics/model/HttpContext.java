package org.prebid.server.analytics.model;

import io.vertx.ext.web.RoutingContext;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.util.HttpUtil;

import java.util.Map;
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
        return context.request().params().entries().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map<String, String> headers(RoutingContext context) {
        return context.request().headers().entries().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map<String, String> cookies(RoutingContext context) {
        return HttpUtil.cookiesAsMap(context);
    }
}
