package org.prebid.server.auction.model;

import com.iab.openrtb.request.BidRequest;
import lombok.Value;

/**
 * Structure to pass {@link BidRequest} along with the bidder name
 */
@Value(staticConstructor = "of")
public class BidderRequest {

    String bidder;

    String storedResponse;

    BidRequest bidRequest;
}
