package org.prebid.server.auction.model;

import com.iab.openrtb.request.BidRequest;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.auction.versionconverter.OrtbVersion;

@Builder(toBuilder = true)
@Value
public class BidderRequest {

    String bidder;

    OrtbVersion ortbVersion;

    String storedResponse;

    BidRequest bidRequest;

    public BidderRequest with(BidRequest bidRequest) {
        return toBuilder().bidRequest(bidRequest).build();
    }
}
