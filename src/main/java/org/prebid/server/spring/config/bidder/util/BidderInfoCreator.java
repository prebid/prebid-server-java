package org.prebid.server.spring.config.bidder.util;

import org.prebid.server.proto.response.BidderInfo;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.DefaultBidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.MetaInfo;

public class BidderInfoCreator {

    private BidderInfoCreator() {

    }

    public static BidderInfo create(BidderConfigurationProperties bidderConfigurationProperties,
                                    DefaultBidderConfigurationProperties defaultConfigurationProperties) {
        return create(bidderConfigurationProperties, defaultConfigurationProperties, null);
    }

    public static BidderInfo create(BidderConfigurationProperties bidderConfigurationProperties,
                                    DefaultBidderConfigurationProperties defaultConfigurationProperties,
                                    String aliasOf) {
        final MetaInfo metaInfo = bidderConfigurationProperties.getMetaInfo();
        replaceEmptyConfigurationPropertiesWithDefault(bidderConfigurationProperties, defaultConfigurationProperties);
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

    private static void replaceEmptyConfigurationPropertiesWithDefault(
            BidderConfigurationProperties bidderConfigurationProperties,
            DefaultBidderConfigurationProperties defaultBidderConfigurationProperties) {
        if (bidderConfigurationProperties.getEnabled() == null) {
            bidderConfigurationProperties.setEnabled(defaultBidderConfigurationProperties.getEnabled());
        }
        if (bidderConfigurationProperties.getPbsEnforcesGdpr() == null) {
            bidderConfigurationProperties.setPbsEnforcesGdpr(defaultBidderConfigurationProperties.getPbsEnforcesGdpr());
        }
        if (bidderConfigurationProperties.getPbsEnforcesCcpa() == null) {
            bidderConfigurationProperties.setPbsEnforcesCcpa(defaultBidderConfigurationProperties.getPbsEnforcesCcpa());
        }
        if (bidderConfigurationProperties.getModifyingVastXmlAllowed() == null) {
            bidderConfigurationProperties.setModifyingVastXmlAllowed(
                    defaultBidderConfigurationProperties.getModifyingVastXmlAllowed());
        }
        if (bidderConfigurationProperties.getDeprecatedNames() == null) {
            bidderConfigurationProperties.setDeprecatedNames(defaultBidderConfigurationProperties.getDeprecatedNames());
        }
        if (bidderConfigurationProperties.getAliases() == null) {
            bidderConfigurationProperties.setAliases(defaultBidderConfigurationProperties.getAliases());
        }
    }

}
