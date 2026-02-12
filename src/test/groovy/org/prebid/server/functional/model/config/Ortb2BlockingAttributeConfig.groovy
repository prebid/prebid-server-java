package org.prebid.server.functional.model.config

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.AUDIO_BATTR
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BADV
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BANNER_BATTR
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BAPP
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BCAT
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BTYPE
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.VIDEO_BATTR

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class Ortb2BlockingAttributeConfig {

    Boolean enforceBlocks

    Object blockedAdomain
    Object blockedApp
    Object blockedBannerAttr
    Object blockedVideoAttr
    Object blockedAudioAttr
    Object blockedAdvCat
    Object blockedBannerType

    Object blockUnknownAdomain
    Object blockUnknownAdvCat

    Object allowedAdomainForDeals
    Object allowedAppForDeals
    Object allowedBannerAttrForDeals
    Object allowedVideoAttrForDeals
    Object allowedAudioAttrForDeals
    Object allowedAdvCatForDeals

    Ortb2BlockingActionOverride actionOverrides

    static getDefaultConfig(Object ortb2Attributes, Ortb2BlockingAttribute attributeName, Object ortb2AttributesForDeals = null) {
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
                case BANNER_BATTR:
                    blockedBannerAttr = ortb2Attributes
                    allowedBannerAttrForDeals = ortb2AttributesForDeals
                    break
                case VIDEO_BATTR:
                    blockedVideoAttr = ortb2Attributes
                    allowedVideoAttrForDeals = ortb2AttributesForDeals
                    break
                case AUDIO_BATTR:
                    blockedAudioAttr = ortb2Attributes
                    allowedAudioAttrForDeals = ortb2AttributesForDeals
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
