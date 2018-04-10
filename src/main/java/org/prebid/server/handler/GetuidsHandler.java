package org.prebid.server.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;

import java.util.Objects;

public class GetuidsHandler implements Handler<RoutingContext> {

    private final UidsCookieService uidsCookieService;

    public GetuidsHandler(UidsCookieService uidsCookieService) {
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);
        routingContext.addCookie(uidsCookieService.toCookie(uidsCookie)).response().end(uidsCookie.toJson());
    }
}
