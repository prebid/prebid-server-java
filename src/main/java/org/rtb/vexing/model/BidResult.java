package org.rtb.vexing.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.BidderDebug;

import java.util.List;

@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class BidResult {

    List<Bid.BidBuilder> bidBuilders;

    BidderDebug bidderDebug;

    String error;

    boolean timedOut;

    public static BidResult error(BidderDebug bidderDebug, String error) {
        return new BidResult(null, bidderDebug, error, false);
    }

    public static BidResult timeout(BidderDebug bidderDebug, String error) {
        return new BidResult(null, bidderDebug, error, true);
    }

    public static BidResult success(List<Bid.BidBuilder> bidBuilders, BidderDebug bidderDebug) {
        return new BidResult(bidBuilders, bidderDebug, null, false);
    }

    public static BidResult empty(BidderDebug bidderDebug) {
        return new BidResult(null, bidderDebug, null, false);
    }
}
