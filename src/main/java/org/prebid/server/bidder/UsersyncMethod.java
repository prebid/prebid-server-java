package org.prebid.server.bidder;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class UsersyncMethod {

    UsersyncMethodType type;

    String usersyncUrl;

    String redirectUrl;

    boolean supportCORS;

    UsersyncFormat formatOverride;

    public UsersyncMethod withUsersyncUrl(String usersyncUrl) {
        return toBuilder().usersyncUrl(usersyncUrl).build();
    }
}
