package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.AppNexus
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredImp
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.Adrino
import org.prebid.server.functional.model.request.auction.Amx
import org.prebid.server.functional.model.request.auction.AuctionEnvironment
import org.prebid.server.functional.model.request.auction.Banner
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.AnyUnsupportedBidder
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.ImpExt
import org.prebid.server.functional.model.request.auction.ImpExtContext
import org.prebid.server.functional.model.request.auction.ImpExtContextData
import org.prebid.server.functional.model.request.auction.InterestGroupAuctionSupport
import org.prebid.server.functional.model.request.auction.Native
import org.prebid.server.functional.model.request.auction.PrebidOptions
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.request.auction.Source
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.request.vtrack.VtrackRequest
import org.prebid.server.functional.model.request.vtrack.xml.Vast
import org.prebid.server.functional.model.response.auction.Adm
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidExt
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.CcpaConsent

import static org.prebid.server.functional.model.Currency.CHF
import static org.prebid.server.functional.model.Currency.EUR
import static org.prebid.server.functional.model.Currency.JPY
import static org.prebid.server.functional.model.Currency.USD
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS_UPPER_CASE
import static org.prebid.server.functional.model.bidder.BidderName.AMX
import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC_CAMEL_CASE
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.CompressionType.GZIP
import static org.prebid.server.functional.model.bidder.CompressionType.NONE
import static org.prebid.server.functional.model.request.auction.Asset.titleAsset
import static org.prebid.server.functional.model.request.auction.AuctionEnvironment.DEVICE_ORCHESTRATED
import static org.prebid.server.functional.model.request.auction.AuctionEnvironment.NOT_SUPPORTED
import static org.prebid.server.functional.model.request.auction.AuctionEnvironment.SERVER_ORCHESTRATED
import static org.prebid.server.functional.model.request.auction.AuctionEnvironment.UNKNOWN
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.DOOH
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.request.auction.SecurityLevel.NON_SECURE
import static org.prebid.server.functional.model.request.auction.SecurityLevel.SECURE
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.REQUEST_BLOCKED_UNACCEPTABLE_CURRENCY
import static org.prebid.server.functional.model.response.auction.ErrorType.ALIAS
import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.model.response.auction.MediaType.AUDIO
import static org.prebid.server.functional.model.response.auction.MediaType.BANNER
import static org.prebid.server.functional.model.response.auction.MediaType.NATIVE
import static org.prebid.server.functional.model.response.auction.MediaType.VIDEO
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer
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
        assert response.ext?.debug?.httpcalls[BidderName.GENERIC.value]

        and: "Response should not contain error"
        assert !response.ext?.errors

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(adapterConfig)

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
        assert response.ext?.errors[GENERIC]*.code == [2]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(adapterConfig)

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
        def pbsConfig = ["adapter-defaults.modifying-vast-xml-allowed": adapterDefault,
                         "adapters.generic.modifying-vast-xml-allowed": generic]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

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

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        adapterDefault | generic
        "true"         | "true"
        "false"        | "true"
    }

    def "PBS should not modify vast xml when adapter-defaults.modifying-vast-xml-allowed = #adapterDefault and BIDDER.modifying-vast-xml-allowed = #generic"() {
        given: "PBS with adapter configuration"
        def pbsConfig = ["adapter-defaults.modifying-vast-xml-allowed": adapterDefault,
                         "adapters.generic.modifying-vast-xml-allowed": generic]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

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

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        adapterDefault | generic
        "true"         | "false"
        "false"        | "false"
    }

    def "PBS should mask values when adapter-defaults.pbs-enforces-ccpa = #adapterDefault settings when BIDDER.pbs-enforces-ccpa = #generic"() {
        given: "PBS with adapter configuration"
        def pbsConfig = ["adapter-defaults.pbs-enforces-ccpa": adapterDefault,
                         "adapters.generic.pbs-enforces-ccpa": generic]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        def validCcpa = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        bidRequest.regs.usPrivacy = validCcpa
        def lat = PBSUtils.getRandomDecimal(0, 90)
        def lon = PBSUtils.getRandomDecimal(0, 90)
        bidRequest.device = new Device(geo: new Geo(lat: lat, lon: lon))

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo?.lat as BigDecimal == PBSUtils.roundDecimal(lat, 2)
        assert bidderRequests.device?.geo?.lon as BigDecimal == PBSUtils.roundDecimal(lon, 2)

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        adapterDefault | generic
        "true"         | "true"
        "false"        | "true"
    }

    def "PBS should not mask values when adapter-defaults.pbs-enforces-ccpa = #adapterDefault settings when BIDDER.pbs-enforces-ccpa = #generic"() {
        given: "PBS with adapter configuration"
        def pbsConfig = ["adapter-defaults.pbs-enforces-ccpa": adapterDefault,
                         "adapters.generic.pbs-enforces-ccpa": generic]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        def validCcpa = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        bidRequest.regs.usPrivacy = validCcpa
        def lat = PBSUtils.getRandomDecimal(0, 90) as float
        def lon = PBSUtils.getRandomDecimal(0, 90) as float
        bidRequest.device = new Device(geo: new Geo(lat: lat, lon: lon))

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo?.lat == lat
        assert bidderRequests.device?.geo?.lon == lon

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

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
        bidRequest.ext.prebid.bidderParams = [(BidderName.GENERIC): [firstParam: PBSUtils.randomNumber]]

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
        bidRequest.ext.prebid.bidderParams = [(BidderName.GENERIC): [secondParam: secondParam]]

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
        def pbsConfig = ["adapters.generic.enabled"           : "true",
                         "adapters.generic.endpoint"          : "https://",
                         "http-client.circuit-breaker.enabled": "false"]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain error"
        assert response.ext?.errors[GENERIC]*.code == [999]
        assert response.ext?.errors[GENERIC]*.message == ["host name must not be empty"]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
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
        def pbsConfig = ["auction.filter-imp-media-type.enabled"                     : "true",
                         ("adapters.generic.meta-info.${configMediaType}".toString()): ""]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain empty seatbid"
        assert response.seatbid.isEmpty()

        and: "Response should contain error"
        assert response.ext?.warnings[GENERIC]*.code == [2]
        assert response.ext?.warnings[GENERIC]*.message == ["Bidder does not support any media types."]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        configMediaType    | bidRequest
        "app-media-types"  | BidRequest.getDefaultBidRequest(APP)
        "site-media-types" | BidRequest.getDefaultBidRequest(SITE)
    }

    def "PBS should not validate request when filter-imp-media-type = false and #configMediaType is empty in bidder config"() {
        given: "Pbs config"
        def pbsConfig = ["auction.filter-imp-media-type.enabled"                     : "false",
                         ("adapters.generic.meta-info.${configMediaType}".toString()): ""]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain seatbid"
        assert response.seatbid

        and: "Response should not contain error"
        assert !response.ext?.errors

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        configMediaType    | bidRequest
        "app-media-types"  | BidRequest.getDefaultBidRequest(APP)
        "site-media-types" | BidRequest.getDefaultBidRequest(SITE)
    }

    def "PBS should emit error when filter-imp-media-type = true and request contains media type that is not configured in bidder config"() {
        given: "Pbs config"
        def pbsConfig = ["auction.filter-imp-media-type.enabled"      : "true",
                         "adapters.generic.meta-info.site-media-types": "native"]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

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

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should not validate request when filter-imp-media-type = false and request contains only media type that is not configured in bidder config"() {
        given: "Pbs config"
        def pbsConfig = ["auction.filter-imp-media-type.enabled"      : "false",
                         "adapters.generic.meta-info.site-media-types": "native"]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

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

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should emit error for request with multiple impressions when filter-imp-media-type = true, one of imp doesn't contain supported media type"() {
        given: "Pbs config"
        def pbsConfig = ["auction.filter-imp-media-type.enabled"      : "true",
                         "adapters.generic.meta-info.site-media-types": "native,video"]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

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
        assert response.ext?.warnings[GENERIC]*.code == [2]
        assert response.ext?.warnings[GENERIC]*.message ==
                ["Imp ${bidRequest.imp[0].id} does not have a supported media type and has been removed from the " +
                         "request for this bidder." as String]

        and: "seatbid should not be empty"
        assert !response.seatbid.isEmpty()

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS auction should reject the bidder with media-type that is not supported by DOOH configuration with proper warning"() {
        given: "PBS service with configuration for dooh media-types"
        def pbsConfig = ["auction.filter-imp-media-type.enabled"      : "true",
                         "adapters.generic.meta-info.dooh-media-types": mediaType]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        when: "Requesting PBS auction"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain proper warning"
        assert bidResponse.ext?.warnings[GENERIC]?.message.contains("Bid request contains 0 impressions after filtering.")

        and: "Bid response shouldn't contain any seatbid"
        assert !bidResponse.seatbid

        and: "Should't send any bidder request"
        assert !bidder.getBidderRequests(bidRequest.id)

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

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
        def pbsConfig = ["auction.filter-imp-media-type.enabled"      : "true",
                         "adapters.generic.meta-info.dooh-media-types": mediaType.value]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid response with adm and nurl"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].adm = new Adm(assets: [titleAsset])
            seatbid[0].bid[0].nurl = PBSUtils.randomString
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain proper warning"
        assert response.ext?.warnings[GENERIC]?.message ==
                ["Imp ${bidRequest.imp[1].id} does not have a supported media type and has been removed from the request for this bidder."]

        and: "Bid response should contain seatbid"
        assert response.seatbid

        and: "Should send bidder request with only proper imp"
        assert bidder.getBidderRequest(bidRequest.id).imp.id == [bidRequest.imp.first().id]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        mediaType | bidRequest
        BANNER    | BidRequest.getDefaultBidRequest(DOOH).tap { imp << Imp.getDefaultImpression(VIDEO) }
        VIDEO     | BidRequest.getDefaultVideoRequest(DOOH).tap { imp << Imp.getDefaultImpression(NATIVE) }
        AUDIO     | BidRequest.getDefaultAudioRequest(DOOH).tap { imp << Imp.getDefaultImpression(NATIVE) }
    }

    def "PBS should return empty seatBit when filter-imp-media-type = true, request.imp doesn't contain supported media type"() {
        given: "Pbs config"
        def pbsConfig = ["auction.filter-imp-media-type.enabled"      : "true",
                         "adapters.generic.meta-info.site-media-types": "native,video"]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

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
        assert response.ext?.warnings[GENERIC]*.code == [2, 2]
        assert response.ext?.warnings[GENERIC]*.message ==
                ["Imp ${bidRequest.imp[0].id} does not have a supported media type and has been removed from " +
                         "the request for this bidder.",
                 "Bid request contains 0 impressions after filtering."]

        and: "seatbid should be empty"
        assert response.seatbid.isEmpty()

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should send server specific info to bidder when such is set in PBS config"() {
        given: "PBS with server info configuration"
        def serverDataCenter = PBSUtils.randomString
        def serverExternalUrl = "https://${PBSUtils.randomString}.com/"
        def serverHostVendorId = PBSUtils.randomNumber
        def pbsConfig = ["datacenter-region"  : serverDataCenter,
                         "external-url"       : serverExternalUrl as String,
                         "gdpr.host-vendor-id": serverHostVendorId as String]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS auction is requested"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS has sent server info to bidder during auction"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert bidderRequest?.ext?.prebid?.server?.externalUrl == serverExternalUrl
        assert bidderRequest.ext.prebid.server.datacenter == serverDataCenter
        assert bidderRequest.ext.prebid.server.gvlId == serverHostVendorId

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should request to bidder with header Content-Encoding = gzip when adapters.BIDDER.endpoint-compression = gzip"() {
        given: "PBS with adapter configuration"
        def compressionType = GZIP.value
        def pbsConfig = ["adapters.generic.enabled"             : "true",
                         "adapters.generic.endpoint-compression": compressionType]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain header Content-Encoding = gzip"
        assert response.ext?.debug?.httpcalls?.get(BidderName.GENERIC.value)?.requestHeaders?.first()
                ?.get(CONTENT_ENCODING_HEADER)?.first() == compressionType

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should send request to bidder without header Content-Encoding when adapters.BIDDER.endpoint-compression = none"() {
        given: "PBS with adapter configuration"
        def pbsConfig = ["adapters.generic.enabled"             : "true",
                         "adapters.generic.endpoint-compression": NONE.value]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should not contain header Content-Encoding"
        assert !response.ext?.debug?.httpcalls?.get(BidderName.GENERIC.value)?.requestHeaders?.first()
                ?.get(CONTENT_ENCODING_HEADER)

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
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
        null                | SECURE
        SECURE              | SECURE
        NON_SECURE          | NON_SECURE
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
        null          | SECURE
        SECURE        | SECURE
        NON_SECURE    | NON_SECURE
    }

    def "PBS shouldn't emit warning and proceed auction when imp.ext.anyUnsupportedBidder and imp.ext.prebid.bidder.generic in the request"() {
        given: "Default bid request"
        def unsupportedBidder = new AnyUnsupportedBidder(anyUnsupportedField: PBSUtils.randomString)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.anyUnsupportedBidder = unsupportedBidder
            imp[0].ext.prebid.bidder.generic = new Generic()
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain imp.ext.anyUnsupportedBidder"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].ext.anyUnsupportedBidder == unsupportedBidder

        and: "Response shouldn't contain warning"
        assert !response?.ext?.warnings
    }

    def "PBS should emit warning and proceed auction when imp.ext.anyUnsupportedBidder and imp.ext.generic in the request"() {
        given: "Default bid request"
        def unsupportedBidder = new AnyUnsupportedBidder(anyUnsupportedField: PBSUtils.randomString)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.generic = new Generic()
            imp[0].ext.anyUnsupportedBidder = unsupportedBidder
            imp[0].ext.prebid.bidder = null
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain imp.ext.anyUnsupportedBidder"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].ext.anyUnsupportedBidder == unsupportedBidder

        and: "PBS should emit an warning"
        assert response?.ext?.warnings[PREBID]*.code == [999]
        assert response?.ext?.warnings[PREBID]*.message ==
                ["WARNING: request.imp[0].ext.prebid.bidder.anyUnsupportedBidder was dropped with a reason: " +
                         "request.imp[0].ext.prebid.bidder contains unknown bidder: anyUnsupportedBidder"]
    }

    def "PBS should emit warning and proceed auction when ext.prebid fields include adunitcode"() {
        given: "Default bid request with populated ext.prebid.bidderParams"
        def genericBidderParams = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidderParams = [adUnitCode     : PBSUtils.randomString,
                                       (GENERIC.value): genericBidderParams]
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain error"
        assert !response.ext?.errors

        and: "PBS should emit an warning"
        assert response?.ext?.warnings[PREBID]*.code == [999]
        assert response?.ext?.warnings[PREBID]*.message ==
                ["WARNING: request.imp[0].ext.prebid.bidder.adUnitCode was dropped with a reason: " +
                         "request.imp[0].ext.prebid.bidder contains unknown bidder: adUnitCode"]
    }

    def "PBS shouldn't emit warning and proceed auction when all imp.ext fields known for PBS"() {
        given: "Default bid request with populated imp.ext"
        def impExt = ImpExt.getDefaultImpExt().tap {
            prebid.bidder.generic = null
            prebid.adUnitCode = PBSUtils.randomString
            generic = new Generic()
            auctionEnvironment = PBSUtils.getRandomEnum(AuctionEnvironment, [AuctionEnvironment.SERVER_ORCHESTRATED, AuctionEnvironment.UNKNOWN])
            all = PBSUtils.randomNumber
            context = new ImpExtContext(data: new ImpExtContextData())
            data = new ImpExtContextData(pbAdSlot: PBSUtils.randomString)
            general = PBSUtils.randomString
            gpid = PBSUtils.randomString
            skadn = PBSUtils.randomString
            tid = PBSUtils.randomString
        }
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext = impExt
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain error"
        assert !response.ext?.errors

        and: "Response shouldn't contain warning"
        assert !response.ext?.warnings

        and: "Bidder request should contain same field as requested"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest.imp[0].ext) {
            it.bidder == impExt.generic
            it.auctionEnvironment == impExt.auctionEnvironment
            it.all == impExt.all
            it.context == impExt.context
            it.data == impExt.data
            it.general == impExt.general
            it.gpid == impExt.gpid
            it.skadn == impExt.skadn
            it.tid == impExt.tid
            it.prebid.adUnitCode == impExt.prebid.adUnitCode
        }
    }

    def "PBS shouldn't emit warning and proceed auction when all imp.ext.prebid fields known for PBS"() {
        given: "PBS with old ortb version"
        def pbsConfig = ['adapters.generic.ortb-version': '2.5']
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with populated imp.ext.prebid"
        def impExt = ImpExt.getDefaultImpExt().tap {
            prebid.adUnitCode = PBSUtils.randomString
            prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomString)
            prebid.isRewardedInventory = PBSUtils.getRandomNumber(0, 1)
            prebid.options = new PrebidOptions(echoVideoAttrs: PBSUtils.randomBoolean)
        }
        def bidRequest = BidRequest.defaultVideoRequest.tap {
            imp[0].rwdd = null
            imp[0].ext = impExt
        }

        and: "Save storedImp into DB"
        def storedImp = StoredImp.getStoredImp(bidRequest)
        storedImpDao.save(storedImp)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain error"
        assert !response.ext?.errors

        and: "Response shouldn't contain warning"
        assert !response.ext?.warnings

        and: "Bidder request should contain same field as requested"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest.imp[0].ext.prebid) {
            it.adUnitCode == impExt.prebid.adUnitCode
            it.storedRequest == impExt.prebid.storedRequest
            it.isRewardedInventory == impExt.prebid.isRewardedInventory
            it.options.echoVideoAttrs == impExt.prebid.options.echoVideoAttrs
        }

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should proceed auction without warning when all ext.prebid.bidderParams fields are known"() {
        given: "Default bid request with populated ext.prebid.bidderParams"
        def genericBidderParams = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidderParams = [ae             : PBSUtils.randomString,
                                       all            : PBSUtils.randomString,
                                       context        : PBSUtils.randomString,
                                       data           : PBSUtils.randomString,
                                       general        : PBSUtils.randomString,
                                       gpid           : PBSUtils.randomString,
                                       skadn          : PBSUtils.randomString,
                                       tid            : PBSUtils.randomString,
                                       (GENERIC.value): genericBidderParams
            ]
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain error"
        assert !response.ext?.errors

        and: "Response shouldn't contain warning"
        assert !response.ext?.warnings

        and: "Bidder request should bidderParams only for bidder"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.bidderParams == [(GENERIC.value): genericBidderParams]
    }

    def "PBS should send request to bidder when adapters.bidder.meta-info.currency-accepted not specified"() {
        given: "PBS with adapter configuration"
        def pbsConfig = ['adapters.generic.meta-info.currency-accepted': '']
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [USD]
            ext.prebid.returnAllBidStatus = true
        }

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain http calls"
        assert response.ext?.debug?.httpcalls[BidderName.GENERIC.value]

        and: "Response should contain seatBid"
        assert response.seatbid.bid.flatten().size() == 1

        and: "Bidder request should be valid"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "Response shouldn't contain error"
        assert !response.ext?.errors

        and: "Response shouldn't contain warning"
        assert !response.ext?.warnings

        and: "PBS response shouldn't contain seatNonBid"
        assert !response.ext.seatnonbid

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should send request to bidder when adapters.bidder.aliases.bidder.meta-info.currency-accepted not specified"() {
        given: "PBS with adapter configuration"
        def pbsConfig = [
                "adapters.generic.aliases.alias.enabled"                    : "true",
                "adapters.generic.aliases.alias.endpoint"                   : "$networkServiceContainer.rootUri/auction".toString(),
                "adapters.generic.aliases.alias.meta-info.currency-accepted": ""]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with alias bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [USD]
            ext.prebid.returnAllBidStatus = true
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.generic = null
        }

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain http calls"
        assert response.ext?.debug?.httpcalls[BidderName.ALIAS.value]

        and: "Response should contain seatBid"
        assert response.seatbid.bid.flatten().size() == 1

        and: "Bidder request should be valid"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "Response shouldn't contain error"
        assert !response.ext?.errors

        and: "Response shouldn't contain warning"
        assert !response.ext?.warnings

        and: "PBS response shouldn't contain seatNonBid"
        assert !response.ext.seatnonbid

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should send request to bidder when adapters.bidder.meta-info.currency-accepted intersect with requested currency"() {
        given: "PBS with adapter configuration"
        def pbsConfig = ["adapters.generic.meta-info.currency-accepted": "${USD},${EUR}".toString()]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [USD]
            ext.prebid.returnAllBidStatus = true
        }

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain http calls"
        assert response.ext?.debug?.httpcalls[BidderName.GENERIC.value]

        and: "Response should contain seatBid"
        assert response.seatbid.bid.flatten().size() == 1

        and: "Bidder request should be valid"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "Response shouldn't contain error"
        assert !response.ext?.errors

        and: "Response shouldn't contain warning"
        assert !response.ext?.warnings

        and: "PBS response shouldn't contain seatNonBid and contain errors"
        assert !response.ext.seatnonbid

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS shouldn't send request to bidder and emit warning when adapters.bidder.meta-info.currency-accepted not intersect with requested currency"() {
        given: "PBS with adapter configuration"
        def pbsConfig = ["adapters.generic.meta-info.currency-accepted": "${JPY},${CHF}".toString()]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [USD]
            ext.prebid.returnAllBidStatus = true
        }

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain http calls"
        assert !response.ext?.debug?.httpcalls

        and: "Response shouldn't contain seatBid"
        assert !response.seatbid

        and: "Pbs shouldn't make bidder request"
        assert !bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain error"
        assert !response.ext?.errors

        and: "Response should seatNon bid with code 205"
        assert response.ext.seatnonbid.size() == 1

        and: "PBS should emit an warnings"
        assert response.ext?.warnings[GENERIC]*.code == [999]
        assert response.ext?.warnings[GENERIC]*.message ==
                ["No match between the configured currencies and bidRequest.cur"]

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == BidderName.GENERIC
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BLOCKED_UNACCEPTABLE_CURRENCY

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should send request to bidder when adapters.bidder.aliases.bidder.meta-info.currency-accepted intersect with requested currency"() {
        given: "PBS with adapter configuration"
        def pbsConfig = [
                "adapters.generic.aliases.alias.enabled"                    : "true",
                "adapters.generic.aliases.alias.endpoint"                   : "$networkServiceContainer.rootUri/auction".toString(),
                "adapters.generic.aliases.alias.meta-info.currency-accepted": "${USD},${EUR}".toString()]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default basic BidRequest with alias bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [USD]
            ext.prebid.returnAllBidStatus = true
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.generic = null
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain http calls"
        assert response.ext?.debug?.httpcalls[ALIAS.value]

        and: "Response should contain seatBid"
        assert response.seatbid.bid.flatten().size() == 1

        and: "Bidder request should be valid"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "Response shouldn't contain error"
        assert !response.ext?.errors

        and: "Response shouldn't contain warning"
        assert !response.ext?.warnings

        and: "PBS response shouldn't contain seatNonBid and contain errors"
        assert !response.ext.seatnonbid

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS shouldn't send request to bidder and emit warning when adapters.bidder.aliases.bidder.meta-info.currency-accepted not intersect with requested currency"() {
        given: "PBS with adapter configuration"
        def pbsConfig = [
                "adapters.generic.aliases.alias.enabled"                    : "true",
                "adapters.generic.aliases.alias.endpoint"                   : "$networkServiceContainer.rootUri/auction".toString(),
                "adapters.generic.aliases.alias.meta-info.currency-accepted": "${JPY},${CHF}".toString()]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default basic BidRequest with alias bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [USD]
            ext.prebid.returnAllBidStatus = true
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.generic = null
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain http calls"
        assert !response.ext?.debug?.httpcalls

        and: "Response shouldn't contain seatBid"
        assert !response.seatbid

        and: "Pbs shouldn't make bidder request"
        assert !bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain error"
        assert !response.ext?.errors

        and: "PBS should emit an warnings"
        assert response.ext?.warnings[ALIAS]*.code == [999]
        assert response.ext?.warnings[ALIAS]*.message ==
                ["No match between the configured currencies and bidRequest.cur"]

        and: "Response should seatNon bid with code 205"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == BidderName.ALIAS
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BLOCKED_UNACCEPTABLE_CURRENCY

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should add auction environment to imp.ext.igs when it is present in imp.ext and imp.ext.igs is empty"() {
        given: "Default bid request with populated imp.ext"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.tap {
                auctionEnvironment = requestedAuctionEnvironment
                interestGroupAuctionSupports = new InterestGroupAuctionSupport(auctionEnvironment: null)
            }
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should imp[].{ae/ext.igs.ae} same value as requested"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].ext.auctionEnvironment == requestedAuctionEnvironment
        assert bidderRequest.imp[0].ext.interestGroupAuctionSupports.auctionEnvironment == requestedAuctionEnvironment

        where:
        requestedAuctionEnvironment << [NOT_SUPPORTED, DEVICE_ORCHESTRATED]
    }

    def "PBS shouldn't add unsupported auction environment to imp.ext.igs when it is present in imp.ext and imp.ext.igs is empty"() {
        given: "Default bid request with populated imp.ext"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.tap {
                auctionEnvironment = requestedAuctionEnvironment
                interestGroupAuctionSupports = new InterestGroupAuctionSupport(auctionEnvironment: null)
            }
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should imp[].ae same value as requested"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].ext.auctionEnvironment == requestedAuctionEnvironment
        assert !bidderRequest.imp[0].ext.interestGroupAuctionSupports.auctionEnvironment

        where:
        requestedAuctionEnvironment << [SERVER_ORCHESTRATED, UNKNOWN]
    }

    def "PBS shouldn't change auction environment in imp.ext.igs when it is present in both imp.ext and imp.ext.igs"() {
        given: "Default bid request with populated imp.ext"
        def extAuctionEnv = PBSUtils.getRandomEnum(AuctionEnvironment, [SERVER_ORCHESTRATED, UNKNOWN])
        def extIgsAuctionEnv = PBSUtils.getRandomEnum(AuctionEnvironment, [SERVER_ORCHESTRATED, UNKNOWN])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.tap {
                auctionEnvironment = extAuctionEnv
                interestGroupAuctionSupports = new InterestGroupAuctionSupport(auctionEnvironment: extIgsAuctionEnv)
            }
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should imp[].{ae/ext.igs.ae} same value as requested"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].ext.auctionEnvironment == extAuctionEnv
        assert bidderRequest.imp[0].ext.interestGroupAuctionSupports.auctionEnvironment == extIgsAuctionEnv
    }

    def "PBS should reject alias bidders when bidder params from request doesn't satisfy own json-schema"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.tap {
                it.generic.exampleProperty = PBSUtils.randomNumber
                //Adrino hard coded bidder alias in generic.yaml
                it.adrino = new Adrino(hash: PBSUtils.randomNumber)
            }
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder should be dropped"
        assert response.ext?.warnings[PREBID]*.code == [999, 999, 999]
        assert response.ext?.warnings[PREBID]*.message ==
                ["WARNING: request.imp[0].ext.prebid.bidder.generic was dropped with a reason: " +
                         "request.imp[0].ext.prebid.bidder.generic failed validation.\n" +
                         "\$.exampleProperty: integer found, string expected",
                 "WARNING: request.imp[0].ext.prebid.bidder.adrino was dropped with a reason: " +
                         "request.imp[0].ext.prebid.bidder.adrino failed validation.\n" +
                         "\$.hash: integer found, string expected",
                 "WARNING: request.imp[0].ext must contain at least one valid bidder"]

        and: "PBS should not call bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0

        and: "targeting should be empty"
        assert response.seatbid.isEmpty()
    }

    def "PBS should reject alias bidders when bidder params from request doesn't satisfy aliased json-schema"() {
        given: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.tap {
                it.generic.exampleProperty = PBSUtils.randomNumber
                //Nativo hard coded bidder alias in generic.yaml
                it.nativo = new Generic(exampleProperty: PBSUtils.randomNumber)
            }
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder should be dropped"
        assert response.ext?.warnings[PREBID]*.code == [999, 999, 999]
        assert response.ext?.warnings[PREBID]*.message ==
                ["WARNING: request.imp[0].ext.prebid.bidder.generic was dropped with a reason: " +
                         "request.imp[0].ext.prebid.bidder.generic failed validation.\n" +
                         "\$.exampleProperty: integer found, string expected",
                 "WARNING: request.imp[0].ext.prebid.bidder.nativo was dropped with a reason: " +
                         "request.imp[0].ext.prebid.bidder.nativo failed validation.\n" +
                         "\$.exampleProperty: integer found, string expected",
                 "WARNING: request.imp[0].ext must contain at least one valid bidder"]

        and: "PBS should not call bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0

        and: "targeting should be empty"
        assert response.seatbid.isEmpty()
    }

    def "PBS should send bidder code from imp[].ext.prebid.bidder to seatbid.bid.ext.prebid.meta.adapterCode"() {
        given: "Default basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].seat = OPENX
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [BidderName.GENERIC]

        and: "Bidder request should be valid"
        assert bidder.getBidderRequest(bidRequest.id)
    }

    def "PBS should send bidder code from imp[].ext.prebid.bidder to seatbid.bid.ext.prebid.meta.adapterCode when requested soft alias"() {
        given: "Default bid request with alias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.tap {
                generic = null
                alias = new Generic()
            }
            ext.prebid.aliases = [(ALIAS.value): BidderName.GENERIC]
            ext.prebid.targeting = new Targeting()
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [BidderName.GENERIC]

        and: "Response should contain seat bid"
        assert response.seatbid.seat == [BidderName.ALIAS]

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${BidderName.ALIAS}"]
        assert targeting["hb_size_${BidderName.ALIAS}"]
        assert targeting["hb_bidder"] == BidderName.ALIAS.value
        assert targeting["hb_bidder_${BidderName.ALIAS}"] == BidderName.ALIAS.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(ALIAS.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)
    }

    def "PBS should populate same code for adapter code when make call for generic hard code alias"() {
        given: "PBS config with bidder"
        def pbsConfig = ["adapters.generic.aliases.alias.enabled" : "true",
                         "adapters.generic.aliases.alias.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
        def defaultPbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with alias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.tap {
                generic = null
                alias = new Generic()
            }
            ext.prebid.targeting = new Targeting()
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [BidderName.ALIAS]

        and: "Response should contain seat bid"
        assert response.seatbid.seat == [BidderName.ALIAS]

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${BidderName.ALIAS}"]
        assert targeting["hb_size_${BidderName.ALIAS}"]
        assert targeting["hb_bidder"] == BidderName.ALIAS.value
        assert targeting["hb_bidder_${BidderName.ALIAS}"] == BidderName.ALIAS.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(ALIAS.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should make call for alias when hard alias and demandSource specified"() {
        given: "PBS config with bidder"
        def pbsConfig = ["adapters.amx.enabled"               : "true",
                         "adapters.amx.endpoint"              : "$networkServiceContainer.rootUri/auction".toString(),
                         "adapters.amx.aliases.alias.enabled" : "true",
                         "adapters.amx.aliases.alias.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
        def defaultPbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid Request with generic and openx bidder within separate imps"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.tap {
                generic = null
                alias = new Generic()
            }
            ext.prebid.targeting = new Targeting()
        }

        and: "Bid response with bidder code"
        def demandSource = PBSUtils.randomString
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, BidderName.ALIAS).tap {
            it.seatbid[0].bid[0].ext = new BidExt(demandSource: demandSource)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain demand source"
        assert response.seatbid.bid.ext.prebid.meta.demandSource.flatten() == [demandSource]

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [BidderName.ALIAS]

        and: "Response should contain seat bid"
        assert response.seatbid.seat == [BidderName.ALIAS]

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${BidderName.ALIAS}"]
        assert targeting["hb_size_${BidderName.ALIAS}"]
        assert targeting["hb_bidder"] == BidderName.ALIAS.value
        assert targeting["hb_bidder_${BidderName.ALIAS}"] == BidderName.ALIAS.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(ALIAS.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should send bidder code from imp[].ext.prebid.bidder to seatbid.bid.ext.prebid.meta.adapterCode when requested soft alias with upper case"() {
        given: "Default bid request with alias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.aliases = [(ALIAS.value): BidderName.GENERIC]
            ext.prebid.targeting = new Targeting()
            imp[0].ext.prebid.bidder.tap {
                generic = null
                aliasUpperCase = new Generic()
            }
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [BidderName.GENERIC]

        and: "Response should contain seat bid"
        assert response.seatbid.seat == [ALIAS_UPPER_CASE]

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${ALIAS_UPPER_CASE}"]
        assert targeting["hb_size_${ALIAS_UPPER_CASE}"]
        assert targeting["hb_bidder"] == ALIAS_UPPER_CASE.value
        assert targeting["hb_bidder_${ALIAS_UPPER_CASE}"] == ALIAS_UPPER_CASE.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(ALIAS_UPPER_CASE.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)
    }

    def "PBS should populate targeting with bidder in camel case when bidder with camel case was requested"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.genericCamelCase = new Generic()
            it.ext.prebid.targeting = new Targeting()
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${GENERIC_CAMEL_CASE}"]
        assert targeting["hb_size_${GENERIC_CAMEL_CASE}"]
        assert targeting["hb_bidder"] == GENERIC_CAMEL_CASE.value
        assert targeting["hb_bidder_${GENERIC_CAMEL_CASE}"] == GENERIC_CAMEL_CASE.value

        and: "Bid response should contain seat"
        assert response.seatbid.seat == [GENERIC_CAMEL_CASE]

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(GENERIC_CAMEL_CASE.value)

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [BidderName.GENERIC]
    }

    def "PBS should make call for alias in upper case when soft alias specified with same name in upper case strategy"() {
        given: "Default bid request with soft alias and targeting"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.aliases = [(ALIAS.value): BidderName.GENERIC]
            imp[0].ext.prebid.bidder.aliasUpperCase = new Generic()
            imp[0].ext.prebid.bidder.generic = null
            it.ext.prebid.targeting = new Targeting()
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [BidderName.GENERIC]

        and: "Response should contain seat bid"
        assert response.seatbid.seat == [ALIAS_UPPER_CASE]

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${ALIAS_UPPER_CASE}"]
        assert targeting["hb_size_${ALIAS_UPPER_CASE}"]
        assert targeting["hb_bidder"] == ALIAS_UPPER_CASE.value
        assert targeting["hb_bidder_${ALIAS_UPPER_CASE}"] == ALIAS_UPPER_CASE.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(ALIAS_UPPER_CASE.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)
    }

    def "PBS should populate adapter code with requested bidder when conflict with soft and hard alias"() {
        given: "PBS config with bidder"
        def pbsConfig = ["adapters.amx.enabled"               : "true",
                         "adapters.amx.endpoint"              : "$networkServiceContainer.rootUri/auction".toString(),
                         "adapters.amx.aliases.alias.enabled" : "true",
                         "adapters.amx.aliases.alias.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
        def defaultPbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Bid request with amx bidder and targeting"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.amx = null
            imp[0].ext.prebid.bidder.generic = null
            it.ext.prebid.aliases = [(ALIAS.value): BidderName.GENERIC]
            it.ext.prebid.targeting = new Targeting()
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [BidderName.ALIAS]

        and: "Response should contain seat bid"
        assert response.seatbid.seat == [BidderName.ALIAS]

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${BidderName.ALIAS}"]
        assert targeting["hb_size_${BidderName.ALIAS}"]
        assert targeting["hb_bidder"] == BidderName.ALIAS.value
        assert targeting["hb_bidder_${BidderName.ALIAS}"] == BidderName.ALIAS.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(ALIAS.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should populate adapter code with requested bidder when conflict with soft and generic hard alias"() {
        given: "PBS config with bidders"
        def pbsConfig = ["adapters.amx.enabled"                   : "true",
                         "adapters.amx.endpoint"                  : "$networkServiceContainer.rootUri/auction".toString(),
                         "adapters.generic.aliases.alias.enabled" : "true",
                         "adapters.generic.aliases.alias.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
        def defaultPbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Bid request with amx bidder and targeting"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.amx = null
            imp[0].ext.prebid.bidder.generic = null
            it.ext.prebid.aliases = [(ALIAS.value): AMX]
            it.ext.prebid.targeting = new Targeting()
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [BidderName.ALIAS]

        and: "Response should contain seat bid"
        assert response.seatbid.seat == [BidderName.ALIAS]

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${BidderName.ALIAS}"]
        assert targeting["hb_size_${BidderName.ALIAS}"]
        assert targeting["hb_bidder"] == BidderName.ALIAS.value
        assert targeting["hb_bidder_${BidderName.ALIAS}"] == BidderName.ALIAS.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(ALIAS.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should properly populate bidder code when soft alias ignore standalone adapter"() {
        given: "PBS config with amx bidder"
        def pbsConfig = ["adapters.amx.enabled" : "true",
                         "adapters.amx.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
        def defaultPbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with soft alias and targeting"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.amx = new Amx()
            imp[0].ext.prebid.bidder.generic = null
            ext.prebid.targeting = new Targeting()
            ext.prebid.aliases = [(AMX.value): BidderName.GENERIC]
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain seat"
        assert response.seatbid.seat == [AMX]

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${AMX}"]
        assert targeting["hb_size_${AMX}"]
        assert targeting["hb_bidder"] == AMX.value
        assert targeting["hb_bidder_${AMX}"] == AMX.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(AMX.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should merge stored imp with appnexus bidder requested when reserve field specified"() {
        given: "Pbs default config with appnexus"
        def pbsConfig = ["adapters.${APPNEXUS.value}.enabled" : "true",
                         "adapters.${APPNEXUS.value}.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
        def defaultPbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default stored request with specified stored imps and request"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.appNexus = AppNexus.getDefault().tap {
                reserve = PBSUtils.getRandomDecimal() as Double
            }
            imp[0].ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomString)
            ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
        }

        and: "Save storedImp into DB"
        def storedImp = StoredImp.getStoredImp(bidRequest).tap {
            impData = Imp.defaultImpression
        }
        storedImpDao.save(storedImp)

        and: "Save stored request with source.tid and cur"
        def storedBidRequest = new BidRequest(cur: [USD], source: new Source(tid: PBSUtils.randomString))
        def storedRequest = StoredRequest.getStoredRequest(storedRequestId, storedBidRequest)
        storedRequestDao.save(storedRequest)

        and: "Default basic bid with bid.ext"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, APPNEXUS).tap {
            seatbid[0].bid[0].ext = new BidExt()
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain appnexus and generic bidder"
        assert response.seatbid.size() == 2
        assert response.seatbid.seat.sort() == [APPNEXUS, BidderName.GENERIC].sort()

        and: "Bidder requests should perform two bidder call"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.size() == 2

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }
}
