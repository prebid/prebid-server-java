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
class Ortb2BlockingAttributeConfig {

    Boolean enforceBlocks

    Object blockedAdomain
    Object blockedApp
    Object blockedBannerAttr
    Object blockedAdvCat
    Object blockedBannerType

    Object blockUnknownAdomain
    Object blockUnknownAdvCat

    Object allowedAdomainForDeals
    Object allowedAppForDeals
    Object allowedBannerAttrForDeals
    Object allowedAdvCatForDeals

    Ortb2BlockingActionOverride actionOverrides

    static getDefaultOrtb2BlockingAttributeConfig(Object ortb2Attributes, Ortb2BlockingAttribute attributeName, Object ortb2AttributesForDeals = null) {
        new Ortb2BlockingAttributeConfig().tap {
            enforceBlocks = false
            switch (attributeName) {
                case BADV:
                    blockedAdomain = ortb2Attributes
                    allowedAdomainForDeals = ortb2AttributesForDeals
                    break
                case BAPP:
                    blockedApp = ortb2Attributes
                    allowedAppForDeals = ortb2AttributesForDeals
                    break
                case BATTR:
                    blockedBannerAttr = ortb2Attributes
                    allowedBannerAttrForDeals = ortb2AttributesForDeals
                    break
                case BCAT:
                    blockedAdvCat = ortb2Attributes
                    allowedAdvCatForDeals = ortb2AttributesForDeals
                    break
                case BTYPE:
                    blockedBannerType = ortb2Attributes
                    break
                default:
                    throw new IllegalArgumentException("Unknown attribute type: $attributeName")
            }
        }
    }

}
