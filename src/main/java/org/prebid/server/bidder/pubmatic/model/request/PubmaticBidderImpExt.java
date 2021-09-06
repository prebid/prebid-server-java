package org.prebid.server.bidder.pubmatic.model.request;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.pubmatic.ExtImpPubmatic;

@AllArgsConstructor(staticName = "of")
@Value
public class PubmaticBidderImpExt {

    ExtImpPubmatic bidder;

    PubmaticExtData data;
}
