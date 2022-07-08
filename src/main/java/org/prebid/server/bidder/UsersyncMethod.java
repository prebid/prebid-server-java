package org.prebid.server.bidder;

import lombok.Value;

@Value(staticConstructor = "of")
public class UsersyncMethod {

    UsersyncMethodType type;

    String usersyncUrl;

    String redirectUrl;

    boolean supportCORS;
}
