package org.prebid.server.proto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class BidderStatus {

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
