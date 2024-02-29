package org.prebid.server.auction.model;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Deal;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.auction.versionconverter.OrtbVersion;

import java.util.List;
import java.util.Map;

@Builder(toBuilder = true)
@Value
public class BidderRequest {

    String bidder;

    OrtbVersion ortbVersion;

    String storedResponse;

    Map<String, List<Deal>> impIdToDeals;

    BidRequest bidRequest;

    public BidderRequest with(BidRequest bidRequest) {
        return toBuilder().bidRequest(bidRequest).build();
    }
}
