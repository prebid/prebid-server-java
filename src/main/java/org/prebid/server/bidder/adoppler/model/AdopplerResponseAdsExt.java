package org.prebid.server.bidder.adoppler.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class AdopplerResponseAdsExt {

    AdopplerResponseVideoAdsExt video;
}
