package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredImp
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.Banner
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.Native
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.model.request.auction.RegsExt
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.request.vtrack.VtrackRequest
import org.prebid.server.functional.model.request.vtrack.xml.Vast
import org.prebid.server.functional.model.response.auction.Adm
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.CcpaConsent

import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.CompressionType.GZIP
import static org.prebid.server.functional.model.bidder.CompressionType.NONE
import static org.prebid.server.functional.model.request.auction.Asset.titleAsset
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.DOOH
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.model.response.auction.MediaType.AUDIO
import static org.prebid.server.functional.model.response.auction.MediaType.BANNER
import static org.prebid.server.functional.model.response.auction.MediaType.NATIVE
import static org.prebid.server.functional.model.response.auction.MediaType.VIDEO
import static org.prebid.server.functional.util.HttpUtil.CONTENT_ENCODING_HEADER
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED

class BidderParamsSpec extends BaseSpec {

    def "PBS should send request to bidder when adapter-defaults.enabled = #adapterDefault and adapters.BIDDER.enabled = #generic"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(adapterConfig)

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain httpcalls"
        assert response.ext?.debug?.httpcalls[GENERIC.value]

        and: "Response should not contain error"
        assert !response.ext?.errors

        where:
        adapterDefault | generic | adapterConfig
        "true"         | "true"  | ["adapter-defaults.enabled"        : adapterDefault,
                                    "adapters.audiencenetwork.enabled": "false",
                                    "adapters.generic.enabled"        : generic]

        "false"        | "true"  | ["adapter-defaults.enabled": adapterDefault,
                                    "adapters.generic.enabled": generic]
    }

    def "PBS should not send request to bidder and emit error when adapter-defaults.enabled = #adapterDefault and adapters.BIDDER.enabled = #generic"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(adapterConfig)

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain error"
        assert response.ext?.errors[ErrorType.GENERIC]*.code == [2]

        where:
        adapterDefault | generic | adapterConfig
        "false"        | "false" | ["adapter-defaults.enabled": adapterDefault,
                                    "adapters.generic.enabled": generic]
        "true"         | "false" | ["adapter-defaults.enabled"        : adapterDefault,
                                    "adapters.audiencenetwork.enabled": "false",
                                    "adapters.generic.enabled"        : generic]
    }

    def "PBS should modify vast xml when adapter-defaults.modifying-vast-xml-allowed = #adapterDefault and BIDDER.modifying-vast-xml-allowed = #generic"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(["adapter-defaults.modifying-vast-xml-allowed": adapterDefault,
                                                       "adapters.generic.modifying-vast-xml-allowed": generic])

        and: "Default vtrack request"
        String payload = PBSUtils.randomString
        def request = VtrackRequest.getDefaultVtrackRequest(encodeXml(Vast.getDefaultVastModel(payload)))
        def accountId = PBSUtils.randomNumber

        and: "Account in the DB"
        def account = new Account(uuid: accountId, eventsEnabled: true)
        accountDao.save(account)

        when: "PBS processes vtrack request"
        pbsService.sendVtrackRequest(request, accountId.toString())

        then: "vast xml is modified"
        def prebidCacheRequest = prebidCache.getXmlRecordedRequestsBody(payload)
        assert prebidCacheRequest.size() == 1
        assert prebidCacheRequest.first().contains("/event?t=imp&b=${request.puts[0].bidid}&a=$accountId&bidder=${request.puts[0].bidder}")

        where:
        adapterDefault | generic
        "true"         | "true"
        "false"        | "true"
    }

    def "PBS should not modify vast xml when adapter-defaults.modifying-vast-xml-allowed = #adapterDefault and BIDDER.modifying-vast-xml-allowed = #generic"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(["adapter-defaults.modifying-vast-xml-allowed": adapterDefault,
                                                       "adapters.generic.modifying-vast-xml-allowed": generic])

        and: "Default VtrackRequest"
        String payload = PBSUtils.randomString
        def request = VtrackRequest.getDefaultVtrackRequest(encodeXml(Vast.getDefaultVastModel(payload)))
        def accountId = PBSUtils.randomNumber

        and: "Account in the DB"
        def account = new Account(uuid: accountId, eventsEnabled: true)
        accountDao.save(account)

        when: "PBS processes vtrack request"
        pbsService.sendVtrackRequest(request, accountId.toString())

        then: "vast xml is not modified"
        def prebidCacheRequest = prebidCache.getXmlRecordedRequestsBody(payload)
        assert prebidCacheRequest.size() == 1
        assert !prebidCacheRequest.first().contains("/event?t=imp&b=${request.puts[0].bidid}&a=$accountId&bidder=${request.puts[0].bidder}")

        where:
        adapterDefault | generic
        "true"         | "false"
        "false"        | "false"
    }

    def "PBS should mask values when adapter-defaults.pbs-enforces-ccpa = #adapterDefault settings when BIDDER.pbs-enforces-ccpa = #generic"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(["adapter-defaults.pbs-enforces-ccpa": adapterDefault,
                                                       "adapters.generic.pbs-enforces-ccpa": generic])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        def validCcpa = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        bidRequest.regs.ext = new RegsExt(usPrivacy: validCcpa)
        def lat = PBSUtils.getRandomDecimal(0, 90)
        def lon = PBSUtils.getRandomDecimal(0, 90)
        bidRequest.device = new Device(geo: new Geo(lat: lat, lon: lon))

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo?.lat as BigDecimal == PBSUtils.roundDecimal(lat, 2)
        assert bidderRequests.device?.geo?.lon as BigDecimal == PBSUtils.roundDecimal(lon, 2)

        where:
        adapterDefault | generic
        "true"         | "true"
        "false"        | "true"
    }

    def "PBS should not mask values when adapter-defaults.pbs-enforces-ccpa = #adapterDefault settings when BIDDER.pbs-enforces-ccpa = #generic"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(["adapter-defaults.pbs-enforces-ccpa": adapterDefault,
                                                       "adapters.generic.pbs-enforces-ccpa": generic])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        def validCcpa = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        bidRequest.regs.ext = new RegsExt(usPrivacy: validCcpa)
        def lat = PBSUtils.getRandomDecimal(0, 90) as float
        def lon = PBSUtils.getRandomDecimal(0, 90) as float
        bidRequest.device = new Device(geo: new Geo(lat: lat, lon: lon))

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo?.lat == lat
        assert bidderRequests.device?.geo?.lon == lon

        where:
        adapterDefault | generic
        "true"         | "false"
        "false"        | "false"
    }

    def "PBS should prefer bidder params from imp[*].ext.prebid.bidder.BIDDER when ext.prebid.bidderparams.BIDDER is specified"() {
        given: "Default basic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        def firstParam = PBSUtils.randomNumber
        bidRequest.imp.first().ext.prebid.bidder.generic = new Generic(firstParam: firstParam)

        and: "Set bidderParam to bidRequest"
        bidRequest.ext.prebid.bidderParams = [(GENERIC): [firstParam: PBSUtils.randomNumber]]

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain zoneId value from imp[*].ext.prebid.bidder.BIDDER"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0]?.ext?.bidder?.firstParam == firstParam
    }

    def "PBS should send bidder params from imp[*].ext.prebid.bidder.BIDDER when ext.prebid.bidderparams.BIDDER isn't specified"() {
        given: "Default basic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        def firstParam = PBSUtils.randomNumber
        bidRequest.imp.first().ext.prebid.bidder.generic = new Generic(firstParam: firstParam)

        and: "Set bidderParam = null to bidRequest"
        bidRequest.ext.prebid.bidderParams = null

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain zoneId value from imp[*].ext.prebid.bidder.BIDDER"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0]?.ext?.bidder?.firstParam == firstParam
    }

    def "PBS should merge bidder params from imp[*].ext.prebid.bidder.BIDDER and ext.prebid.bidderparams.BIDDER"() {
        given: "Default basic BidRequest with zoneId = null"
        def bidRequest = BidRequest.defaultBidRequest
        def firstParam = PBSUtils.randomNumber
        bidRequest.imp.first().ext.prebid.bidder.generic = new Generic(firstParam: firstParam)

        and: "Set bidderParam to bidRequest"
        def secondParam = PBSUtils.randomNumber
        bidRequest.ext.prebid.bidderParams = [(GENERIC): [secondParam: secondParam]]

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should merge bidder params"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0]?.ext?.bidder?.firstParam == firstParam
        assert bidderRequest.imp[0]?.ext?.bidder?.secondParam == secondParam
    }

    def "PBS should only send bidder params from ext.prebid.bidderparams.BIDDER to specified bidder"() {
        given: "Default basic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        def firstParam = PBSUtils.randomNumber
        bidRequest.imp.first().ext.prebid.bidder.generic = new Generic(firstParam: firstParam)
        bidRequest.imp.first().ext.prebid.bidder.appNexus = null

        and: "Set bidderParam to bidRequest"
        bidRequest.ext.prebid.bidderParams = [(APPNEXUS): [placement_id: PBSUtils.randomNumber]]

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain bidder param from another bidder"
        bidder.getBidderRequest(bidRequest.id)
    }

    // TODO: create same test for enabled circuit breaker
    def "PBS should emit warning when bidder endpoint is invalid"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.enabled"           : "true",
                                                       "adapters.generic.endpoint"          : "https://",
                                                       "http-client.circuit-breaker.enabled": "false"])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain error"
        assert response.ext?.errors[ErrorType.GENERIC]*.code == [999]
        assert response.ext?.errors[ErrorType.GENERIC]*.message == ["host name must not be empty"]
    }

    def "PBS should reject bidder when bidder params from request doesn't satisfy json-schema for auction request"() {
        given: "BidRequest with bad bidder datatype"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp << Imp.defaultImpression
            imp[0].ext.prebid.bidder.generic.exampleProperty = PBSUtils.randomNumber
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should not fail the entire auction"
        assert response.seatbid[0].bid.size() == 1

        and: "PBS should call bidder"
        assert bidder.getRequestCount(bidRequest.id) == 1

        and: "Bidder with invalid params should be dropped"
        assert response.ext?.warnings[PREBID]*.code == [999, 999]
        assert response.ext?.warnings[PREBID]*.message ==
                ["WARNING: request.imp[0].ext.prebid.bidder.generic was dropped with a reason: " +
                         "request.imp[0].ext.prebid.bidder.generic failed validation.\n" +
                         "\$.exampleProperty: integer found, string expected",
                 "WARNING: request.imp[0].ext must contain at least one valid bidder"]
    }

    def "PBS should reject bidder when bidder params from stored request doesn't satisfy json-schema for auction request"() {
        given: "BidRequest with stored request, without imp"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomNumber)
            imp = null
        }

        and: "Default stored request with bad bidder datatype"
        def storedRequestModel = BidRequest.defaultStoredRequest.tap {
            imp[0].ext.prebid.bidder.generic.exampleProperty = PBSUtils.randomNumber
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(bidRequest, storedRequestModel)
        storedRequestDao.save(storedRequest)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder should be dropped"
        assert response.ext?.warnings[PREBID]*.code == [999, 999]
        assert response.ext?.warnings[PREBID]*.message ==
                ["WARNING: request.imp[0].ext.prebid.bidder.generic was dropped with a reason: " +
                         "request.imp[0].ext.prebid.bidder.generic failed validation.\n" +
                         "\$.exampleProperty: integer found, string expected",
                 "WARNING: request.imp[0].ext must contain at least one valid bidder"]

        and: "PBS should not call bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0

        and: "seatbid should be empty"
        assert response.seatbid.isEmpty()
    }

    def "PBS should reject bidder when bidder params from stored request doesn't satisfy json-schema for amp request"() {
        given: "AmpRequest with bad bidder datatype"
        def ampRequest = AmpRequest.defaultAmpRequest
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = ampRequest.account
            imp[0].ext.prebid.bidder.generic.exampleProperty = PBSUtils.randomNumber
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder should be dropped"
        assert response.ext?.warnings[PREBID]*.code == [999, 999]
        assert response.ext?.warnings[PREBID]*.message ==
                ["WARNING: request.imp[0].ext.prebid.bidder.generic was dropped with a reason: " +
                         "request.imp[0].ext.prebid.bidder.generic failed validation.\n" +
                         "\$.exampleProperty: integer found, string expected",
                 "WARNING: request.imp[0].ext must contain at least one valid bidder"]

        and: "PBS should not call bidder"
        assert bidder.getRequestCount(ampStoredRequest.id) == 0

        and: "targeting should be empty"
        assert response.targeting.isEmpty()
    }

    def "PBS should emit error when filter-imp-media-type = true and #configMediaType is empty in bidder config"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(
                ["auction.filter-imp-media-type.enabled"                     : "true",
                 ("adapters.generic.meta-info.${configMediaType}".toString()): ""])

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain empty seatbid"
        assert response.seatbid.isEmpty()

        and: "Response should contain error"
        assert response.ext?.warnings[ErrorType.GENERIC]*.code == [2]
        assert response.ext?.warnings[ErrorType.GENERIC]*.message == ["Bidder does not support any media types."]

        where:
        configMediaType    | bidRequest
        "app-media-types"  | BidRequest.getDefaultBidRequest(APP)
        "site-media-types" | BidRequest.getDefaultBidRequest(SITE)
    }

    def "PBS should not validate request when filter-imp-media-type = false and #configMediaType is empty in bidder config"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(
                ["auction.filter-imp-media-type.enabled"                     : "false",
                 ("adapters.generic.meta-info.${configMediaType}".toString()): ""])

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain seatbid"
        assert response.seatbid

        and: "Response should not contain error"
        assert !response.ext?.errors

        where:
        configMediaType    | bidRequest
        "app-media-types"  | BidRequest.getDefaultBidRequest(APP)
        "site-media-types" | BidRequest.getDefaultBidRequest(SITE)
    }

    def "PBS should emit error when filter-imp-media-type = true and request contains media type that is not configured in bidder config"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(
                ["auction.filter-imp-media-type.enabled"      : "true",
                 "adapters.generic.meta-info.site-media-types": "native"])

        and: "Default basic BidRequest with banner, native"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site = Site.defaultSite
            imp[0].banner = Banner.defaultBanner
            imp[0].nativeObj = Native.defaultNative
        }

        and: "Default basic bid with adm"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should remove not configured media type from bidder request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.imp[0]?.banner
        assert bidderRequest.imp[0]?.nativeObj

        and: "Response should not contain warnings"
        assert !response.ext?.warnings
    }

    def "PBS should not validate request when filter-imp-media-type = false and request contains only media type that is not configured in bidder config"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(
                ["auction.filter-imp-media-type.enabled"      : "false",
                 "adapters.generic.meta-info.site-media-types": "native"])

        and: "Default basic BidRequest with banner, native"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site = Site.defaultSite
            imp[0].banner = Banner.defaultBanner
            imp[0].nativeObj = Native.defaultNative
        }

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should not remove not configured media type from bidder request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0]?.banner
        assert bidderRequest.imp[0]?.nativeObj

        and: "Response should not contain error"
        assert !response.ext?.errors
    }

    def "PBS should emit error for request with multiple impressions when filter-imp-media-type = true, one of imp doesn't contain supported media type"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(
                ["auction.filter-imp-media-type.enabled"      : "true",
                 "adapters.generic.meta-info.site-media-types": "native,video"])

        and: "Default basic BidRequest with banner, native"
        def nativeImp = Imp.getDefaultImpression(NATIVE)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site = Site.defaultSite
            imp = [Imp.defaultImpression, nativeImp]
        }

        and: "Default basic bid with adm"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid = [Bid.getDefaultBid(nativeImp)]
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should remove banner imp from bidder request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.size() == 1
        assert !bidderRequest.imp[0].banner
        assert bidderRequest.imp[0].nativeObj

        and: "Response should contain error"
        assert response.ext?.warnings[ErrorType.GENERIC]*.code == [2]
        assert response.ext?.warnings[ErrorType.GENERIC]*.message ==
                ["Imp ${bidRequest.imp[0].id} does not have a supported media type and has been removed from the " +
                         "request for this bidder." as String]

        and: "seatbid should not be empty"
        assert !response.seatbid.isEmpty()
    }

    def "PBS auction should reject the bidder with media-type that is not supported by DOOH configuration with proper warning"() {
        given: "PBS service with configuration for dooh media-types"
        def pbsService = pbsServiceFactory.getService(
                ["auction.filter-imp-media-type.enabled"      : "true",
                 "adapters.generic.meta-info.dooh-media-types": mediaType])

        when: "Requesting PBS auction"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain proper warning"
        assert bidResponse.ext?.warnings[ErrorType.GENERIC]?.message.contains("Bid request contains 0 impressions after filtering.")

        and: "Bid response shouldn't contain any seatbid"
        assert !bidResponse.seatbid

        and: "Should't send any bidder request"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
        mediaType                       | bidRequest
        VIDEO.value                     | BidRequest.getDefaultBidRequest(DOOH)
        NATIVE.value                    | BidRequest.getDefaultBidRequest(DOOH)
        AUDIO.value                     | BidRequest.getDefaultBidRequest(DOOH)
        BANNER.value                    | BidRequest.getDefaultVideoRequest(DOOH)
        "${BANNER}, ${VIDEO}" as String | BidRequest.getDefaultBidRequest(DOOH).tap { imp[0] = Imp.getDefaultImpression(NATIVE) }
    }

    def "PBS auction should reject only imps with media-type that is not supported by DOOH configuration with proper warning"() {
        given: "PBS service with configuration for dooh media-types"
        def pbsService = pbsServiceFactory.getService(
                ["auction.filter-imp-media-type.enabled"      : "true",
                 "adapters.generic.meta-info.dooh-media-types": mediaType.value])

        and: "Default bid response with adm and nurl"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].adm = new Adm(assets: [titleAsset])
            seatbid[0].bid[0].nurl = PBSUtils.randomString
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain proper warning"
        assert response.ext?.warnings[ErrorType.GENERIC]?.message ==
                ["Imp ${bidRequest.imp[1].id} does not have a supported media type and has been removed from the request for this bidder." ]

        and: "Bid response should contain seatbid"
        assert response.seatbid

        and: "Should send bidder request with only proper imp"
        assert bidder.getBidderRequest(bidRequest.id).imp.id == [bidRequest.imp.first().id]

        where:
        mediaType | bidRequest
        BANNER    | BidRequest.getDefaultBidRequest(DOOH).tap { imp << Imp.getDefaultImpression(VIDEO) }
        VIDEO     | BidRequest.getDefaultVideoRequest(DOOH).tap { imp << Imp.getDefaultImpression(NATIVE) }
        AUDIO     | BidRequest.getDefaultAudioRequest(DOOH).tap { imp << Imp.getDefaultImpression(NATIVE) }
    }

    def "PBS should return empty seatBit when filter-imp-media-type = true, request.imp doesn't contain supported media type"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(
                ["auction.filter-imp-media-type.enabled"      : "true",
                 "adapters.generic.meta-info.site-media-types": "native,video"])

        and: "Default basic BidRequest with banner"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site = Site.defaultSite
            imp.first().banner = Banner.defaultBanner
        }

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should not call bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0

        and: "Response should contain errors"
        assert response.ext?.warnings[ErrorType.GENERIC]*.code == [2, 2]
        assert response.ext?.warnings[ErrorType.GENERIC]*.message ==
                ["Imp ${bidRequest.imp[0].id} does not have a supported media type and has been removed from " +
                         "the request for this bidder.",
                 "Bid request contains 0 impressions after filtering."]

        and: "seatbid should be empty"
        assert response.seatbid.isEmpty()
    }

    def "PBS should send server specific info to bidder when such is set in PBS config"() {
        given: "PBS with server info configuration"
        def serverDataCenter = PBSUtils.randomString
        def serverExternalUrl = "https://${PBSUtils.randomString}.com/"
        def serverHostVendorId = PBSUtils.randomNumber
        def pbsService = pbsServiceFactory.getService(["datacenter-region"  : serverDataCenter,
                                                       "external-url"       : serverExternalUrl as String,
                                                       "gdpr.host-vendor-id": serverHostVendorId as String])

        and: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS auction is requested"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS has sent server info to bidder during auction"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert bidderRequest?.ext?.prebid?.server?.externalUrl == serverExternalUrl
        assert bidderRequest.ext.prebid.server.datacenter == serverDataCenter
        assert bidderRequest.ext.prebid.server.gvlId == serverHostVendorId
    }

    def "PBS should request to bidder with header Content-Encoding = gzip when adapters.BIDDER.endpoint-compression = gzip"() {
        given: "PBS with adapter configuration"
        def compressionType = GZIP.value
        def pbsService = pbsServiceFactory.getService(["adapters.generic.enabled"             : "true",
                                                       "adapters.generic.endpoint-compression": compressionType])

        and: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain header Content-Encoding = gzip"
        assert response.ext?.debug?.httpcalls?.get(GENERIC.value)?.requestHeaders?.first()
                ?.get(CONTENT_ENCODING_HEADER)?.first() == compressionType
    }

    def "PBS should send request to bidder without header Content-Encoding when adapters.BIDDER.endpoint-compression = none"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.enabled"             : "true",
                                                       "adapters.generic.endpoint-compression": NONE.value])

        and: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should not contain header Content-Encoding"
        assert !response.ext?.debug?.httpcalls?.get(GENERIC.value)?.requestHeaders?.first()
                ?.get(CONTENT_ENCODING_HEADER)
    }

    def "PBS should not treat reserved imp[].ext.tid object as a bidder"() {
        given: "Default basic BidRequest with imp[].ext.tid object"
        def bidRequest = BidRequest.defaultBidRequest
        def tid = PBSUtils.getRandomString()
        bidRequest.imp.first().ext.tid = tid

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "imp[].ext.tid object should be passed to a bidder"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp?.first()?.ext?.tid == tid
    }

    def "PBS auction should populate imp[0].secure depend which value in imp stored request"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomString)
        }

        and: "Save storedImp into DB"
        def storedImp = StoredImp.getStoredImp(bidRequest).tap {
            impData = Imp.defaultImpression.tap {
                it.secure = secureStoredRequest
            }
        }
        storedImpDao.save(storedImp)

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain imp[0].secure same value as in request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].secure == secureBidderRequest

        where:
        secureStoredRequest | secureBidderRequest
        null                | 1
        1                   | 1
        0                   | 0
    }

    def "PBS auction should populate imp[0].secure depend which value in imp request"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].secure = secureRequest
        }

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain imp[0].secure same value as in request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].secure == secureBidderRequest

        where:
        secureRequest | secureBidderRequest
        null          | 1
        1             | 1
        0             | 0
    }
}
