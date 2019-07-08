package org.prebid.server.bidder.sharethrough.model.bidResponse;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
class ExtImpSharethroughPlacementThirdPartyPartner {

    String key;

    String tag;
}

