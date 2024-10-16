package org.prebid.server.functional.model.config

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.AUDIO_BATTR
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BADV
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BAPP
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BANNER_BATTR
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BCAT
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.BTYPE
import static org.prebid.server.functional.model.config.Ortb2BlockingAttribute.VIDEO_BATTR

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class Ortb2BlockingActionOverride {

    List<Ortb2BlockingOverride> enforceBlocks
    List<Ortb2BlockingOverride> blockedAdomain
    List<Ortb2BlockingOverride> blockedApp
    List<Ortb2BlockingOverride> blockedBannerAttr
    List<Ortb2BlockingOverride> blockedVideoAttr
    List<Ortb2BlockingOverride> blockedAudioAttr
    List<Ortb2BlockingOverride> blockedAdvCat
    List<Ortb2BlockingOverride> blockedBannerType

    List<Ortb2BlockingOverride> blockUnknownAdomain
    List<Ortb2BlockingOverride> blockUnknownAdvCat

    List<Ortb2BlockingOverride> allowedAdomainForDeals
    List<Ortb2BlockingOverride> allowedAppForDeals
    List<Ortb2BlockingOverride> allowedBannerAttrForDeals
    List<Ortb2BlockingOverride> allowedVideoAttrForDeals
    List<Ortb2BlockingOverride> allowedAudioAttrForDeals
    List<Ortb2BlockingOverride> allowedAdvCatForDeals

    static Ortb2BlockingActionOverride getDefaultOverride(Ortb2BlockingAttribute attribute,
                                                          List<Ortb2BlockingOverride> blocked,
                                                          List<Ortb2BlockingOverride> allowedForDeals = null) {

        new Ortb2BlockingActionOverride().tap {
            switch (attribute) {
                case BADV:
                    blockedAdomain = blocked
                    allowedAdomainForDeals = allowedForDeals
                    break
                case BAPP:
                    blockedApp = blocked
                    allowedAppForDeals = allowedForDeals
                    break
                case BANNER_BATTR:
                    blockedBannerAttr = blocked
                    allowedBannerAttrForDeals = allowedForDeals
                    break
                case VIDEO_BATTR:
                    blockedVideoAttr = blocked
                    allowedVideoAttrForDeals = allowedForDeals
                    break
                case AUDIO_BATTR:
                    blockedAudioAttr = blocked
                    allowedAudioAttrForDeals = allowedForDeals
                    break
                case BCAT:
                    blockedAdvCat = blocked
                    allowedAdvCatForDeals = allowedForDeals
                    break
                case BTYPE:
                    blockedBannerType = blocked
                    break
                default:
                    throw new IllegalArgumentException("Unknown attribute type: $attribute")
            }
        }
    }
}
