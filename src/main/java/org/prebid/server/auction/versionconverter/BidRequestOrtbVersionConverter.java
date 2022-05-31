package org.prebid.server.auction.versionconverter;

import com.iab.openrtb.request.BidRequest;

public interface BidRequestOrtbVersionConverter {

    OrtbVersion inVersion();

    OrtbVersion outVersion();

    BidRequest convert(BidRequest bidRequest);
}
