package org.prebid.server.cookie.model;

import lombok.Value;
import org.prebid.server.cookie.UidsCookie;

import java.util.Map;

@Value(staticConstructor = "of")
public class UidsCookieUpdateResult {

    boolean successfullyUpdated;

    Map<String, UidsCookie> uidsCookies;

    public static UidsCookieUpdateResult success(Map<String, UidsCookie> uidsCookies) {
        return of(true, uidsCookies);
    }

    public static UidsCookieUpdateResult failure(Map<String, UidsCookie> uidsCookies) {
        return of(false, uidsCookies);
    }
}
