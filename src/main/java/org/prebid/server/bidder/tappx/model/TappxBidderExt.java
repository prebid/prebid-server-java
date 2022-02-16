package org.prebid.server.bidder.tappx.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class TappxBidderExt {

    String tappxkey;

    String mktag;

    List<String> bcid;

    List<String> bcrid;
}
