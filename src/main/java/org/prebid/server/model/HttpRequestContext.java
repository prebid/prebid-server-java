package org.prebid.server.model;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.util.HttpUtil;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Builder
@Value
public class HttpRequestContext {

    String absoluteUri;

    CaseInsensitiveMultiMap queryParams;

    CaseInsensitiveMultiMap headers;

    String body;

    String scheme;

    String remoteHost;

    public static HttpRequestContext from(RoutingContext context) {
        return HttpRequestContext.builder()
                .absoluteUri(context.request().uri())
                .queryParams(CaseInsensitiveMultiMap.builder().addAll(toMap(context.request().params())).build())
                .headers(headers(context))
                .build();
    }

    private static CaseInsensitiveMultiMap headers(RoutingContext context) {
        final Map<String, String> headers = toMap(context.request().headers());
        final String cookieHeader = HttpUtil.createCookiesHeader(context);
        if (StringUtils.isNotEmpty(cookieHeader)) {
            headers.put(HttpHeaders.COOKIE.toString(), cookieHeader);
        }

        return CaseInsensitiveMultiMap.builder().addAll(headers).build();
    }

    private static Map<String, String> toMap(MultiMap multiMap) {
        return multiMap.names().stream()
                .collect(Collectors.toMap(Function.identity(), multiMap::get));
    }
}
