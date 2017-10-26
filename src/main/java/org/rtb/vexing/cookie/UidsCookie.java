package org.rtb.vexing.cookie;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.rtb.vexing.model.Uids;

import java.util.Base64;
import java.util.Collections;
import java.util.Objects;

public class UidsCookie {

    private static final Logger logger = LoggerFactory.getLogger(UidsCookie.class);

    private static final String COOKIE_NAME = "uids";

    private final Uids uids;

    private UidsCookie(Uids uids) {
        this.uids = uids;
    }

    public static UidsCookie parseFromRequest(RoutingContext context) {
        Uids uids = null;
        final Cookie uidsCookie = context.getCookie(UidsCookie.COOKIE_NAME);
        if (uidsCookie != null) {
            try {
                uids = new JsonObject(Buffer.buffer(Base64.getUrlDecoder().decode(uidsCookie.getValue())))
                        .mapTo(Uids.class);
            } catch (IllegalArgumentException | DecodeException e) {
                logger.debug("Could not decode or parse {0} cookie value {1}", UidsCookie.COOKIE_NAME,
                        uidsCookie.getValue(), e);
            }
        }
        return new UidsCookie(uids != null ? uids : Uids.builder().uids(Collections.emptyMap()).build());
    }

    public String uidFrom(String bidderCode) {
        return uids.uids.get(bidderCode);
    }

    public boolean allowsSync() {
        return !Objects.equals(uids.optout, Boolean.TRUE);
    }
}
