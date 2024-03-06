package org.prebid.server.cookie.model;

import io.vertx.core.http.Cookie;
import lombok.Value;

/**
 * Defines Partitioned Cookie
 * todo: remove this class after the Vertx dependencies update
 *  (assuming the newer version of the Vertx Cookie supports Partitioned Cookies)
 */

@Value(staticConstructor = "of")
public class PartitionedCookie {

    Cookie cookie;

    public String encode() {
        return cookie.encode() + "; Partitioned";
    }
}
