package org.prebid.server.bidder.adnuntius.model.util;

import lombok.Value;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusAdsUnit;

@Value(staticConstructor = "of")
public class AdsUnitWithImpId {

    AdnuntiusAdsUnit adsUnit;

    String impId;
}
