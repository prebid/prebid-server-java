package org.prebid.server.settings.bidder;

import lombok.Value;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Value(staticConstructor = "of")
public class BidderInfo {

    boolean enabled;

    boolean usesHttps;

    String aliasOf;

    MaintainerInfo maintainer;

    CapabilitiesInfo capabilities;

    List<String> vendors;

    GdprInfo gdpr;

    boolean ccpaEnforced;

    boolean modifyingVastXmlAllowed;

    public static BidderInfo create(boolean enabled,
                                    String endpoint,
                                    String aliasOf,
                                    String maintainerEmail,
                                    Boolean validateMediaTypes,
                                    List<String> appMediaTypes,
                                    List<String> siteMediaTypes,
                                    List<String> supportedVendors,
                                    int vendorId,
                                    boolean enforceGdpr,
                                    boolean ccpaEnforced,
                                    boolean modifyingVastXmlAllowed) {

        final CapabilitiesInfo capabilities = new CapabilitiesInfo(
                BooleanUtils.isNotFalse(validateMediaTypes),
                PlatformInfo.create(appMediaTypes),
                PlatformInfo.create(siteMediaTypes));

        return of(
                enabled,
                StringUtils.startsWith(endpoint, "https://"),
                aliasOf,
                new MaintainerInfo(maintainerEmail),
                capabilities,
                supportedVendors,
                new GdprInfo(vendorId, enforceGdpr),
                ccpaEnforced,
                modifyingVastXmlAllowed);
    }
}
