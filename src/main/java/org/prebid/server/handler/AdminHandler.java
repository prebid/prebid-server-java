package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.prebid.server.manager.AdminManager;

import java.util.Objects;
import java.util.function.BiConsumer;

public class AdminHandler implements Handler<RoutingContext> {

    private static final String LOGGING_PARAM = "logging";
    private static final String RECORDS_PARAM = "records";

    private final AdminManager adminManager;

    public AdminHandler(AdminManager adminManager) {
        this.adminManager = Objects.requireNonNull(adminManager);
    }

    @Override
    public void handle(RoutingContext context) {
        final HttpServerRequest request = context.request();

        final BiConsumer<Logger, String> loggingLevelModifier;
        final int records;

        final String loggingLevel = request.getParam(LOGGING_PARAM);
        final String recordsAsString = request.getParam(RECORDS_PARAM);

        try {
            loggingLevelModifier = loggingLevel(loggingLevel);
            records = records(recordsAsString);
        } catch (IllegalArgumentException e) {
            context.response()
                    .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                    .end(e.getMessage());
            return;
        }

        adminManager.setupByCounter(AdminManager.COUNTER_KEY, records, loggingLevelModifier, onFinish());

        context.response()
                .end(String.format("Logging level was changed to %s, for %s requests", loggingLevel, recordsAsString));
    }

    private static BiConsumer<Logger, String> loggingLevel(String level) {
        if (StringUtils.isEmpty(level)) {
            throw new IllegalArgumentException("Logging level cannot be empty");
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
                throw new IllegalArgumentException(String.format("Invalid logging level: %s", level));
        }
    }

    private BiConsumer<Logger, String> onFinish() {
        return (logger, message) -> defaultLogModifier(logger).accept(logger, message);
    }

    private static BiConsumer<Logger, String> defaultLogModifier(Logger defaultLogger) {
        if (defaultLogger.isTraceEnabled()) {
            return Logger::trace;
        } else if (defaultLogger.isDebugEnabled()) {
            return Logger::debug;
        } else if (defaultLogger.isInfoEnabled()) {
            return Logger::info;
        } else if (defaultLogger.isWarnEnabled()) {
            return Logger::warn;
        }
        return Logger::error;
    }

    private static int records(String records) {
        if (!StringUtils.isNumeric(records)) {
            throw new IllegalArgumentException(String.format("Invalid records parameter: %s", records));
        }

        final int numberOfRecords = NumberUtils.toInt(records);
        if (numberOfRecords < 0 || numberOfRecords >= 100_000) {
            throw new IllegalArgumentException(String.format("Invalid records parameter: %s, must be between"
                    + " 0 and 100 000", records));
        }
        return numberOfRecords;
    }
}
