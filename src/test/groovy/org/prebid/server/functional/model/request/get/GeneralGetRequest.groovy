package org.prebid.server.functional.model.request.get

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.prebid.server.functional.model.request.amp.ConsentType
import org.prebid.server.functional.model.request.auction.DebugCondition
import org.prebid.server.functional.model.request.auction.DeviceType
import org.prebid.server.functional.model.request.auction.VideoPlacementSubtypes
import org.prebid.server.functional.model.request.auction.VideoPlcmtSubtype
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

    DebugCondition debug

    @JsonProperty("of")
    String outputFormat

    @JsonProperty("om")
    String outputModule

    @JsonProperty("rprof")
    @JsonSerialize(using = CommaSeparatedListSerializer)
    List<String> requestProfiles

    @JsonProperty("iprof")
    @JsonSerialize(using = CommaSeparatedListSerializer)
    List<String> impProfiles

    @JsonProperty("sarid")
    String storedAuctionResponseId

    List<String> mimes

    @JsonProperty("w")
    Integer width

    @JsonProperty("h")
    Integer height

    @JsonProperty("ow")
    Integer overrideWidth

    @JsonProperty("oh")
    Integer overrideHeight

    Object sizes

    @JsonProperty("ms")
    Object sizesLegacy

    String slot

    @JsonProperty("mindur")
    Integer minDuration

    @JsonProperty("maxdur")
    Integer maxDuration

    @JsonSerialize(using = CommaSeparatedListSerializer)
    List<Integer> api

    @JsonProperty("battr")
    @JsonSerialize(using = CommaSeparatedListSerializer)
    List<Integer> blockAttributes

    @JsonSerialize(using = CommaSeparatedListSerializer)
    List<Integer> delivery

    Integer linearity

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
    @JsonSerialize(using = CommaSeparatedListSerializer)
    List<Integer> protocols

    @JsonProperty("rqddurs")
    @JsonSerialize(using = CommaSeparatedListSerializer)
    List<Integer> requiredDurations

    @JsonProperty("seq")
    Integer sequence

    @JsonProperty("slotinpod")
    Integer slotInPod

    @JsonProperty("startdelay")
    Integer startDelay

    Integer skip

    @JsonProperty("skipafter")
    Integer skipAfter

    @JsonProperty("skipmin")
    Integer skipMin

    @JsonProperty("pos")
    Integer position

    @JsonProperty("stitched")
    Integer stitched

    Integer feed

    @JsonProperty("nvol")
    Integer normalizedVolume

    VideoPlacementSubtypes placement

    @JsonProperty("plcmt")
    VideoPlcmtSubtype placementSubtype

    @JsonProperty("playbackend")
    Integer playbackEnd

    @JsonProperty("playbackmethod")
    @JsonSerialize(using = CommaSeparatedListSerializer)
    List<Integer> playbackMethods

    @JsonProperty("boxingallowed")
    Integer boxingAllowed

    @JsonProperty("btype")
    @JsonSerialize(using = CommaSeparatedListSerializer)
    List<Integer> bannerTypes

    @JsonProperty("expdir")
    @JsonSerialize(using = CommaSeparatedListSerializer)
    List<Integer> expandableDirections

    @JsonProperty("topframe")
    Integer topFrame

    String targeting

    @JsonProperty("tcfc")
    String consent

    @JsonProperty("gdpr_consent")
    String consentLegacy

    @JsonProperty("consent_string")
    String consentStringLegacy

    Integer gdpr

    @JsonProperty("privacy")
    Integer gdprPrivacy

    @JsonProperty("gdpr_applies")
    String gdprApplies

    @JsonProperty("usp")
    String usPrivacy

    @JsonProperty("addtl_consent")
    String additionalConsent

    @JsonProperty("consent_type")
    ConsentType consentType

    @JsonProperty("gpp_sid")
    @JsonSerialize(using = CommaSeparatedListSerializer)
    List<Integer> gppSid

    Integer coppa

    @JsonProperty("gpc")
    Integer globalPrivacyControl

    @JsonProperty("dnt")
    Integer doNotTrack

    @JsonProperty("lmt")
    Integer limitAdTracking

    @JsonProperty("bcat")
    @JsonSerialize(using = CommaSeparatedListSerializer)
    List<String> blockedCategories

    @JsonProperty("badv")
    @JsonSerialize(using = CommaSeparatedListSerializer)
    List<String> blockedAdvertisers

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
    Integer contentCategoryTaxonomy

    @JsonProperty("cseries")
    String contentSeries

    @JsonProperty("rss_feed")
    String contentSeriesAlias

    @JsonProperty("ctitle")
    String contentTitle

    @JsonProperty("curl")
    String contentUrl

    @JsonProperty("clivestream")
    Integer contentLivestream

    @JsonProperty("ip")
    String deviceIp

    @JsonProperty("ua")
    String deviceUa

    @JsonProperty("dtype")
    DeviceType deviceType

    @JsonProperty("ifa")
    String deviceIfa

    @JsonProperty("ifat")
    String deviceIfaType

    String unknown

    @JsonProperty("unknown_alias")
    String unknownAlias

    static GeneralGetRequest getDefault(String storedRequestId = PBSUtils.randomNumber) {
        new GeneralGetRequest(storedRequestId: storedRequestId, debug: ENABLED)
    }

    String resolveStoredRequestId() {
        storedRequestId ?: storedRequestIdLegacy
    }

    String resolveAccountId() {
        accountId ?: accountIdLegacy
    }
}
