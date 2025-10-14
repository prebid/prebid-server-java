package org.prebid.server.functional.model.request.get

import com.fasterxml.jackson.annotation.JsonProperty
import org.prebid.server.functional.model.request.amp.ConsentType
import org.prebid.server.functional.model.request.auction.DebugCondition
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.request.auction.DebugCondition.ENABLED

class GeneralGetRequest {

    @JsonProperty("srid")
    String storedRequestId

    @JsonProperty("tag_id")
    String storedRequestIdLegacy

    @JsonProperty("pubid")
    String accountId

    @JsonProperty("account")
    String accountIdLegacy

    @JsonProperty("tmax")
    Integer timeoutMax

    @JsonProperty("debug")
    DebugCondition debug

    @JsonProperty("of")
    String outputFormat

    @JsonProperty("om")
    String outputModule

    @JsonProperty("rprof")
    List<String> requestProfiles

    @JsonProperty("iprof")
    List<String> impProfiles

    @JsonProperty("sarid")
    String storedAuctionResponseId

    @JsonProperty("mimes")
    List<String> mimes

    @JsonProperty("w")
    Integer width

    @JsonProperty("h")
    Integer height

    @JsonProperty("ow")
    Integer originalWidth

    @JsonProperty("oh")
    Integer originalHeight

    @JsonProperty("sizes")
    Object sizes

    @JsonProperty("ms")
    Object sizesLegacy

    @JsonProperty("slot")
    String slot

    @JsonProperty("mindur")
    Integer minDuration

    @JsonProperty("maxdur")
    Integer maxDuration

    @JsonProperty("api")
    List<Integer> api

    @JsonProperty("battr")
    List<Integer> battr

    @JsonProperty("delivery")
    List<Integer> delivery

    @JsonProperty("linearity")
    Integer linearityMode

    @JsonProperty("minbr")
    Integer minBitrate

    @JsonProperty("maxbr")
    Integer maxBitrate

    @JsonProperty("maxex")
    Integer maxExtended

    @JsonProperty("maxseq")
    Integer maxSequence

    @JsonProperty("mincpms")
    Integer minCpmPerSec

    @JsonProperty("poddur")
    Integer podDuration

    @JsonProperty("podid")
    Integer podId

    @JsonProperty("podseq")
    Integer podSequence

    @JsonProperty("proto")
    List<Integer> proto

    @JsonProperty("rqddurs")
    List<Integer> requiredDurations

    @JsonProperty("seq")
    Integer sequence

    @JsonProperty("slotinpod")
    Integer slotInPod

    @JsonProperty("startdelay")
    Integer startDelay

    @JsonProperty("skip")
    Integer skip

    @JsonProperty("skipafter")
    Integer skipAfter

    @JsonProperty("skipmin")
    Integer skipMin

    @JsonProperty("pos")
    Integer position

    @JsonProperty("stitched")
    Integer stitched

    @JsonProperty("feed")
    Integer feed

    @JsonProperty("nvol")
    Integer normalizedVolume

    @JsonProperty("placement")
    Integer placement

    @JsonProperty("plcmt")
    Integer placementSubtype

    @JsonProperty("playbackend")
    Integer playbackEndMode

    @JsonProperty("playbackmethod")
    List<Integer> playbackMethods

    @JsonProperty("boxingallowed")
    Integer boxingAllowed

    @JsonProperty("btype")
    List<Integer> bannerTypes

    @JsonProperty("expdir")
    List<Integer> expandableDirections

    @JsonProperty("topframe")
    Integer topFrame

    @JsonProperty("targeting")
    String targeting

    @JsonProperty("consent")
    String gppConsent

    @JsonProperty("gdpr_consent")
    String gppConsentLegacy

    @JsonProperty("consent_string")
    String gppConsentStringLegacy

    @JsonProperty("gdpr")
    Integer gdpr

    @JsonProperty("privacy")
    Integer privacy

    @JsonProperty("gdpr_applies")
    String gdprApplies

    @JsonProperty("usp")
    String usPrivacy

    @JsonProperty("addtl_consent")
    String additionalConsent

    @JsonProperty("consent_type")
    ConsentType consentType

    @JsonProperty("gpp_sid")
    Integer gppSid

    @JsonProperty("coppa")
    Integer coppaFlag

    @JsonProperty("gpc")
    Integer globalPrivacyControl

    @JsonProperty("dnt")
    Integer doNotTrack

    @JsonProperty("lmt")
    Integer limitAdTracking

    @JsonProperty("bcat")
    List<String> blockedCategories

    @JsonProperty("badv")
    List<String> blockedAdvertisers

    @JsonProperty("page")
    String page

    @JsonProperty("bundle")
    String appBundle

    @JsonProperty("name")
    String appName

    @JsonProperty("storeurl")
    String storeUrl

    @JsonProperty("cgenre")
    String contentGenre

    @JsonProperty("clang")
    String contentLanguage

    @JsonProperty("crating")
    String contentRating

    @JsonProperty("ccat")
    Integer contentCategory

    @JsonProperty("ccattax")
    List<String> contentCategoryTaxonomy

    @JsonProperty("cseries")
    String contentSeries

    @JsonProperty("rss_feed")
    String contentSeriesAlias

    @JsonProperty("ctitle")
    String contentTitle

    @JsonProperty("curl")
    String contentUrl

    @JsonProperty("clivestream")
    String contentLivestream

    @JsonProperty("ip")
    String deviceIp

    @JsonProperty("ua")
    String deviceUa

    @JsonProperty("dtype")
    String deviceType

    @JsonProperty("ifa")
    String deviceIfa

    @JsonProperty("ifat")
    String deviceIfaType

    @JsonProperty("unknown")
    String unknown

    @JsonProperty("unknown_alias")
    String unknownAlias

    static GeneralGetRequest getDefault(String storedRequestId = PBSUtils.randomNumber) {
        new GeneralGetRequest(storedRequestId: storedRequestId, debug: ENABLED)
    }

    String resolveStoredRequestId() {
        storedRequestId != null ? storedRequestId : storedRequestIdLegacy
    }
}
