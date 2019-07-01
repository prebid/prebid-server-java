package org.prebid.server.bidder.sharethrough.model.bidResponse;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpSharethroughPlacementThirdPartyPartner {
    String key;
    String tag;
}
