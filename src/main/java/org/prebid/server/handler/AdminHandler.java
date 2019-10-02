package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.prebid.server.execution.LoggerLevelModifier;

public class AdminHandler implements Handler<RoutingContext> {

    private final LoggerLevelModifier errorLoggerLevelSwitch;

    public AdminHandler(LoggerLevelModifier errorLoggerLevelSwitch) {
        this.errorLoggerLevelSwitch = errorLoggerLevelSwitch;
    }

    @Override
    public void handle(RoutingContext context) {
        final HttpServerRequest request = context.request();
        final int records;
        try {
            records = records(request.getParam("records"));
        } catch (Exception e) {
            context.response()
                    .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                    .end(e.getMessage());
            return;
        }

        errorLoggerLevelSwitch.setErrorOnBadRequestCount(records);
        context.response().end();
    }

    private int records(String records) {
        if (!StringUtils.isNumeric(records)) {
            throw new IllegalArgumentException(String.format("Invalid records parameter: %s", records));
        }

        final int numberOfRecords = NumberUtils.toInt(records);
        if (numberOfRecords < 0 || numberOfRecords >= 100_000) {
            throw new IllegalArgumentException(String.format("Invalid records parameter: %s, must be between" +
                    " 0 and 100_000", records));
        }
        return numberOfRecords;
    }
}

