package org.prebid.server.bidder;

import lombok.Value;

@Value(staticConstructor = "of")
public class Usersyncer {

    String cookieFamilyName;

    UsersyncMethod primaryMethod;

    UsersyncMethod secondaryMethod;

    @Value(staticConstructor = "of")
    public static class UsersyncMethod {

        String type;

        String usersyncUrl;

        String redirectUrl;

        boolean supportCORS;
    }
}
