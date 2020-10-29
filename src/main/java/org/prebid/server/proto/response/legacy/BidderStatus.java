package org.prebid.server.proto.response.legacy;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.response.UsersyncInfo;

import java.util.List;

@Deprecated
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
