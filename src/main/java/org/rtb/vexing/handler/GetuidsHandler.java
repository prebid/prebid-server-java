package org.rtb.vexing.handler;

import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.cookie.UidsCookieFactory;

import java.util.Objects;

public class GetuidsHandler {

    private final UidsCookieFactory uidsCookieFactory;

    public GetuidsHandler(UidsCookieFactory uidsCookieFactory) {
        this.uidsCookieFactory = Objects.requireNonNull(uidsCookieFactory);
    }

    public void getuids(RoutingContext routingContext) {
        final UidsCookie uidsCookie = uidsCookieFactory.parseFromRequest(routingContext);
        final Cookie cookie = uidsCookie.toCookie();
        routingContext.addCookie(cookie).response().end(uidsCookie.toJson());
    }
}
