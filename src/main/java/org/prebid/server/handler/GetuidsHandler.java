package org.prebid.server.handler;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class GetuidsHandler implements Handler<RoutingContext> {

    private final UidsCookieService uidsCookieService;

    public GetuidsHandler(UidsCookieService uidsCookieService) {
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);
        final Map<String, String> uids = uidsCookie.getCookieUids().getUids().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        // Extract just the uid for each bidder
                        uidEntry -> uidEntry.getValue().getUid()));

        routingContext.response().end(Json.encode(BuyerUids.of(uids)));
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class BuyerUids {

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        Map<String, String> buyeruids;
    }
}
