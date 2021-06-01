package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.execution.HttpResponseSender;
import org.prebid.server.settings.CachingApplicationSettings;

import java.util.Objects;

/**
 * Handles HTTP requests for invalidating account settings cache.
 */
public class AccountCacheInvalidationHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AccountCacheInvalidationHandler.class);

    private static final String ACCOUNT_ID_PARAM = "account";

    private final CachingApplicationSettings cachingApplicationSettings;

    public AccountCacheInvalidationHandler(CachingApplicationSettings cachingApplicationSettings) {
        this.cachingApplicationSettings = Objects.requireNonNull(cachingApplicationSettings);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final String accountId = routingContext.request().getParam(ACCOUNT_ID_PARAM);

        final HttpResponseSender responseSender = HttpResponseSender.from(routingContext, logger);
        if (StringUtils.isBlank(accountId)) {
            responseSender
                    .status(HttpResponseStatus.BAD_REQUEST)
                    .body("Account id is not defined");
        } else {
            cachingApplicationSettings.invalidateAccountCache(accountId);
        }
        responseSender.send();
    }
}
