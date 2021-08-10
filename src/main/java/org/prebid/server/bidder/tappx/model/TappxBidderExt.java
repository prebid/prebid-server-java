package org.prebid.server.bidder.tappx.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@AllArgsConstructor(staticName = "of")
public class TappxBidderExt {

    String tappxkey;

    String mktag;

    List<String> bcid;

    List<String> bcrid;
}
