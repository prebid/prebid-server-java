package org.prebid.server.bidder;

import lombok.Value;

@Value(staticConstructor = "of")
public class Usersyncer {

    String cookieFamilyName;

    UsersyncMethod primaryMethod;

    UsersyncMethod secondaryMethod;

    @Value(staticConstructor = "of")
    public static class UsersyncMethod {

        public static final String IFRAME_TYPE = "iframe";
        public static final String REDIRECT_TYPE = "redirect";

        String type;

        String usersyncUrl;

        String redirectUrl;

        boolean supportCORS;
    }
}
