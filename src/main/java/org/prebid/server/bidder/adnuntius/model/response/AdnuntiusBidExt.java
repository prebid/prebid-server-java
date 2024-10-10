package org.prebid.server.bidder.adnuntius.model.response;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.response.ExtBidDsa;

@Value(staticConstructor = "of")
public class AdnuntiusBidExt {

    ExtBidDsa dsa;
}
