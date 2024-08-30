package org.prebid.server.functional.model.config

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BADV
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BAPP
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BATTR
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BCAT
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BTYPE

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class Ortb2BlockingActionOverride {

    List<Ortb2BlockingOverride> enforceBlocks
    List<Ortb2BlockingOverride> blockedAdomain
    List<Ortb2BlockingOverride> blockedApp
    List<Ortb2BlockingOverride> blockedBannerAttr
    List<Ortb2BlockingOverride> blockedAdvCat
    List<Ortb2BlockingOverride> blockedBannerType

    List<Ortb2BlockingOverride> blockUnknownAdomain
    List<Ortb2BlockingOverride> blockUnknownAdvCat

    List<Ortb2BlockingOverride> allowedAdomainForDeals
    List<Ortb2BlockingOverride> allowedAppForDeals
    List<Ortb2BlockingOverride> allowedBannerAttrForDeals
    List<Ortb2BlockingOverride> allowedAdvCatForDeals

    static Ortb2BlockingActionOverride getDefaultOrtb2BlockingActionOverride(Ortb2BlockingAttribute attribute,
                                                                             Ortb2BlockingOverride blocked,
                                                                             Ortb2BlockingOverride allowedForDeals = null) {

        new Ortb2BlockingActionOverride().tap {
            switch (attribute) {
                case BADV:
                    blockedAdomain = blocked ? [blocked] : null
                    allowedAdomainForDeals = allowedForDeals ? [allowedForDeals] : null
                    break
                case BAPP:
                    blockedApp = blocked ? [blocked] : null
                    allowedAppForDeals = allowedForDeals ? [allowedForDeals] : null
                    break
                case BATTR:
                    blockedBannerAttr = blocked ? [blocked] : null
                    allowedBannerAttrForDeals = allowedForDeals ? [allowedForDeals] : null
                    break
                case BCAT:
                    blockedAdvCat = blocked ? [blocked] : null
                    allowedAdvCatForDeals = allowedForDeals ? [allowedForDeals] : null
                    break
                case BTYPE:
                    blockedBannerType = blocked ? [blocked] : null
                    break
                default:
                    throw new IllegalArgumentException("Unknown attribute type: $attribute")
            }
        }
    }
}
