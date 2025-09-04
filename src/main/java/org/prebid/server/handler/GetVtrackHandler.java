package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.cache.CoreCacheService;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.model.Endpoint;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;
import org.prebid.server.vertx.verticles.server.HttpEndpoint;
import org.prebid.server.vertx.verticles.server.application.ApplicationResource;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class GetVtrackHandler implements ApplicationResource {

    private static final Logger logger = LoggerFactory.getLogger(GetVtrackHandler.class);

    private static final String UUID_PARAMETER = "uuid";
    private static final String CH_PARAMETER = "ch";

    private final long defaultTimeout;
    private final CoreCacheService coreCacheService;
    private final TimeoutFactory timeoutFactory;

    public GetVtrackHandler(long defaultTimeout, CoreCacheService coreCacheService, TimeoutFactory timeoutFactory) {
        this.defaultTimeout = defaultTimeout;
        this.coreCacheService = Objects.requireNonNull(coreCacheService);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
    }

    @Override
    public List<HttpEndpoint> endpoints() {
        return Collections.singletonList(HttpEndpoint.of(HttpMethod.GET, Endpoint.vtrack.value()));
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final String uuid = routingContext.request().getParam(UUID_PARAMETER);
        final String ch = routingContext.request().getParam(CH_PARAMETER);
        if (StringUtils.isBlank(uuid)) {
            respondWith(
                    routingContext,
                    HttpResponseStatus.BAD_REQUEST,
                    "'%s' is a required query parameter and can't be empty".formatted(UUID_PARAMETER));
            return;
        }

        final Timeout timeout = timeoutFactory.create(defaultTimeout);

        coreCacheService.getCachedObject(uuid, ch, timeout)
                .onComplete(asyncCache -> handleCacheResult(asyncCache, routingContext));
    }

    private static void respondWithServerError(RoutingContext routingContext, Throwable exception) {
        logger.error("Error occurred while sending request to cache", exception);
        respondWith(routingContext, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                "%s: %s".formatted("Error occurred while sending request to cache", exception.getMessage()));
    }

    private static void respondWith(RoutingContext routingContext,
                                    HttpResponseStatus status,
                                    MultiMap headers,
                                    String body) {

        HttpUtil.executeSafely(routingContext, Endpoint.vtrack,
                response -> {
                    headers.forEach(response::putHeader);
                    response.setStatusCode(status.code())
                            .end(body);
                });
    }

    private static void respondWith(RoutingContext routingContext, HttpResponseStatus status, String body) {
        HttpUtil.executeSafely(routingContext, Endpoint.vtrack,
                response -> response
                        .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON)
                        .setStatusCode(status.code())
                        .end(body));
    }

    private void handleCacheResult(AsyncResult<HttpClientResponse> async, RoutingContext routingContext) {
        if (async.failed()) {
            respondWithServerError(routingContext, async.cause());
        } else {
            final HttpClientResponse response = async.result();
            final HttpResponseStatus status = HttpResponseStatus.valueOf(response.getStatusCode());
            if (status == HttpResponseStatus.OK) {
                respondWith(routingContext, status, response.getHeaders(), response.getBody());
            } else {
                respondWith(routingContext, status, response.getBody());
            }
        }
    }
}
