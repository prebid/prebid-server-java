package org.prebid.server.bidder;

import lombok.Value;
import org.prebid.server.spring.config.bidder.model.usersync.CookieFamilySource;

import java.util.List;

@Value(staticConstructor = "of")
public class Usersyncer {

    boolean enabled;

    String cookieFamilyName;

    CookieFamilySource cookieFamilySource;

    UsersyncMethod iframe;

    UsersyncMethod redirect;

    boolean skipWhenInGdprScope;

    List<Integer> gppSidToSkip;

    public static Usersyncer of(String cookieFamilyName,
                                UsersyncMethod iframe,
                                UsersyncMethod redirect,
                                boolean skipWhenInGdprScope,
                                List<Integer> gppSidToSkip) {

        return of(
                true,
                cookieFamilyName,
                CookieFamilySource.ROOT,
                iframe,
                redirect,
                skipWhenInGdprScope,
                gppSidToSkip);
    }
}
