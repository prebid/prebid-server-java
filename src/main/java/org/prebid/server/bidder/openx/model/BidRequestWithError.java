package org.prebid.server.bidder.openx.model;

import com.iab.openrtb.request.BidRequest;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class BidRequestWithError {

    BidRequest bidRequest;

    List<String> errors;
}
