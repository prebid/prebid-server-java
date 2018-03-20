package org.prebid.server.bidder.sovrn.model;

import com.iab.openrtb.request.BidRequest;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class BidRequestWithUrl {

    BidRequest bidRequest;

    String endpointUrl;
}
