package org.prebid.server.cookie.model;

import lombok.Value;
import org.prebid.server.cookie.UidsCookie;

@Value(staticConstructor = "of")
public class UidsCookieUpdateResult {

    boolean successfullyUpdated;

    UidsCookie uidsCookie;

    public static UidsCookieUpdateResult updated(UidsCookie uidsCookie) {
        return of(true, uidsCookie);
    }

    public static UidsCookieUpdateResult unaltered(UidsCookie uidsCookie) {
        return of(false, uidsCookie);
    }
}
