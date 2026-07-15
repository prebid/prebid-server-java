package org.prebid.server.bidder.elementaltv.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class ElementalTVResponseAdsExt {

    ElementalTVResponseVideoAdsExt video;
}
