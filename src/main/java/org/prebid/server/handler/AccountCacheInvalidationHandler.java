package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
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
    private final String endpoint;

    public AccountCacheInvalidationHandler(CachingApplicationSettings cachingApplicationSettings, String endpoint) {
        this.cachingApplicationSettings = Objects.requireNonNull(cachingApplicationSettings);
        this.endpoint = Objects.requireNonNull(endpoint);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final String accountId = routingContext.request().getParam(ACCOUNT_ID_PARAM);

        if (StringUtils.isBlank(accountId)) {
            HttpUtil.executeSafely(routingContext, endpoint,
                    response -> response
                            .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                            .end("Account id is not defined"));
        } else {
            cachingApplicationSettings.invalidateAccountCache(accountId);
            HttpUtil.executeSafely(routingContext, endpoint,
                    HttpServerResponse::end);
        }
    }
}
