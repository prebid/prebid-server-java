package org.prebid.server.bidder.pubmatic.proto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.bidder.pubmatic.model.PubmaticExtData;
import org.prebid.server.proto.openrtb.ext.request.pubmatic.ExtImpPubmatic;

@AllArgsConstructor(staticName = "of")
@Value
public class PubmaticImpExt {

    ExtImpPubmatic bidder;

    PubmaticExtData data;
}
