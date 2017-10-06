package org.rtb.vexing.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.List;

@ToString
@EqualsAndHashCode
@AllArgsConstructor(staticName = "from")
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public final class Bidder {

    String bidderCode;

    List<AdUnitBid> adUnitBids;
}
