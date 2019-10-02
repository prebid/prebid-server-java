package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.prebid.server.execution.LogModifier;

import java.util.function.BiConsumer;

public class AdminHandler implements Handler<RoutingContext> {

    private static final String RECORDS_PARAM = "records";
    private static final String LOGGING_PARAM = "logging";

    private final LogModifier loggerModifier;

    public AdminHandler(LogModifier loggerModifier) {
        this.loggerModifier = loggerModifier;
    }

    @Override
    public void handle(RoutingContext context) {
        final HttpServerRequest request = context.request();
        final BiConsumer<Logger, String> loggingLevelModifier;
        final int records;
        try {
            loggingLevelModifier = loggingLevel(request.getParam(LOGGING_PARAM));
            records = records(request.getParam(RECORDS_PARAM));
        } catch (IllegalArgumentException e) {
            context.response()
                    .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                    .end(e.getMessage());
            return;
        }

        loggerModifier.setLogModifier(records, loggingLevelModifier);
        context.response().end();
    }

    private BiConsumer<Logger, String> loggingLevel(String level) {
        if (StringUtils.isBlank(level)) {
            throw new IllegalArgumentException(String.format("Invalid LoggingLevel: %s", level));
        }

        switch (level) {
            case "info":
                return Logger::info;
            case "warn":
                return Logger::warn;
            case "trace":
                return Logger::trace;
            case "error":
                return Logger::error;
            case "fatal":
                return Logger::fatal;
            case "debug":
                return Logger::debug;
            default:
                throw new IllegalArgumentException(String.format("Invalid LoggingLevel: %s", level));
        }
    }

    private int records(String records) {
        if (!StringUtils.isNumeric(records)) {
            throw new IllegalArgumentException(String.format("Invalid records parameter: %s", records));
        }

        final int numberOfRecords = NumberUtils.toInt(records);
        if (numberOfRecords < 0 || numberOfRecords >= 100_000) {
            throw new IllegalArgumentException(String.format("Invalid records parameter: %s, must be between"
                    + " 0 and 100_000", records));
        }
        return numberOfRecords;
    }
}

