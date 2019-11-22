package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.prebid.server.execution.LogModifier;

import java.util.Objects;
import java.util.function.BiConsumer;

public class AdminHandler implements Handler<RoutingContext> {

    private static final String RECORDS_PARAM = "records";
    private static final String LOGGING_PARAM = "logging";

    private final LogModifier logModifier;

    public AdminHandler(LogModifier logModifier) {
        this.logModifier = Objects.requireNonNull(logModifier);
    }

    @Override
    public void handle(RoutingContext context) {
        final HttpServerRequest request = context.request();
        final BiConsumer<Logger, String> loggingLevelModifier;
        final String loggingParam = request.getParam(LOGGING_PARAM);
        final String recordsParam = request.getParam(RECORDS_PARAM);
        final int records;

        try {
            loggingLevelModifier = loggingLevel(loggingParam);
            records = records(recordsParam);
        } catch (IllegalArgumentException e) {
            context.response()
                    .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                    .end(e.getMessage());
            return;
        }

        logModifier.set(loggingLevelModifier, records);
        context.response()
                .end(String.format("Logging level was changed to %s, for %s requests", loggingParam, recordsParam));
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

