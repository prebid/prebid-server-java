package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.AppNexus
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.request.auction.Adrino
import org.prebid.server.functional.model.request.auction.Amx
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.response.auction.BidExt
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS_UPPER_CASE
import static org.prebid.server.functional.model.bidder.BidderName.AMX
import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.BOGUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.GENER_X
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.CompressionType.GZIP
import static org.prebid.server.functional.model.response.auction.ErrorType.ALIAS
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer
import static org.prebid.server.functional.util.HttpUtil.CONTENT_ENCODING_HEADER

class AliasSpec extends BaseSpec {

    private static final Map<String, String> ADDITIONAL_BIDDERS_CONFIG = [
            "adapters.${OPENX.value}.enabled"     : "true",
            "adapters.${OPENX.value}.endpoint"    : "$networkServiceContainer.rootUri/${OPENX.value}/auction".toString(),
            "adapters.${APPNEXUS.value}.enabled"  : "true",
            "adapters.${APPNEXUS.value}.endpoint" : "$networkServiceContainer.rootUri/${APPNEXUS.value}/auction".toString()
    ]

    private static final Map<String, String> AMX_CONFIG = [
            "adapters.amx.enabled" : "true",
            "adapters.amx.endpoint": "$networkServiceContainer.rootUri/auction".toString()
    ]

    private static final Map<String, String> GENERIC_HARD_ALIAS_CONFIG =
            ADDITIONAL_BIDDERS_CONFIG + GENERIC_ALIAS_CONFIG + AMX_CONFIG

    private static final Map<String, String> AMX_HARD_ALIAS_CONFIG = AMX_CONFIG + [
            "adapters.amx.aliases.alias.enabled"            : "true",
            "adapters.amx.aliases.alias.meta-info.vendor-id": "0",
            "adapters.amx.aliases.alias.endpoint"           : "$networkServiceContainer.rootUri/auction".toString(),
            "adapters.generic.endpoint-compression"        : GZIP.value
    ]

    private static PrebidServerService pbsServiceWithGenericHardAlias
    private static PrebidServerService pbsServiceWithAmxHardAlias

    def setupSpec() {
        pbsServiceWithGenericHardAlias = pbsServiceFactory.getService(GENERIC_HARD_ALIAS_CONFIG)
        pbsServiceWithAmxHardAlias = pbsServiceFactory.getService(AMX_HARD_ALIAS_CONFIG)
    }

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(GENERIC_HARD_ALIAS_CONFIG)
        pbsServiceFactory.removeContainer(AMX_HARD_ALIAS_CONFIG)
    }

    def "PBS should route request-defined alias to its parent adapter"() {
        given: "Default bid request with alias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            imp[0].ext.prebid.bidder.alias = new Generic()
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS contain two http calls and the same url for both"
        def responseDebug = response.ext.debug
        assert responseDebug.httpcalls.size() == 2
        assert responseDebug.httpcalls[GENERIC.value]*.uri == responseDebug.httpcalls[ALIAS.value]*.uri

        and: "Resolved request should contain aliases as in request"
        assert responseDebug.resolvedRequest.ext.prebid.aliases == bidRequest.ext.prebid.aliases

        and: "Bidder request should contain request per-alies"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.size() == 2
    }

    def "PBS should ignore request alias without matching bidder params"() {
        given: "Default bid request with alias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.aliases = [(BOGUS.value): GENERIC]
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS contain one bidder requested"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.size() == 1

        and: "Resolved request should contain unknown aliases as in request"
        assert response.ext.debug.resolvedRequest.ext.prebid.aliases == bidRequest.ext.prebid.aliases
    }

    def "PBS should inherit gzip compression for soft alias"() {
        given: "Default bid request with soft alias"
        def compressionType = GZIP.value
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.aliases = [(GENER_X.value): GENERIC]
            imp[0].ext.prebid.bidder.generX = new Generic()
        }

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxHardAlias.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain header Content-Encoding = gzip"
        assert response.ext?.debug?.httpcalls?.get(GENER_X.value)?.requestHeaders?.first()
                ?.get(CONTENT_ENCODING_HEADER)?.first() == compressionType
    }

    def "PBS should reject alias GVL ID referencing unknown alias"() {
        given: "Default basic BidRequest with aliasgvlids and aliases"
        def bidderName = PBSUtils.randomString
        def validId = 1
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.aliasgvlids = [(bidderName): validId]
        bidRequest.ext.prebid.aliases = [(PBSUtils.randomString): GENERIC]

        when: "Sending auction request to PBS"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.responseBody.contains("Invalid request format: request.ext.prebid.aliasgvlids. " +
                "vendorId ${validId} refers to unknown bidder alias: ${bidderName.toLowerCase()}")
    }

    def "PBS should reject nonpositive GVL IDs for aliases"() {
        given: "Default basic BidRequest with aliasgvlids and aliases"
        def bidderName = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.aliasgvlids = [(bidderName): invalidId]
        bidRequest.ext.prebid.aliases = [(bidderName): GENERIC]

        when: "Sending auction request to PBS"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.responseBody.contains("Invalid request format: request.ext.prebid.aliasgvlids. " +
                "Invalid vendorId ${invalidId} for alias: ${bidderName.toLowerCase()}. Choose a different vendorId, or remove this entry.")

        where:
        invalidId << [PBSUtils.randomNegativeNumber, 0]
    }

    def "PBS should reject soft alias referencing unknown parent bidder"() {
        given: "Default bid request with alias"
        def randomString = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.aliases = [(randomString): BOGUS]
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == BAD_REQUEST.code()
        assert exception.responseBody == "Invalid request format: request.ext.prebid.aliases.${randomString.toLowerCase()} " +
                "refers to unknown bidder: $BOGUS.value"
    }

    def "PBS should register configured hard alias independently of parent"() {
        given: "Default bid request with alias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.alias = new Generic()
        }

        when: "PBS processes auction request"
        pbsServiceWithGenericHardAlias.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain request per-alies"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.size() == 2
    }

    def "PBS should prefer a material bidder over a conflicting soft alias"() {
        given: "Default bid request with OpenX and request-defined alias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            imp[0].ext.prebid.bidder.generic = new Generic()
            ext.prebid.aliases = [(OPENX.value): GENERIC]
        }

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithGenericHardAlias.sendAuctionRequest(bidRequest)

        then: "PBS contain two http calls and the different url for both"
        def responseDebug = bidResponse.ext.debug
        assert responseDebug.httpcalls.size() == 2
        assert responseDebug.httpcalls[OPENX.value]*.uri == ["$networkServiceContainer.rootUri/openx/auction"]
        assert responseDebug.httpcalls[GENERIC.value]*.uri == ["$networkServiceContainer.rootUri/auction"]
    }

    def "PBS should accept valid material bidder params despite conflicting soft alias"() {
        given: "Default bid request with openx and alias bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.appNexus = AppNexus.default
            imp[0].ext.prebid.bidder.generic = null
            ext.prebid.aliases = [(APPNEXUS.value): OPENX]
        }

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithGenericHardAlias.sendAuctionRequest(bidRequest)

        then: "PBS contain http call for specific bidder"
        def responseDebug = bidResponse.ext.debug
        assert responseDebug.httpcalls.size() == 1
        assert responseDebug.httpcalls[APPNEXUS.value]*.uri == ["$networkServiceContainer.rootUri/${APPNEXUS.value}/auction"]

        and: "PBS should not contain any warnings"
        assert !bidResponse.ext.warnings
    }

    def "PBS should reject invalid material bidder params despite conflicting soft alias"() {
        given: "Default bid request with openx and alias bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.appNexus = new AppNexus()
            imp[0].ext.prebid.bidder.generic = null
            ext.prebid.aliases = [(APPNEXUS.value): OPENX]
        }

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithGenericHardAlias.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't contain http calls"
        assert !bidResponse.ext.debug.httpcalls

        and: "Bid response should contain warning"
        assert bidResponse.ext?.warnings[PREBID]?.code == [999, 999]
        assert bidResponse.ext?.warnings[PREBID]*.message ==
                ["WARNING: request.imp[0].ext.prebid.bidder.${APPNEXUS.value} was dropped with a reason: " +
                         "request.imp[0].ext.prebid.bidder.${APPNEXUS.value} failed validation.\n" +
                         "\$: must be valid to one and only one schema, but 0 are valid\n" +
                         "\$: required property 'placement_id' not found\n" +
                         "\$: required property 'inv_code' not found\n" +
                         "\$: required property 'placementId' not found\n" +
                         "\$: required property 'member' not found\n" +
                         "\$: required property 'invCode' not found",
                 "WARNING: request.imp[0].ext must contain at least one valid bidder"]
    }

    def "PBS should invoke both original bidder and request-defined alias"() {
        given: "Bid request with original bidder and soft alias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.generX = new Generic()
            ext.prebid.aliases = [(GENER_X.value): GENERIC]
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should send requests to both bidders"
        assert response.ext.debug.httpcalls.size() == 2
        assert response.ext.debug.httpcalls[GENER_X.value]*.uri == ["$networkServiceContainer.rootUri/auction"]
        assert response.ext.debug.httpcalls[GENERIC.value]*.uri == ["$networkServiceContainer.rootUri/auction"]

        and: "Bidder should receive two requests"
        assert bidder.getBidderRequests(bidRequest.id).size() == 2
    }

    def "PBS should invoke only request-defined alias when original bidder is absent"() {
        given: "Bid request with soft alias and without original bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.generX = new Generic()
            imp[0].ext.prebid.bidder.generic = null
            ext.prebid.aliases = [(GENER_X.value): GENERIC]
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should send request only to alias"
        assert response.ext.debug.httpcalls.size() == 1
        assert response.ext.debug.httpcalls[GENER_X.value]*.uri == ["$networkServiceContainer.rootUri/auction"]

        and: "Bidder should receive one request"
        assert bidder.getBidderRequests(bidRequest.id).size() == 1
    }

    def "PBS should preserve uppercase soft alias in seat and targeting"() {
        given: "Default bid request with alias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            ext.prebid.targeting = new Targeting()
            imp[0].ext.prebid.bidder.tap {
                generic = null
                aliasUpperCase = new Generic()
            }
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [GENERIC]

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

    def "PBS should reject invalid bidder parameters for generic and hard alias"() {
        given: "Default basic generic BidRequest"
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

    def "PBS should retain parent adapter code for soft alias"() {
        given: "Default bid request with alias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.tap {
                generic = null
                alias = new Generic()
            }
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            ext.prebid.targeting = new Targeting()
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [GENERIC]

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

        and: "Bidder request should be valid and not contain aliases"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id).first
        assert !bidderRequests.ext.prebid.aliases
    }

    def "PBS should use alias adapter code for Generic hard alias"() {
        given: "Default bid request with hard alias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.tap {
                generic = null
                alias = new Generic()
            }
            ext.prebid.targeting = new Targeting()
        }

        when: "PBS processes auction request"
        def response = pbsServiceWithGenericHardAlias.sendAuctionRequest(bidRequest)

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
    }

    def "PBS should propagate demandSource for AMX hard alias"() {
        given: "Default bid request with AMX hard alias"
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
        def response = pbsServiceWithAmxHardAlias.sendAuctionRequest(bidRequest)

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
    }

    def "PBS should prefer AMX hard alias over conflicting soft alias"() {
        given: "Bid request with AMX hard alias and conflicting soft alias"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.amx = null
            imp[0].ext.prebid.bidder.generic = null
            it.ext.prebid.aliases = [(ALIAS.value): GENERIC]
            it.ext.prebid.targeting = new Targeting()
        }

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxHardAlias.sendAuctionRequest(bidRequest)

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
    }

    def "PBS should prefer Generic hard alias over conflicting soft alias"() {
        given: "Bid request with Generic hard alias and conflicting soft alias"
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.amx = null
            imp[0].ext.prebid.bidder.generic = null
            it.ext.prebid.aliases = [(ALIAS.value): AMX]
            it.ext.prebid.targeting = new Targeting()
        }

        when: "PBS processes auction request"
        def response = pbsServiceWithGenericHardAlias.sendAuctionRequest(bidRequest)

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
    }

    def "PBS should preserve material AMX bidder despite conflicting soft alias"() {
        given: "Bid request with AMX material bidder and conflicting soft alias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.amx = new Amx()
            imp[0].ext.prebid.bidder.generic = null
            ext.prebid.targeting = new Targeting()
            ext.prebid.aliases = [(AMX.value): GENERIC]
        }

        when: "PBS processes auction request"
        def response = pbsServiceWithGenericHardAlias.sendAuctionRequest(bidRequest)

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
    }
}
