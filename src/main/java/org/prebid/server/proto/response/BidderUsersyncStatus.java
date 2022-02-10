package org.prebid.server.proto.response;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class BidderUsersyncStatus {

    String bidder;

    String error;

    Boolean noCookie;

    UsersyncInfo usersync;
}
