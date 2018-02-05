package org.rtb.vexing.model.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
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
