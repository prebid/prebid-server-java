package org.prebid.server.auction.gpp.model;

import com.iab.gpp.encoder.GppModel;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.gpp.model.privacy.Privacy;
import org.prebid.server.auction.gpp.model.privacy.TcfEuV2Privacy;
import org.prebid.server.auction.gpp.model.privacy.UspV1Privacy;
import org.prebid.server.exception.PreBidException;

class GppContextUtils {

    private GppContextUtils() {
    }

    static GppModel gppModel(String gpp) {
        if (StringUtils.isEmpty(gpp)) {
            return null;
        }

        try {
            return new GppModel(gpp);
        } catch (Exception e) {
            throw new PreBidException("GPP string invalid: " + e.getMessage());
        }
    }

    static GppContext.Regions withPrivacy(GppContext.Regions regions, Privacy privacy) {
        final GppContext.Regions.RegionsBuilder regionsBuilder = regions.toBuilder();
        withPrivacy(regionsBuilder, privacy);
        return regionsBuilder.build();
    }

    static void withPrivacy(GppContext.Regions.RegionsBuilder regionsBuilder, Privacy privacy) {
        if (privacy == null) {
            throw new IllegalArgumentException("Privacy cannot be null");
        }

        if (privacy instanceof TcfEuV2Privacy tcfEuV2Privacy) {
            regionsBuilder.tcfEuV2Privacy(tcfEuV2Privacy);
        } else if (privacy instanceof UspV1Privacy uspV1Privacy) {
            regionsBuilder.uspV1Privacy(uspV1Privacy);
        }
    }
}
