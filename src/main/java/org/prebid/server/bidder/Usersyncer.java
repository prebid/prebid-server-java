package org.prebid.server.bidder;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class Usersyncer {

    String cookieFamilyName;

    UsersyncMethod iframe;

    UsersyncMethod redirect;
}
