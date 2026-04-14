package org.prebid.server.bidder;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class Usersyncer {

    boolean enabled;

    String bidder;

    String cookieFamilyName;

    UsersyncMethod iframe;

    UsersyncMethod redirect;

    boolean skipWhenInGdprScope;

    List<Integer> gppSidToSkip;

    public static Usersyncer of(String bidder,
                                String cookieFamilyName,
                                UsersyncMethod iframe,
                                UsersyncMethod redirect,
                                boolean skipWhenInGdprScope,
                                List<Integer> gppSidToSkip) {

        return of(
                true,
                bidder,
                cookieFamilyName,
                iframe,
                redirect,
                skipWhenInGdprScope,
                gppSidToSkip);
    }
}
