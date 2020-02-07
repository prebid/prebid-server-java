package org.prebid.server.handler;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.json.JacksonMapper;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class GetuidsHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(GetuidsHandler.class);

    private final UidsCookieService uidsCookieService;
    private final JacksonMapper mapper;

    public GetuidsHandler(UidsCookieService uidsCookieService, JacksonMapper mapper) {
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void handle(RoutingContext context) {
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(context);
        final Map<String, String> uids = uidsCookie.getCookieUids().getUids().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        // Extract just the uid for each bidder
                        uidEntry -> uidEntry.getValue().getUid()));

        final String body = mapper.encode(BuyerUids.of(uids));

        // don't send the response if client has gone
        if (context.response().closed()) {
            logger.warn("The client already closed connection, response will be skipped");
            return;
        }
        context.response().end(body);
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class BuyerUids {

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        Map<String, String> buyeruids;
    }
}
