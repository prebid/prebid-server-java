package org.prebid.server.bidder.pubmatic.model.request;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.pubmatic.ExtImpPubmatic;

@Value(staticConstructor = "of")
public class PubmaticBidderImpExt {

    ExtImpPubmatic bidder;

    PubmaticExtData data;

    Integer ae;

}
