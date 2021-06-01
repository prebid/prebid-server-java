package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.execution.HttpResponseSender;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.settings.CacheNotificationListener;
import org.prebid.server.settings.proto.request.InvalidateSettingsCacheRequest;
import org.prebid.server.settings.proto.request.UpdateSettingsCacheRequest;

import java.util.Objects;

/**
 * Handles HTTP requests for updating/invalidating settings cache.
 */
public class SettingsCacheNotificationHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(SettingsCacheNotificationHandler.class);

    private final CacheNotificationListener cacheNotificationListener;
    private final JacksonMapper mapper;

    public SettingsCacheNotificationHandler(CacheNotificationListener cacheNotificationListener, JacksonMapper mapper) {
        this.cacheNotificationListener = Objects.requireNonNull(cacheNotificationListener);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        switch (routingContext.request().method()) {
            case POST:
                doSave(routingContext);
                break;
            case DELETE:
                doInvalidate(routingContext);
                break;
            default:
                doFail(routingContext);
        }
    }

    /**
     * Propagates updating settings cache
     */
    private void doSave(RoutingContext routingContext) {
        final Buffer body = routingContext.getBody();
        if (body == null) {
            respondWith(routingContext, HttpResponseStatus.BAD_REQUEST, "Missing update data.");
            return;
        }

        final UpdateSettingsCacheRequest request;
        try {
            request = mapper.decodeValue(body, UpdateSettingsCacheRequest.class);
        } catch (DecodeException e) {
            respondWith(routingContext, HttpResponseStatus.BAD_REQUEST, "Invalid update.");
            return;
        }

        cacheNotificationListener.save(request.getRequests(), request.getImps());
        respondWith(routingContext, HttpResponseStatus.OK);
    }

    /**
     * Propagates invalidating settings cache
     */
    private void doInvalidate(RoutingContext routingContext) {
        final Buffer body = routingContext.getBody();
        if (body == null) {
            respondWith(routingContext, HttpResponseStatus.BAD_REQUEST, "Missing invalidation data.");
            return;
        }

        final InvalidateSettingsCacheRequest request;
        try {
            request = mapper.decodeValue(body, InvalidateSettingsCacheRequest.class);
        } catch (DecodeException e) {
            respondWith(routingContext, HttpResponseStatus.BAD_REQUEST, "Invalid invalidation.");
            return;
        }

        cacheNotificationListener.invalidate(request.getRequests(), request.getImps());
        respondWith(routingContext, HttpResponseStatus.OK);
    }

    /**
     * Makes failure response in case of unexpected request
     */
    private void doFail(RoutingContext routingContext) {
        respondWith(routingContext, HttpResponseStatus.METHOD_NOT_ALLOWED);
    }

    private static void respondWith(RoutingContext routingContext, HttpResponseStatus status) {
        respondWith(routingContext, status, null);
    }

    private static void respondWith(RoutingContext routingContext, HttpResponseStatus status, String body) {
        HttpResponseSender.from(routingContext, logger)
                .status(status)
                .body(body)
                .send();
    }
}
