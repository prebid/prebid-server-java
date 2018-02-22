package org.prebid.server.model.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public final class BidderStatus {

    String bidder;

    String adUnit;

    Integer responseTimeMs;

    Integer numBids;

    String error;

    Boolean noCookie;

    Boolean noBid;

    UsersyncInfo usersync;

    List<BidderDebug> debug;
}
