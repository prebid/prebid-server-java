package org.rtb.vexing.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.BidderStatus;

import java.util.List;

@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class BidderResult {

    BidderStatus bidderStatus;

    List<Bid> bids;

    boolean timedOut;
}
