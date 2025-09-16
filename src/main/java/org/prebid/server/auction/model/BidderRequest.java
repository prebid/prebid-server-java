package org.prebid.server.auction.model;

import com.iab.openrtb.request.BidRequest;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.prebid.server.bidder.model.Price;

import java.util.Map;

@Builder(toBuilder = true)
@Value
public class BidderRequest {

    String bidder;

    OrtbVersion ortbVersion;

    String storedResponse;

    BidRequest bidRequest;

    Map<String, Price> originalPriceFloors;

    public BidderRequest with(BidRequest bidRequest) {
        return toBuilder().bidRequest(bidRequest).build();
    }
}
