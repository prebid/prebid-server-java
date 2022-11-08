package org.prebid.server.auction.model;

import com.iab.openrtb.request.BidRequest;
import lombok.Value;
import org.prebid.server.auction.versionconverter.OrtbVersion;

/**
 * Structure to pass {@link BidRequest} along with the bidder name
 */
@Value(staticConstructor = "of")
public class BidderRequest {

    String bidder;

    OrtbVersion ortbVersion;

    String storedResponse;

    BidRequest bidRequest;

    public BidderRequest with(BidRequest bidRequest) {
        return of(this.bidder, this.ortbVersion, this.storedResponse, bidRequest);
    }
}
