package org.prebid.server.bidder.sovrn;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Collections;

public class SovrnMetaInfo implements MetaInfo {

    @Override
    public BidderInfo info() {
        return BidderInfo.create("sovrnoss@sovrn.com",
                Collections.singletonList("banner"),
                Collections.singletonList("banner"), null);
    }
}
