package org.prebid.server.bidder;

import lombok.Value;

@Value(staticConstructor = "of")
public class Usersyncer {

    String cookieFamilyName;

    UsersyncMethod iframe;

    UsersyncMethod redirect;
}
