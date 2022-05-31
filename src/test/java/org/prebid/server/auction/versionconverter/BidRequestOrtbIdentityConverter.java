package org.prebid.server.auction.versionconverter;

import com.iab.openrtb.request.BidRequest;
import lombok.NoArgsConstructor;

@NoArgsConstructor(staticName = "instance")
public class BidRequestOrtbIdentityConverter implements BidRequestOrtbVersionConverter {

    @Override
    public OrtbVersion inVersion() {
        return null;
    }

    @Override
    public OrtbVersion outVersion() {
        return null;
    }

    @Override
    public BidRequest convert(BidRequest bidRequest) {
        return bidRequest;
    }
}
