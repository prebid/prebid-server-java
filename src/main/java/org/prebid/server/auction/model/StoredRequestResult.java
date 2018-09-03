package org.prebid.server.auction.model;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class StoredRequestResult {

    BidRequest bidRequest;

    Map<Imp, String> failedImpsToError;
}

