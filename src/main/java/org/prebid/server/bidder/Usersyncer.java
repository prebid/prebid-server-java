package org.prebid.server.bidder;

import lombok.Value;
import org.prebid.server.spring.config.bidder.model.usersync.CookieFamilySource;

@Value(staticConstructor = "of")
public class Usersyncer {

    boolean enabled;

    String cookieFamilyName;

    CookieFamilySource cookieFamilySource;

    UsersyncMethod iframe;

    UsersyncMethod redirect;

    public static Usersyncer of(String cookieFamilyName, UsersyncMethod iframe, UsersyncMethod redirect) {
        return of(true, cookieFamilyName, CookieFamilySource.ROOT, iframe, redirect);
    }
}
