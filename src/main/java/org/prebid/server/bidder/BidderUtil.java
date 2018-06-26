package org.prebid.server.bidder;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;

/**
 * Util class to help {@link Bidder}s implementation process responses and requests
 */
public class BidderUtil {

    private BidderUtil() {
    }

    public static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";

    /**
     * Creates shared headers for all bidders
     */
    public static MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .add(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON);
    }
}
