package org.prebid.server.auction.model;

import com.iab.openrtb.request.BidRequest;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Structure to pass {@link BidRequest} along with the bidder name
 */
@AllArgsConstructor(staticName = "of")
@Value
public final class BidderRequest {

    String bidder;

    BidRequest bidRequest;
}
