package org.prebid.server.auction.versionconverter.up;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConverter;

public class BidRequestOrtb25To26Converter implements BidRequestOrtbVersionConverter {

    @Override
    public BidRequest convert(BidRequest bidRequest) {
        return bidRequest;
    }
}
