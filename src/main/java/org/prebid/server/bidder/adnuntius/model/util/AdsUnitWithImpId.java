package org.prebid.server.bidder.adnuntius.model.util;

import com.iab.openrtb.request.Imp;
import lombok.Value;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusAdsUnit;
import org.prebid.server.proto.openrtb.ext.request.adnuntius.ExtImpAdnuntius;

@Value(staticConstructor = "of")
public class AdsUnitWithImpId {

    AdnuntiusAdsUnit adsUnit;

    Imp imp;

    ExtImpAdnuntius extImpAdnuntius;
}
