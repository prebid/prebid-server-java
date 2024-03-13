package org.prebid.server.handler.admin;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.settings.CacheNotificationListener;
import org.prebid.server.settings.proto.request.InvalidateSettingsCacheRequest;
import org.prebid.server.settings.proto.request.UpdateSettingsCacheRequest;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

/**
 * Handles HTTP requests for updating/invalidating settings cache.
 */
public class SettingsCacheNotificationHandler implements Handler<RoutingContext> {

    private final CacheNotificationListener cacheNotificationListener;
    private final JacksonMapper mapper;
    private final String endpoint;

    public SettingsCacheNotificationHandler(CacheNotificationListener cacheNotificationListener, JacksonMapper mapper,
                                            String endpoint) {
        this.cacheNotificationListener = Objects.requireNonNull(cacheNotificationListener);
        this.mapper = Objects.requireNonNull(mapper);
        this.endpoint = Objects.requireNonNull(endpoint);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        switch (routingContext.request().method()) {
            case POST -> doSave(routingContext);
            case DELETE -> doInvalidate(routingContext);
            default -> doFail(routingContext);
        }
    }

    /**
     * Propagates updating settings cache.
     */
    private void doSave(RoutingContext routingContext) {
        final Buffer body = routingContext.getBody();
        if (body == null) {
            respondWithBadRequest(routingContext, "Missing update data.");
            return;
        }

        final UpdateSettingsCacheRequest request;
        try {
            request = mapper.decodeValue(body, UpdateSettingsCacheRequest.class);
        } catch (DecodeException e) {
            respondWithBadRequest(routingContext, "Invalid update.");
            return;
        }

        cacheNotificationListener.save(request.getRequests(), request.getImps());
        respondWith(routingContext, HttpResponseStatus.OK);
    }

    /**
     * Propagates invalidating settings cache.
     */
    private void doInvalidate(RoutingContext routingContext) {
        final Buffer body = routingContext.getBody();
        if (body == null) {
            respondWithBadRequest(routingContext, "Missing invalidation data.");
            return;
        }

        final InvalidateSettingsCacheRequest request;
        try {
            request = mapper.decodeValue(body, InvalidateSettingsCacheRequest.class);
        } catch (DecodeException e) {
            respondWithBadRequest(routingContext, "Invalid invalidation.");
            return;
        }

        cacheNotificationListener.invalidate(request.getRequests(), request.getImps());
        respondWith(routingContext, HttpResponseStatus.OK);
    }

    /**
     * Makes failure response in case of unexpected request.
     */
    private void doFail(RoutingContext routingContext) {
        respondWith(routingContext, HttpResponseStatus.METHOD_NOT_ALLOWED);
    }

    private void respondWithBadRequest(RoutingContext routingContext, String body) {
        HttpUtil.executeSafely(routingContext, endpoint,
                response -> response
                        .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                        .end(body));
    }

    private void respondWith(RoutingContext routingContext, HttpResponseStatus status) {
        HttpUtil.executeSafely(routingContext, endpoint,
                response -> response
                        .setStatusCode(status.code())
                        .end());
    }
}
