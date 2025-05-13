package org.prebid.server.bidder.adoppler.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class AdopplerResponseAdsExt {

    AdopplerResponseVideoAdsExt video;
}
