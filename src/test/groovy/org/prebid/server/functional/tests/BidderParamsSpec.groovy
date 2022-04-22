package org.prebid.server.functional.tests

import io.qameta.allure.Issue
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.model.request.auction.RegsExt
import org.prebid.server.functional.model.request.vtrack.VtrackRequest
import org.prebid.server.functional.model.request.vtrack.xml.Vast
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.CcpaConsent

import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED

@PBSTest
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

        where:
        adapterDefault | generic | adapterConfig
        "true"         | "true"  | ["adapter-defaults.enabled"   : adapterDefault,
                                    "adapters.facebook.enabled"  : "false",
                                    "adapters.brightroll.enabled": "false",
                                    "adapters.generic.enabled"   : generic]
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
        "true"         | "false" | ["adapter-defaults.enabled"   : adapterDefault,
                                    "adapters.facebook.enabled"  : "false",
                                    "adapters.brightroll.enabled": "false",
                                    "adapters.generic.enabled"   : generic]
    }

    def "PBS should modify vast xml when adapter-defaults.modifying-vast-xml-allowed = #adapterDefault and BIDDER.modifying-vast-xml-allowed = #generic"() {
        given: "PBS with adapter configuration"
        def pbsService = pbsServiceFactory.getService(["adapter-defaults.modifying-vast-xml-allowed": adapterDefault,
                                                       "adapters.generic.modifying-vast-xml-allowed": generic])

        and: "Default VtrackRequest"
        String payload = PBSUtils.randomString
        def request = VtrackRequest.getDefaultVtrackRequest(mapper.encodeXml(Vast.getDefaultVastModel(payload)))
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
        def request = VtrackRequest.getDefaultVtrackRequest(mapper.encodeXml(Vast.getDefaultVastModel(payload)))
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
        def lat = PBSUtils.getFractionalRandomNumber(0, 90)
        def lon = PBSUtils.getFractionalRandomNumber(0, 90)
        bidRequest.device = new Device(geo: new Geo(lat: lat, lon: lon))

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo?.lat == PBSUtils.getRoundedFractionalNumber(lat, 2)
        assert bidderRequests.device?.geo?.lon == PBSUtils.getRoundedFractionalNumber(lon, 2)

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
        def lat = PBSUtils.getFractionalRandomNumber(0, 90)
        def lon = PBSUtils.getFractionalRandomNumber(0, 90)
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

        then: "Response shouldn't contain bidder param from another biddder"
        bidder.getBidderRequest(bidRequest.id)
    }

    // TODO: create same test for enabled circuit breaker
    @Issue("https://github.com/prebid/prebid-server-java/issues/1478")
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
        assert response.ext?.errors[ErrorType.GENERIC]*.message == ["no empty host accepted"]
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
        def storedRequest = StoredRequest.getDbStoredRequest(bidRequest, storedRequestModel)
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
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
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
}
