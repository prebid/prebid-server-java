package org.prebid.server.auction.versionconverter.down;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConverter;
import org.prebid.server.auction.versionconverter.OrtbVersion;

public class BidRequestOrtb26To25Converter implements BidRequestOrtbVersionConverter {

    @Override
    public OrtbVersion inVersion() {
        return OrtbVersion.ORTB_2_6;
    }

    @Override
    public OrtbVersion outVersion() {
        return OrtbVersion.ORTB_2_5;
    }

    @Override
    public BidRequest convert(BidRequest bidRequest) {
        return bidRequest;
    }
}
