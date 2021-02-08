package org.prebid.server.settings.bidder;

import lombok.Value;
import org.apache.commons.lang3.BooleanUtils;

import java.util.List;

@Value
public class BidderInfo {

    boolean enabled;

    MaintainerInfo maintainer;

    CapabilitiesInfo capabilities;

    List<String> vendors;

    GdprInfo gdpr;

    boolean ccpaEnforced;

    boolean modifyingVastXmlAllowed;

    public static BidderInfo create(boolean enabled,
                                    String maintainerEmail,
                                    Boolean validateMediaTypes,
                                    List<String> appMediaTypes,
                                    List<String> siteMediaTypes,
                                    List<String> supportedVendors,
                                    int vendorId,
                                    boolean enforceGdpr,
                                    boolean ccpaEnforced,
                                    boolean modifyingVastXmlAllowed) {
        final MaintainerInfo maintainer = new MaintainerInfo(maintainerEmail);
        final CapabilitiesInfo capabilities = new CapabilitiesInfo(
                BooleanUtils.isNotFalse(validateMediaTypes),
                PlatformInfo.create(appMediaTypes),
                PlatformInfo.create(siteMediaTypes));
        final GdprInfo gdpr = new GdprInfo(vendorId, enforceGdpr);

        return new BidderInfo(
                enabled, maintainer, capabilities, supportedVendors, gdpr, ccpaEnforced, modifyingVastXmlAllowed);
    }
}
