package org.prebid.server.bidder;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class Usersyncer {

    String cookieFamilyName;

    List<UsersyncMethod> methods;

    @Value(staticConstructor = "of")
    public static class UsersyncMethod {

        UsersyncMethodType type;

        String usersyncUrl;

        String redirectUrl;

        boolean supportCORS;
    }
}
