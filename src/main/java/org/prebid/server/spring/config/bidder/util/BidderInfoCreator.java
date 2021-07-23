package org.prebid.server.spring.config.bidder.util;

import org.prebid.server.proto.response.BidderInfo;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.DefaultBidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.MetaInfo;

public class BidderInfoCreator {

    private BidderInfoCreator() {

    }

    public static BidderInfo create(BidderConfigurationProperties bidderConfigurationProperties) {
        return create(bidderConfigurationProperties, null);
    }

    public static BidderInfo create(BidderConfigurationProperties bidderConfigurationProperties,
                                    String aliasOf) {
        final MetaInfo metaInfo = bidderConfigurationProperties.getMetaInfo();
        return BidderInfo.create(
                bidderConfigurationProperties.getEnabled(),
                aliasOf,
                metaInfo.getMaintainerEmail(),
                metaInfo.getAppMediaTypes(),
                metaInfo.getSiteMediaTypes(),
                metaInfo.getSupportedVendors(),
                metaInfo.getVendorId(),
                bidderConfigurationProperties.getPbsEnforcesGdpr(),
                bidderConfigurationProperties.getPbsEnforcesCcpa(),
                bidderConfigurationProperties.getModifyingVastXmlAllowed());
    }

}
