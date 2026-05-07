package org.prebid.server.bidder;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UsersyncMethod {

    UsersyncMethodType type;

    String usersyncUrl;

    String uidMacro;

    boolean supportCORS;

    UsersyncFormat formatOverride;
}
