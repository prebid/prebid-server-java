package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.settings.CachingApplicationSettings;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

/**
 * Handles HTTP requests for invalidating account settings cache.
 */
public class AccountCacheInvalidationHandler implements Handler<RoutingContext> {

    private static final String ACCOUNT_ID_PARAM = "account";

    private final CachingApplicationSettings cachingApplicationSettings;

    public AccountCacheInvalidationHandler(CachingApplicationSettings cachingApplicationSettings) {
        this.cachingApplicationSettings = Objects.requireNonNull(cachingApplicationSettings);
    }

    @Override
    public void handle(RoutingContext context) {
        final HttpServerRequest request = context.request();
        final String accountId = request.getParam(ACCOUNT_ID_PARAM);

        if (StringUtils.isBlank(accountId)) {
            HttpUtil.respondWith(context, HttpResponseStatus.BAD_REQUEST, "Account id is not defined");
        } else {
            cachingApplicationSettings.invalidateAccountCache(accountId);
            HttpUtil.respondWith(context, HttpResponseStatus.OK, null);
        }
    }
}
