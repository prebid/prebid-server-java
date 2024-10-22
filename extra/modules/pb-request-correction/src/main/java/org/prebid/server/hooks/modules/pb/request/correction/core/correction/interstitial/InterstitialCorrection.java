package org.prebid.server.hooks.modules.pb.request.correction.core.correction.interstitial;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.prebid.server.hooks.modules.pb.request.correction.core.config.model.Config;
import org.prebid.server.hooks.modules.pb.request.correction.core.correction.Correction;

public class InterstitialCorrection implements Correction {

    @Override
    public BidRequest apply(BidRequest bidRequest) {
        return bidRequest.toBuilder()
                .imp(bidRequest.getImp().stream()
                        .map(InterstitialCorrection::removeInterstitial)
                        .toList())
                .build();
    }

    private static Imp removeInterstitial(Imp imp) {
        final Integer interstitial = imp.getInstl();
        return interstitial != null && interstitial == 1
                ? imp.toBuilder().instl(null).build()
                : imp;
    }
}
