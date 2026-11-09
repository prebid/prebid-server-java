package org.prebid.server.functional.tests.bidder

import org.prebid.server.functional.model.bidder.AppNexus
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.db.StoredImp
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.Adrino
import org.prebid.server.functional.model.request.auction.AnyUnsupportedBidder
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.ImpExt
import org.prebid.server.functional.model.request.auction.ImpExtContext
import org.prebid.server.functional.model.request.auction.ImpExtContextData
import org.prebid.server.functional.model.request.auction.PrebidOptions
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.model.request.auction.Source
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.response.auction.BidExt
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.Currency.USD
import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC_CAMEL_CASE
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.auction.SecurityLevel.NON_SECURE
import static org.prebid.server.functional.model.request.auction.SecurityLevel.SECURE
import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID

class BidderParamsSpec extends BaseSpec {

    def "PBS should prefer bidder params from imp[*].ext.prebid.bidder.BIDDER when ext.prebid.bidderparams.BIDDER is specified"() {
        given: "Default basic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        def firstParam = PBSUtils.randomNumber
        bidRequest.imp.first().ext.prebid.bidder.generic = new Generic(firstParam: firstParam)

        and: "Set bidderParam to bidRequest"
        bidRequest.ext.prebid.bidderParams = [(BidderName.GENERIC): [firstParam: PBSUtils.randomNumber]]

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain firstParam value from imp[*].ext.prebid.bidder.BIDDER"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0]?.ext?.bidder?.firstParam == firstParam

        and: "Bidder request should contain requested bidder param for related itself bidder"
        assert bidderRequest.ext.prebid.bidderParams[GENERIC] == bidRequest.ext.prebid.bidderParams[GENERIC]
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

        then: "Response should contain firstParam value from imp[*].ext.prebid.bidder.BIDDER"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0]?.ext?.bidder?.firstParam == firstParam
    }

    def "PBS should merge bidder params from imp[*].ext.prebid.bidder.BIDDER and ext.prebid.bidderparams.BIDDER"() {
        given: "Default basic BidRequest"
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

        then: "Bidder request should not contain params from another bidder"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].ext.bidder.firstParam == firstParam
        assert !bidderRequest.imp[0].ext.bidder.placementId
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

        and: "Bidder request should contain only valid impression"
        assert bidder.getBidderRequest(bidRequest.id).imp*.id == [bidRequest.imp[1].id]

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

    def "PBS should not treat reserved imp[].ext.tid as bidder"() {
        given: "Default basic BidRequest with imp[].ext.tid object"
        def bidRequest = BidRequest.defaultBidRequest
        def tid = PBSUtils.getRandomString()
        bidRequest.imp.first().ext.tid = tid

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "imp[].ext.tid object should be passed to a bidder"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp?.first()?.ext?.tid == tid

        and: "Response shouldn't contain warning"
        assert !response.ext?.warnings
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

        then: "Bidder request should contain imp[0].secure same value as in request"
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
    }

    def "PBS should proceed auction without warning when all ext.prebid.bidderParams fields are known"() {
        given: "Default bid request with populated ext.prebid.bidderParams"
        def genericBidderParams = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidderParams = [all            : PBSUtils.randomString,
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

    def "PBS should merge stored imp with appnexus bidder requested when reserve field specified"() {
        given: "Pbs default config with appnexus"
        def pbsConfig = ["adapters.${APPNEXUS.value}.enabled" : "true",
                         "adapters.${APPNEXUS.value}.endpoint": "$Dependencies.networkServiceContainer.rootUri/auction".toString()]
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
    }
}
