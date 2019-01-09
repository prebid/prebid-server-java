package org.prebid.server.bidder;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import org.prebid.server.util.HttpUtil;

/**
 * Util interface to help {@link Bidder}s implementation process responses and requests
 */
public interface BidderUtil {

    /**
     * Creates shared headers for all bidders
     */
    static MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE)
                .add(HttpUtil.ACCEPT_HEADER, HttpHeaderValues.APPLICATION_JSON);
    }
}
