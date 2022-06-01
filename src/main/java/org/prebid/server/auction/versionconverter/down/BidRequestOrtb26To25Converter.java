package org.prebid.server.auction.versionconverter.down;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConverter;

public class BidRequestOrtb26To25Converter implements BidRequestOrtbVersionConverter {

    @Override
    public BidRequest convert(BidRequest bidRequest) {
        return bidRequest;
    }
}
