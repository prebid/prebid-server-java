package org.rtb.vexing.model.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public final class Bidder {

    String bidder;

    String adUnitCode;

    Integer responseTime;

    Integer numBids;

    String error;

    Boolean noCookie;

    Boolean noBid;

    // UsersyncInfo usersync;

    // BidderDebug debug;
}
