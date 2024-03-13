package org.prebid.server.handler.admin;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.log.CriteriaManager;

import java.util.Objects;

public class TracerLogHandler implements Handler<RoutingContext> {

    private static final String ACCOUNT_PARAMETER = "account";
    private static final String BIDDER_CODE_PARAMETER = "bidderCode";
    private static final String LOG_LEVEL_PARAMETER = "level";
    private static final String DURATION_IN_SECONDS = "duration";

    private final CriteriaManager criteriaManager;

    public TracerLogHandler(CriteriaManager criteriaManager) {
        this.criteriaManager = Objects.requireNonNull(criteriaManager);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final MultiMap parameters = routingContext.request().params();
        final String accountId = parameters.get(ACCOUNT_PARAMETER);
        final String bidderCode = parameters.get(BIDDER_CODE_PARAMETER);

        if (StringUtils.isBlank(accountId) && StringUtils.isBlank(bidderCode)) {
            routingContext.response()
                    .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                    .end("At least one parameter should be defined: account, bidderCode");
            return;
        }

        final int duration;
        final String loggerLevel = parameters.get(LOG_LEVEL_PARAMETER);
        try {
            duration = parseDuration(parameters.get(DURATION_IN_SECONDS));
        } catch (InvalidRequestException e) {
            routingContext.response()
                    .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                    .end(e.getMessage());
            return;
        }

        try {
            criteriaManager.addCriteria(accountId, bidderCode, loggerLevel, duration);
        } catch (IllegalArgumentException e) {
            routingContext.response()
                    .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                    .end("Invalid parameter: " + e.getMessage());
            return;
        }

        routingContext.response().end();
    }

    private static int parseDuration(String rawDuration) {
        if (rawDuration == null) {
            throw new InvalidRequestException("duration parameter should be defined");
        }
        try {
            return Integer.parseInt(rawDuration);
        } catch (NumberFormatException e) {
            throw new InvalidRequestException(
                    "duration parameter should be defined as integer, but was " + rawDuration);
        }
    }
}
