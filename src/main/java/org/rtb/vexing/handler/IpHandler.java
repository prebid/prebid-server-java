package org.rtb.vexing.handler;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This component enables tracing of incoming HTTP request's parameters
 */
public class IpHandler implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext context) {

        final HttpServerRequest request = context.request();
        final String userAgent = request.headers().get(HttpHeaders.USER_AGENT);
        final String hostIp = request.remoteAddress().host();

        final String xRealIpHeaderValue = getXRealIpHeaderValue(request);
        final String xFowardedForHeaderValue = getXFowardedForHeaderValue(request);

        final String forwardedIp = ObjectUtils.firstNonNull(xFowardedForHeaderValue, xRealIpHeaderValue, "");
        final String realIp = ObjectUtils.firstNonNull(xFowardedForHeaderValue, xRealIpHeaderValue, hostIp);

        final List<String> result = new ArrayList<>();
        result.add(String.format("User Agent: %s", ObjectUtils.firstNonNull(userAgent, "")));
        result.add(String.format("IP: %s", ObjectUtils.firstNonNull(hostIp, "")));
        result.add(String.format("Port: %s", request.remoteAddress().port()));
        result.add(String.format("Forwarded IP: %s", forwardedIp));
        result.add(String.format("Real IP: %s", realIp));

        final Stream<String> headers = request.headers().entries().stream()
                .map(e -> String.join(": ", e.getKey(), e.getValue()));

        context.response().end(Stream.concat(result.stream(), headers)
                .collect(Collectors.joining("\n")));
    }

    private String getXFowardedForHeaderValue(HttpServerRequest request) {
        return StringUtils.trimToNull(StringUtils.substringBefore(request.headers().get("X-Forwarded-For"), ","));
    }

    private static String getXRealIpHeaderValue(HttpServerRequest request) {
        return StringUtils.trimToNull(request.headers().get("X-Real-IP"));
    }
}
