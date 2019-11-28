package org.prebid.server.handler;

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

    public SettingsCacheNotificationHandler(CacheNotificationListener cacheNotificationListener, JacksonMapper mapper) {
        this.cacheNotificationListener = Objects.requireNonNull(cacheNotificationListener);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void handle(RoutingContext context) {
        switch (context.request().method()) {
            case POST:
                doSave(context);
                break;
            case DELETE:
                doInvalidate(context);
                break;
            default:
                doFail(context);
        }
    }

    /**
     * Propagates updating settings cache
     */
    private void doSave(RoutingContext context) {
        final Buffer body = context.getBody();
        if (body == null) {
            HttpUtil.respondWith(context, HttpResponseStatus.BAD_REQUEST, "Missing update data.");
            return;
        }

        final UpdateSettingsCacheRequest request;
        try {
            request = mapper.decodeValue(body, UpdateSettingsCacheRequest.class);
        } catch (DecodeException e) {
            HttpUtil.respondWith(context, HttpResponseStatus.BAD_REQUEST, "Invalid update.");
            return;
        }

        cacheNotificationListener.save(request.getRequests(), request.getImps());
        HttpUtil.respondWith(context, HttpResponseStatus.OK, null);
    }

    /**
     * Propagates invalidating settings cache
     */
    private void doInvalidate(RoutingContext context) {
        final Buffer body = context.getBody();
        if (body == null) {
            HttpUtil.respondWith(context, HttpResponseStatus.BAD_REQUEST, "Missing invalidation data.");
            return;
        }

        final InvalidateSettingsCacheRequest request;
        try {
            request = mapper.decodeValue(body, InvalidateSettingsCacheRequest.class);
        } catch (DecodeException e) {
            HttpUtil.respondWith(context, HttpResponseStatus.BAD_REQUEST, "Invalid invalidation.");
            return;
        }

        cacheNotificationListener.invalidate(request.getRequests(), request.getImps());
        HttpUtil.respondWith(context, HttpResponseStatus.OK, null);
    }

    /**
     * Makes failure response in case of unexpected request
     */
    private void doFail(RoutingContext context) {
        HttpUtil.respondWith(context, HttpResponseStatus.METHOD_NOT_ALLOWED, null);
    }
}
