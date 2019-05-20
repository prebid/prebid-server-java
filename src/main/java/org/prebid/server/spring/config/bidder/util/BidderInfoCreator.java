package org.prebid.server.spring.config.bidder.util;

import org.prebid.server.proto.response.BidderInfo;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.MetaInfo;

public class BidderInfoCreator {

    private BidderInfoCreator() {

    }

    public static BidderInfo create(BidderConfigurationProperties configurationProperties) {
        final MetaInfo metaInfo = configurationProperties.getMetaInfo();
        return BidderInfo.create(
                configurationProperties.getEnabled(),
                metaInfo.getMaintainerEmail(),
                metaInfo.getAppMediaTypes(),
                metaInfo.getSiteMediaTypes(),
                metaInfo.getSupportedVendors(),
                metaInfo.getVendorId(),
                configurationProperties.getPbsEnforcesGdpr());
    }
}
