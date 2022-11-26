package org.prebid.server.bidder;

import lombok.Value;
import org.prebid.server.spring.config.bidder.model.usersync.CookieFamilySource;

@Value(staticConstructor = "of")
public class Usersyncer {

    String cookieFamilyName;

    CookieFamilySource cookieFamilySource;

    UsersyncMethod iframe;

    UsersyncMethod redirect;

    public static Usersyncer of(String cookieFamilyName, UsersyncMethod iframe, UsersyncMethod redirect) {
        return of(cookieFamilyName, CookieFamilySource.ROOT, iframe, redirect);
    }
}
