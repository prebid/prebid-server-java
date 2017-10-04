package org.rtb.vexing.model.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Format;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.List;

@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public final class Bidder {

    /* Unique code for an adapter to call. */
    String bidderCode;

    List<Format> sizes;

    /* Whether this ad will render in the top IFRAME. */
    Integer topframe;  // ... really just a boolean 0|1.

    /* 1 = the ad is interstitial or full screen, 0 = not interstitial. */
    Integer instl;  // ... really just a boolean 0|1.

    /* Unique code of the ad unit on the page. */
    String adUnitCode;

    String bidId;

    ObjectNode params;

    public static Bidder from(AdUnit unit, Bid bid) {
        /*FIXME: bid.bidId could be absent, generate in this case*/
        return new Bidder(bid.bidder, unit.sizes, unit.topframe, unit.instl, unit.code, bid.bidId, bid.params);
    }
}
