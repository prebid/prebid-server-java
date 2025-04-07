package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AlternateBidderCodes
import org.prebid.server.functional.model.config.BidderConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredImp
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.auction.Amx
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.model.request.auction.StoredBidResponse
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.response.auction.BidExt
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Shared

import static org.prebid.server.functional.model.AccountStatus.ACTIVE
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS_CAMEL_CASE
import static org.prebid.server.functional.model.bidder.BidderName.AMX
import static org.prebid.server.functional.model.bidder.BidderName.AMX_CAMEL_CASE
import static org.prebid.server.functional.model.bidder.BidderName.BOGUS
import static org.prebid.server.functional.model.bidder.BidderName.EMPTY
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC_CAMEL_CASE
import static org.prebid.server.functional.model.bidder.BidderName.UNKNOWN
import static org.prebid.server.functional.model.bidder.BidderName.WILDCARD
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.RESPONSE_REJECTED_GENERAL
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class AlternateBidderCodeSpec extends BaseSpec {

    private static final String ADAPTER_RESPONSE_VALIDATION_METRICS = "adapter.%s.response.validation.seat"
    private static final String ERROR_BID_CODE_VALIDATION = "BidId `%s` validation messages: " +
            "Error: invalid bidder code %s was set by the adapter %s for the account %s"
    private static final String INVALID_BIDDER_CODE_LOGS = "invalid bidder code %s was set by the adapter %s for the account %s"
    private static final Map AMX_CONFIG = ["adapters.amx.enabled" : "true",
                                           "adapters.amx.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
    @Shared
    private static final PrebidServerService pbsServiceWithAmxBidder = pbsServiceFactory.getService(AMX_CONFIG)

    @Override
    def cleanupSpec() {
        pbsServiceFactory.removeContainer(AMX_CONFIG)
    }

    def "PBS shouldn't discard bid amx alias when soft alias request with allowed bidder code"() {
        given: "Default bid request with amx bidder"
        def bidRequest = getBidRequestWithAmxBidder().tap {
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.amx = null
            ext.prebid.aliases = [(ALIAS.value): AMX]
        }

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: ALIAS)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seat"
        assert response.seatbid.seat == [ALIAS]

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${ALIAS}"]
        assert targeting["hb_size_${ALIAS}"]
        assert targeting["hb_bidder"] == ALIAS.value
        assert targeting["hb_bidder_${ALIAS}"] == ALIAS.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(ALIAS.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "PBS shouldn't emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(ALIAS)]
    }

    def "PBS should populate meta demand source when bid response with demand source"() {
        given: "Default bid request with amx bidder"
        def bidRequest = getBidRequestWithAmxBidder()

        and: "Bid response with demand source"
        def demandSource = PBSUtils.getRandomString()
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(demandSource: demandSource)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seat"
        assert response.seatbid.seat == [AMX]

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain demand source"
        assert response.seatbid.bid.ext.prebid.meta.demandSource.flatten() == [demandSource]

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

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBS shouldn't emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]
    }

    def "PBS shouldn't populate meta demand source when bid response without demand source"() {
        given: "Default bid request with amx bidder"
        def bidRequest = getBidRequestWithAmxBidder()

        and: "Bid response without demand source"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(demandSource: null)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seat"
        assert response.seatbid.seat == [AMX]

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

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

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBS shouldn't emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]
    }

    def "PBS shouldn't discard bid for amx bidder same seat in response as seat in bid.ext.bidderCode"() {
        given: "Default bid request with amx bidder"
        def bidRequest = getBidRequestWithAmxBidder()

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: bidderCode)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seat"
        assert response.seatbid.seat == [bidderCode]

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${bidderCode}"]
        assert targeting["hb_size_${bidderCode}"]
        assert targeting["hb_bidder"] == bidderCode.value
        assert targeting["hb_bidder_${bidderCode}"] == bidderCode.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(bidderCode.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "PBS shouldn't emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]

        where:
        bidderCode << [AMX, AMX_CAMEL_CASE]
    }

    def "PBS should discard bid for amx bidder when imp[].bidder isn't same as in bid.ext.bidderCode"() {
        given: "Default bid request with amx bidder"
        def bidRequest = getBidRequestWithAmxBidder()

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: bidderName)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Bid response shouldn't seat bid"
        assert response.seatbid.isEmpty()

        and: "Response should seatNon bid with code 300"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == bidderName.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_GENERAL

        and: "Response should contain error"
        def error = response.ext?.errors[ErrorType.AMX][0]
        assert error.code == 5
        assert error.message == ERROR_BID_CODE_VALIDATION
                .formatted(bidResponse.seatbid[0].bid[0].id, bidderName, AMX, bidRequest.accountId)

        and: "PBS should emit logs"
        def logs = pbsServiceWithAmxBidder.getLogsByValue(bidRequest.accountId)
        assert logs.contains(INVALID_BIDDER_CODE_LOGS.formatted(bidderName, AMX, bidRequest.accountId))

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(AMX.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should emit metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]

        where:
        bidderName << [BOGUS, UNKNOWN, WILDCARD]
    }

    def "PBS should discard bid amx alias requested when imp[].bidder isn't same as in bid.ext.bidderCode"() {
        given: "Default bid request with amx bidder"
        def bidRequest = getBidRequestWithAmxBidder().tap {
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.amx = null
            ext.prebid.aliases = [(ALIAS.value): AMX]
        }

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: bidderName)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Bid response shouldn't seat bid"
        assert response.seatbid.isEmpty()

        and: "Response should seatNon bid with code 300"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == bidderName.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_GENERAL

        and: "Response should contain error"
        def error = response.ext?.errors[ErrorType.ALIAS][0]
        assert error.code == 5
        assert error.message == ERROR_BID_CODE_VALIDATION
                .formatted(bidResponse.seatbid[0].bid[0].id, bidderName, ALIAS, bidRequest.accountId)

        and: "PBS should emit logs"
        def logs = pbsServiceWithAmxBidder.getLogsByValue(bidRequest.accountId)
        assert logs.contains(INVALID_BIDDER_CODE_LOGS.formatted(bidderName, ALIAS, bidRequest.accountId))

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(ALIAS.value)

        and: "PBS should emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(ALIAS)]

        where:
        bidderName << [BOGUS, UNKNOWN, WILDCARD]
    }

    def "PBS shouldn't discard bid amx alias requested when imp[].bidder is same as in bid.ext.bidderCode and alternate bidder code allow"() {
        given: "Default bid request with amx bidder"
        def bidRequest = getBidRequestWithAmxBidderAndAlternateBidderCode().tap {
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.amx = null
            ext.prebid.aliases = [(ALIAS.value): AMX]
            ext.prebid.alternateBidderCodes = requestAlternateBidderCode
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = getAccountWithAlternateBidderCode(bidRequest).tap {
            config.alternateBidderCodes = accountAlternateBidderCodes
        }
        accountDao.save(account)

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, ALIAS).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: GENERIC)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.seat == [GENERIC]

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${GENERIC}"]
        assert targeting["hb_size_${GENERIC}"]
        assert targeting["hb_bidder"] == GENERIC.value
        assert targeting["hb_bidder_${GENERIC}"] == GENERIC.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(GENERIC.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBS shouldn't emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(ALIAS)]

        where:
        requestAlternateBidderCode                                                                                                  | accountAlternateBidderCodes
        new AlternateBidderCodes(enabled: true, bidders: [(ALIAS): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]) | null
        null                                                                                                                        | new AlternateBidderCodes(enabled: true, bidders: [(ALIAS): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])])
    }

    def "PBS shouldn't discard bid amx alias requested when imp[].bidder is same as in bid.ext.bidderCode"() {
        given: "Default bid request with amx bidder"
        def bidRequest = getBidRequestWithAmxBidder().tap {
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.amx = null
            ext.prebid.aliases = [(ALIAS.value): AMX]
        }

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, ALIAS).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: bidderCode)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.seat == [bidderCode]

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${bidderCode}"]
        assert targeting["hb_size_${bidderCode}"]
        assert targeting["hb_bidder"] == bidderCode.value
        assert targeting["hb_bidder_${bidderCode}"] == bidderCode.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(bidderCode.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBS shouldn't emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(bidderCode)]

        where:
        bidderCode << [ALIAS, ALIAS_CAMEL_CASE]
    }

    def "PBS shouldn't discard the bid or emit a response warning when account alternate bidder codes not fully configured"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = getBidRequestWithAmxBidder()

        and: "Save account config into DB with alternate bidder codes"
        def account = getAccountWithAlternateBidderCode(bidRequest).tap {
            config.alternateBidderCodes = accountAlternateBidderCodes
        }
        accountDao.save(account)

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: AMX)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain seatbid.seat"
        assert response.seatbid[0].seat == AMX

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

        and: "Response shouldn't contain warnings,errors and seatnonbid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "Metric shouldn't be updated"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]

        where:
        accountAlternateBidderCodes << [null,
                                        new AlternateBidderCodes(),
                                        new AlternateBidderCodes(enabled: true),
                                        new AlternateBidderCodes(enabled: false),
                                        new AlternateBidderCodes(bidders: [(AMX): new BidderConfig()]),
                                        new AlternateBidderCodes(bidders: [(UNKNOWN): new BidderConfig()]),
                                        new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig()]),
                                        new AlternateBidderCodes(enabled: true, bidders: [(UNKNOWN): new BidderConfig()]),
                                        new AlternateBidderCodes(enabled: false, bidders: [(UNKNOWN): new BidderConfig()]),
                                        new AlternateBidderCodes(enabled: false, bidders: [(AMX): new BidderConfig()]),
                                        new AlternateBidderCodes(bidders: [(AMX): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])]),
                                        new AlternateBidderCodes(bidders: [(UNKNOWN): new BidderConfig(enabled: false, allowedBidderCodes: [AMX])]),
                                        new AlternateBidderCodes(enabled: false, bidders: [(AMX): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])]),
                                        new AlternateBidderCodes(enabled: false, bidders: [(UNKNOWN): new BidderConfig(enabled: false, allowedBidderCodes: [AMX])]),
                                        new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])]),
                                        new AlternateBidderCodes(enabled: true, bidders: [(UNKNOWN): new BidderConfig(enabled: false, allowedBidderCodes: [AMX])])]
    }

    def "PBS shouldn't discard the bid or emit a response warning when request alternate bidder codes not fully configured"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = getBidRequestWithAmxBidderAndAlternateBidderCode().tap {
            ext.prebid.alternateBidderCodes = requestedAlternateBidderCodes
        }

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: AMX)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain seatbid.seat"
        assert response.seatbid[0].seat == AMX

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${AMX}"]
        assert targeting["hb_size_${AMX}"]
        assert targeting["hb_bidder"] == AMX.value
        assert targeting["hb_bidder_${AMX}"] == AMX.value

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings,errors and seatnonbid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(AMX.value)

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "Metric shouldn't be updated"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]

        where:
        requestedAlternateBidderCodes << [null,
                                          new AlternateBidderCodes(),
                                          new AlternateBidderCodes(enabled: true),
                                          new AlternateBidderCodes(enabled: false),
                                          new AlternateBidderCodes(bidders: [(AMX): new BidderConfig()]),
                                          new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig()]),
                                          new AlternateBidderCodes(enabled: false, bidders: [(AMX): new BidderConfig()]),
                                          new AlternateBidderCodes(bidders: [(AMX): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])]),
                                          new AlternateBidderCodes(enabled: false, bidders: [(AMX): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])]),
                                          new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])]),]
    }

    def "PBS should validate and throw error when request alternate bidder codes not fully configured"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = getBidRequestWithAmxBidderAndAlternateBidderCode().tap {
            ext.prebid.alternateBidderCodes = requestedAlternateBidderCodes
        }

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: AMX)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == "Invalid request format: " +
                "request.ext.prebid.alternatebiddercodes.bidders.unknown is not a known bidder or alias"


        where:
        requestedAlternateBidderCodes << [new AlternateBidderCodes(bidders: [(UNKNOWN): new BidderConfig()]),
                                          new AlternateBidderCodes(enabled: true, bidders: [(UNKNOWN): new BidderConfig()]),
                                          new AlternateBidderCodes(enabled: false, bidders: [(UNKNOWN): new BidderConfig()]),
                                          new AlternateBidderCodes(bidders: [(UNKNOWN): new BidderConfig(enabled: false, allowedBidderCodes: [AMX])]),
                                          new AlternateBidderCodes(enabled: false, bidders: [(UNKNOWN): new BidderConfig(enabled: false, allowedBidderCodes: [AMX])]),
                                          new AlternateBidderCodes(enabled: true, bidders: [(UNKNOWN): new BidderConfig(enabled: false, allowedBidderCodes: [AMX])])]
    }

    def "PBS shouldn't discard bid when alternate bidder code allows bidder codes fully configured and bidder requested in uppercase"() {
        given: "Default bid request with AMX bidder"
        def bidRequest = getBidRequestWithAmxBidderAndAlternateBidderCode().tap {
            imp[0].ext.prebid.bidder.tap {
                amxUpperCase = new Amx()
                amx = null
            }
            ext.prebid.alternateBidderCodes.bidders[AMX].allowedBidderCodesLowerCase = [GENERIC]
        }

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: GENERIC)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain seatbid.seat"
        assert response.seatbid[0].seat == GENERIC

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${GENERIC}"]
        assert targeting["hb_size_${GENERIC}"]
        assert targeting["hb_bidder"] == GENERIC.value
        assert targeting["hb_bidder_${GENERIC}"] == GENERIC.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(GENERIC.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "Response shouldn't contain warnings,errors and seatnonbid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "Metric shouldn't be updated"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(GENERIC)]
    }

    def "PBS shouldn't discard bid when alternate bidder code allows bidder codes fully configured with different case"() {
        given: "Default bid request with amx bidder"
        def bidRequest = getBidRequestWithAmxBidder()

        and: "Save account config into DB with alternate bidder codes"
        def account = getAccountWithAlternateBidderCode(bidRequest).tap {
            config = configAccountAlternateBidderCodes
        }
        accountDao.save(account)

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: GENERIC)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain seatbid.seat"
        assert response.seatbid[0].seat == GENERIC

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${GENERIC}"]
        assert targeting["hb_size_${GENERIC}"]
        assert targeting["hb_bidder"] == GENERIC.value
        assert targeting["hb_bidder_${GENERIC}"] == GENERIC.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(GENERIC.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings,errors and seatnonbid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "Metric shouldn't be updated"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]

        where:
        configAccountAlternateBidderCodes << [
                new AccountConfig(alternateBidderCodesSnakeCase: new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig(enabled: true, allowedBidderCodesSnakeCase: [GENERIC])])),
                new AccountConfig(alternateBidderCodes: new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig(enabled: true, allowedBidderCodesSnakeCase: [GENERIC])])),
                new AccountConfig(alternateBidderCodesSnakeCase: new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]))]
    }

    def "PBS should take precede of request and discard the bid and emit a response error when alternate bidder codes enabled and bidder came with different bidder code"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = getBidRequestWithAmxBidderAndAlternateBidderCode()

        and: "Save account config into DB with alternate bidder codes"
        def account = getAccountWithAlternateBidderCode(bidRequest).tap {
            config.alternateBidderCodes.bidders[AMX].allowedBidderCodes = [UNKNOWN]
        }
        accountDao.save(account)

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: UNKNOWN)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Bid response shouldn't seat bid"
        assert response.seatbid.isEmpty()

        and: "Response should seatNon bid with code 300"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == UNKNOWN.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_GENERAL

        and: "Response should contain error"
        def error = response.ext?.errors[ErrorType.AMX][0]
        assert error.code == 5
        assert error.message == ERROR_BID_CODE_VALIDATION
                .formatted(bidResponse.seatbid[0].bid[0].id, UNKNOWN, AMX, bidRequest.accountId)

        and: "PBS should emit logs"
        def logs = pbsServiceWithAmxBidder.getLogsByValue(bidRequest.accountId)
        assert logs.contains(INVALID_BIDDER_CODE_LOGS.formatted(UNKNOWN, AMX, bidRequest.accountId))

        and: "PBS should emit metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(AMX.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)
    }

    def "PBS should discard the bid and emit a response warning when alternate bidder codes disabled and bidder came with different bidderCode"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = getBidRequestWithAmxBidderAndAlternateBidderCode().tap {
            ext.prebid.alternateBidderCodes.enabled = requestedAlternateBidderCodes
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = getAccountWithAlternateBidderCode(bidRequest).tap {
            config.alternateBidderCodes.enabled = accountAlternateBidderCodes
        }
        accountDao.save(account)

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: UNKNOWN)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Bid response shouldn't seat bid"
        assert response.seatbid.isEmpty()

        and: "Response should seatNon bid with code 300"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == UNKNOWN.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_GENERAL

        and: "Response should contain error"
        def error = response.ext?.errors[ErrorType.AMX][0]
        assert error.code == 5
        assert error.message == ERROR_BID_CODE_VALIDATION
                .formatted(bidResponse.seatbid[0].bid[0].id, UNKNOWN, AMX, bidRequest.accountId)

        and: "PBS should emit logs"
        def logs = pbsServiceWithAmxBidder.getLogsByValue(bidRequest.accountId)
        assert logs.contains(INVALID_BIDDER_CODE_LOGS.formatted(UNKNOWN, AMX, bidRequest.accountId))

        and: "PBS should emit metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(AMX.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        where:
        requestedAlternateBidderCodes | accountAlternateBidderCodes
        false                         | true
        false                         | false
        false                         | null
        null                          | false
    }

    def "PBS shouldn't discard the bid or emit a response warning when account alternate bidder codes are enabled and allowed bidder codes are either a wildcard or empty"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = getBidRequestWithAmxBidder()

        and: "Save account config into DB with alternate bidder codes"
        def account = getAccountWithAlternateBidderCode(bidRequest).tap {
            config.alternateBidderCodes.bidders[AMX].allowedBidderCodes = accountAllowedBidderCodes
        }
        accountDao.save(account)

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: GENERIC)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain seatbid.seat"
        assert response.seatbid.seat.flatten() == [GENERIC]

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${GENERIC}"]
        assert targeting["hb_size_${GENERIC}"]
        assert targeting["hb_bidder"] == GENERIC.value
        assert targeting["hb_bidder_${GENERIC}"] == GENERIC.value

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(GENERIC.value)

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "Response shouldn't contain warnings and errors and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBs metric shouldn't be updated"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]

        where:
        accountAllowedBidderCodes << [[WILDCARD], [WILDCARD, EMPTY], [EMPTY, WILDCARD], null]
    }

    def "PBS shouldn't discard the bid or emit a response warning when request alternate bidder codes are enabled and allowed bidder codes are either a wildcard or empty"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = getBidRequestWithAmxBidderAndAlternateBidderCode().tap {
            ext.prebid.alternateBidderCodes.bidders[AMX].allowedBidderCodesLowerCase = requestedAllowedBidderCodes
        }

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: GENERIC)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain seatbid.seat"
        assert response.seatbid.seat.flatten() == [GENERIC]

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${GENERIC}"]
        assert targeting["hb_size_${GENERIC}"]
        assert targeting["hb_bidder"] == GENERIC.value
        assert targeting["hb_bidder_${GENERIC}"] == GENERIC.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(GENERIC.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and errors and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "PBs metric shouldn't be updated"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]

        where:
        requestedAllowedBidderCodes << [[WILDCARD], [WILDCARD, EMPTY], [EMPTY, WILDCARD], null]
    }

    def "PBS shouldn't discard the bid or emit a response warning when request alternate bidder codes are enabled and the allowed bidder codes is same as bidder's request"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = getBidRequestWithAmxBidderAndAlternateBidderCode().tap {
            ext.prebid.alternateBidderCodes.bidders = requestAlternateBidders
        }

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: GENERIC)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain seatbid.seat"
        assert response.seatbid.seat.flatten() == [GENERIC]

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${GENERIC}"]
        assert targeting["hb_size_${GENERIC}"]
        assert targeting["hb_bidder"] == GENERIC.value
        assert targeting["hb_bidder_${GENERIC}"] == GENERIC.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(GENERIC.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and errors and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBs metric shouldn't be updated"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]

        where:
        requestAlternateBidders << [[(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])],
                                    [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC_CAMEL_CASE])],
                                    [(AMX_CAMEL_CASE): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC_CAMEL_CASE])],
                                    [(AMX_CAMEL_CASE): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]]
    }

    def "PBS shouldn't discard the bid or emit a response warning when account alternate bidder codes are enabled and the allowed bidder codes is same as bidder's request"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = getBidRequestWithAmxBidder()

        and: "Save account config into DB with alternate bidder codes"
        def account = getAccountWithAlternateBidderCode(bidRequest).tap {
            config.alternateBidderCodes.bidders = accountAlternateBidders
        }
        accountDao.save(account)

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: GENERIC)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain seatbid.seat"
        assert response.seatbid.seat.flatten() == [GENERIC]

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${GENERIC}"]
        assert targeting["hb_size_${GENERIC}"]
        assert targeting["hb_bidder"] == GENERIC.value
        assert targeting["hb_bidder_${GENERIC}"] == GENERIC.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(GENERIC.value)

        and: "Response shouldn't contain warnings and errors and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBs metric shouldn't be updated"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]

        where:
        accountAlternateBidders << [[(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])],
                                    [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC_CAMEL_CASE])],
                                    [(AMX_CAMEL_CASE): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC_CAMEL_CASE])],
                                    [(AMX_CAMEL_CASE): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]]
    }

    def "PBS shouldn't discard the bid or emit a response warning when default account alternate bidder codes are enabled and the allowed bidder codes match the bidder's request"() {
        given: "Pbs config with default-account-config of alternate bidder code"
        def defaultAccountConfig = AccountConfig.defaultAccountConfig.tap {
            alternateBidderCodes = new AlternateBidderCodes().tap {
                it.enabled = true
                it.bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [AMX])]
            }
        }
        def config = AMX_CONFIG + ["settings.default-account-config": encode(defaultAccountConfig)]
        def pbsService = pbsServiceFactory.getService(config)

        and: "Default bid request"
        def bidRequest = getBidRequestWithAmxBidderAndAlternateBidderCode().tap {
            ext.prebid.alternateBidderCodes = null
        }

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: AMX)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsService)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "Response should contain seatbid.seat"
        assert response.seatbid.seat.flatten() == [AMX]

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

        and: "Response shouldn't contain warnings and errors and seatnonbid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "Alert.general metric shouldn't be updated"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(config)
    }

    def "PBS should discard the bid and emit a response warning when request alternate bidder codes are enabled and the allowed bidder codes doesn't match the bidder's request"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = getBidRequestWithAmxBidderAndAlternateBidderCode()

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: requestedAllowedBidderCode)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Bid response shouldn't seat bid"
        assert response.seatbid.isEmpty()

        and: "Response should seatNon bid with code 300"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == requestedAllowedBidderCode.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_GENERAL

        and: "Response should contain error"
        def error = response.ext?.errors[ErrorType.AMX][0]
        assert error.code == 5
        assert error.message == ERROR_BID_CODE_VALIDATION
                .formatted(bidResponse.seatbid[0].bid[0].id, requestedAllowedBidderCode, AMX, bidRequest.accountId)

        and: "PBS should emit logs"
        def logs = pbsServiceWithAmxBidder.getLogsByValue(bidRequest.accountId)
        assert logs.contains(INVALID_BIDDER_CODE_LOGS.formatted(requestedAllowedBidderCode, AMX, bidRequest.accountId))

        and: "PBS should emit metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(AMX.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        where:
        requestedAllowedBidderCode << [UNKNOWN, BOGUS]
    }

    def "PBS should discard the bid and emit a response warning when account alternate bidder codes are enabled and the allowed bidder codes doesn't match the bidder's request"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = getBidRequestWithAmxBidder()

        and: "Save account config into DB with alternate bidder codes"
        def account = getAccountWithAlternateBidderCode(bidRequest)
        accountDao.save(account)

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: requestedAllowedBidderCode)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Bid response shouldn't seat bid"
        assert response.seatbid.isEmpty()

        and: "Response should seatNon bid with code 300"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == requestedAllowedBidderCode.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_GENERAL

        and: "Response should contain error"
        def error = response.ext?.errors[ErrorType.AMX][0]
        assert error.code == 5
        assert error.message == ERROR_BID_CODE_VALIDATION
                .formatted(bidResponse.seatbid[0].bid[0].id, requestedAllowedBidderCode, AMX, bidRequest.accountId)

        and: "PBS should emit logs"
        def logs = pbsServiceWithAmxBidder.getLogsByValue(bidRequest.accountId)
        assert logs.contains(INVALID_BIDDER_CODE_LOGS.formatted(requestedAllowedBidderCode, AMX, bidRequest.accountId))

        and: "PBS should emit metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(AMX.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        where:
        requestedAllowedBidderCode << [UNKNOWN, BOGUS]
    }

    def "PBS should discard the bid and emit a response warning when default account alternate bidder codes are enabled and the allowed bidder codes doesn't match the bidder's request"() {
        given: "Pbs config with default-account-config"
        def defaultAccountConfig = AccountConfig.defaultAccountConfig.tap {
            alternateBidderCodes = new AlternateBidderCodes().tap {
                it.enabled = true
                it.bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [AMX])]
            }
        }
        def pbsConfig = AMX_CONFIG + ["settings.default-account-config": encode(defaultAccountConfig)]
        def pbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request"
        def bidRequest = getBidRequestWithAmxBidder()

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: allowedBidderCodes)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsService)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Bid response shouldn't seat bid"
        assert response.seatbid.isEmpty()

        and: "Response should seatNon bid with code 300"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == allowedBidderCodes.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_GENERAL

        and: "Response should contain error"
        def error = response.ext?.errors[ErrorType.AMX][0]
        assert error.code == 5
        assert error.message == ERROR_BID_CODE_VALIDATION
                .formatted(bidResponse.seatbid[0].bid[0].id, allowedBidderCodes, AMX, bidRequest.accountId)

        and: "PBS should emit logs"
        def logs = pbsService.getLogsByValue(bidRequest.accountId)
        assert logs.contains(INVALID_BIDDER_CODE_LOGS.formatted(allowedBidderCodes, AMX, bidRequest.accountId))

        and: "Response shouldn't contain demand source"
        assert !response?.seatbid?.bid?.ext?.prebid?.meta?.demandSource

        and: "PBS should emit metrics"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(AMX.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        allowedBidderCodes << [BOGUS, UNKNOWN]
    }

    def "PBS shouldn't discard bid when hard alias and alternate bidder allow bidder code"() {
        given: "PBS config with bidder"
        def pbsConfig = AMX_CONFIG + ["adapters.amx.aliases.alias.enabled" : "true",
                                      "adapters.amx.aliases.alias.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
        def defaultPbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with alias"
        def bidRequest = getBidRequestWithAmxBidderAndAlternateBidderCode().tap {
            imp[0].ext.prebid.bidder.tap {
                amx = null
                alias = new Generic()
            }
            ext.prebid.alternateBidderCodes.bidders = [(ALIAS): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]
        }

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, ALIAS).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: GENERIC)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [ALIAS]

        and: "Response should contain seat bid"
        assert response.seatbid.seat == [GENERIC]

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${GENERIC}"]
        assert targeting["hb_size_${GENERIC}"]
        assert targeting["hb_bidder"] == GENERIC.value
        assert targeting["hb_bidder_${GENERIC}"] == GENERIC.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(GENERIC.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "PBS shouldn't emit validation metrics"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(GENERIC)]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS shouldn't discard bid when alternate bidder code allow and soft alias with case"() {
        given: "Default bid request with amx bidder"
        def bidRequest = getBidRequestWithAmxBidderAndAlternateBidderCode().tap {
            imp[0].ext.prebid.bidder.aliasUpperCase = new Generic()
            imp[0].ext.prebid.bidder.amx = null
            ext.prebid.aliases = [(ALIAS.value): AMX]
            ext.prebid.alternateBidderCodes = requestAlternateBidderCode
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = getAccountWithAlternateBidderCode(bidRequest).tap {
            config.alternateBidderCodes = accountAlternateBidderCodes
        }
        accountDao.save(account)

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, ALIAS).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: GENERIC)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.seat == [GENERIC]

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${GENERIC}"]
        assert targeting["hb_size_${GENERIC}"]
        assert targeting["hb_bidder"] == GENERIC.value
        assert targeting["hb_bidder_${GENERIC}"] == GENERIC.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(GENERIC.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBS shouldn't emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(ALIAS)]
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(GENERIC)]
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]

        where:
        requestAlternateBidderCode                                                                                                  | accountAlternateBidderCodes
        new AlternateBidderCodes(enabled: true, bidders: [(ALIAS): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]) | null
        null                                                                                                                        | new AlternateBidderCodes(enabled: true, bidders: [(ALIAS): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])])
    }

    def "PBS shouldn't discard bid when alternate bidder code allow and soft alias with case with base bidder in alternate bidder code"() {
        given: "Default bid request with amx bidder"
        def bidRequest = getBidRequestWithAmxBidderAndAlternateBidderCode().tap {
            imp[0].ext.prebid.bidder.aliasUpperCase = new Generic()
            imp[0].ext.prebid.bidder.amx = null
            ext.prebid.aliases = [(ALIAS.value): AMX]
            ext.prebid.alternateBidderCodes = requestAlternateBidderCode
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = getAccountWithAlternateBidderCode(bidRequest).tap {
            config.alternateBidderCodes = accountAlternateBidderCodes
        }
        accountDao.save(account)

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, ALIAS).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: GENERIC)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.seat == [GENERIC]

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${GENERIC}"]
        assert targeting["hb_size_${GENERIC}"]
        assert targeting["hb_bidder"] == GENERIC.value
        assert targeting["hb_bidder_${GENERIC}"] == GENERIC.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(GENERIC.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBS shouldn't emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(ALIAS)]
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(GENERIC)]
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]

        where:
        requestAlternateBidderCode                                                                                                | accountAlternateBidderCodes
        new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]) | null
        null                                                                                                                      | new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])])
    }

    def "PBS should populate adapter code with requested bidder when conflict soft and hard alias and alternate bidder code"() {
        given: "PBS config with bidder"
        def pbsConfig = AMX_CONFIG + ["adapters.amx.aliases.alias.enabled" : "true",
                                      "adapters.amx.aliases.alias.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
        def defaultPbsService = pbsServiceFactory.getService(pbsConfig)

        and: "Bid request with amx bidder and targeting"
        def bidRequest = getBidRequestWithAmxBidderAndAlternateBidderCode().tap {
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.amx = null
            imp[0].ext.prebid.bidder.generic = null
            it.ext.prebid.aliases = [(ALIAS.value): GENERIC]
            it.ext.prebid.alternateBidderCodes.bidders = [(ALIAS): new BidderConfig(enabled: true, allowedBidderCodesLowerCase: [GENERIC])]
        }

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, ALIAS).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: GENERIC)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [ALIAS]

        and: "Response should contain seat bid"
        assert response.seatbid.seat == [GENERIC]

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${GENERIC}"]
        assert targeting["hb_size_${GENERIC}"]
        assert targeting["hb_bidder"] == GENERIC.value
        assert targeting["hb_bidder_${GENERIC}"] == GENERIC.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(GENERIC.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS shouldn't emit validation metrics"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(GENERIC)]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should populate two seat bid when different bidder response with same seat"() {
        given: "Default bid request with amx and generic bidder"
        def bidRequest = getBidRequestWithAmxBidderAndAlternateBidderCode().tap {
            imp[0].ext.prebid.bidder.generic = new Generic()
            ext.prebid.alternateBidderCodes.bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]
        }

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: GENERIC)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seat"
        assert response.seatbid.seat.sort() == [GENERIC, GENERIC].sort()

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX, GENERIC]

        and: "Response should contain bidder amx targeting"
        def targeting = response.seatbid.bid.ext.prebid.targeting.flatten().collectEntries()
        assert targeting["hb_pb_${GENERIC}"]
        assert targeting["hb_size_${GENERIC}"]
        assert targeting["hb_bidder_${GENERIC}"] == GENERIC.value

        and: 'Response targeting should contain generic'
        assert targeting["hb_pb_${GENERIC}"]
        assert targeting["hb_size_${GENERIC}"]
        assert targeting["hb_bidder_${GENERIC}"] == GENERIC.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(GENERIC.value)

        and: "Response should contain repose millis with amx bidder"
        assert !response.ext.responsetimemillis.containsKey(AMX.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "PBS shouldn't emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(GENERIC)]
    }

    def "PBS should return two seat when same bidder response with different bidder code"() {
        given: "Default bid request with amx and generic bidder"
        def bidRequest = getBidRequestWithAmxBidderAndAlternateBidderCode().tap {
            imp[0].ext.prebid.bidder.amx = new Amx()
            imp.add(Imp.getDefaultImpression())
            imp[1].ext.prebid.bidder.amx = new Amx()
            imp[1].ext.prebid.bidder.generic = null
            ext.prebid.alternateBidderCodes.bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC, AMX])]
        }

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: GENERIC)
            it.seatbid[0].bid[1].ext = new BidExt(bidderCode: AMX)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seat"
        assert response.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX, AMX]

        and: "Response should contain bidder amx targeting"
        def targeting = response.seatbid.bid.ext.prebid.targeting.flatten().collectEntries()
        assert targeting["hb_pb_${AMX}"]
        assert targeting["hb_size_${AMX}"]
        assert targeting["hb_bidder_${AMX}"] == AMX.value

        and: 'Response targeting should contain generic'
        assert targeting["hb_pb_${GENERIC}"]
        assert targeting["hb_size_${GENERIC}"]
        assert targeting["hb_bidder_${GENERIC}"] == GENERIC.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(GENERIC.value)
        assert response.ext.responsetimemillis.containsKey(AMX.value)

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "PBS shouldn't emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(GENERIC)]
    }

    def "PBS should populate seat bid from stored bid response when stored bid response and alternate bidder code specified"() {
        given: "Default bid request with amx bidder"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = getBidRequestWithAmxBidderAndAlternateBidderCode().tap {
            imp[0].ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: AMX)]
            ext.prebid.alternateBidderCodes.bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]
        }

        and: "Stored bid response in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: GENERIC)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithAmxBidder)

        when: "PBS processes auction request"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seat"
        assert response.seatbid.seat.sort() == [AMX].sort()

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain bidder generic targeting"
        def targeting = response.seatbid.bid.ext.prebid.targeting.flatten().collectEntries()
        assert targeting["hb_pb_${AMX}"]
        assert targeting["hb_size_${AMX}"]
        assert targeting["hb_bidder_${AMX}"] == AMX.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(AMX.value)

        and: "Bidder request shouldn't be called due to storedBidResponse"
        assert !bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "PBS shouldn't emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]
    }

    def "PBS auction allow bidder code when imp stored request and allowed bidder code present"() {
        given: "Default bid request"
        def bidRequest = getBidRequestWithAmxBidderAndAlternateBidderCode().tap {
            imp[0].ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomString)
            ext.prebid.alternateBidderCodes.bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]
        }

        and: "Save storedImp into DB"
        def storedImp = StoredImp.getStoredImp(bidRequest)
        storedImpDao.save(storedImp)

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: GENERIC)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = pbsServiceWithAmxBidder.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seat"
        assert response.seatbid.seat.sort() == [GENERIC].sort()

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain bidder generic targeting"
        def targeting = response.seatbid.bid.ext.prebid.targeting.flatten().collectEntries()
        assert targeting["hb_pb_${GENERIC}"]
        assert targeting["hb_size_${GENERIC}"]
        assert targeting["hb_bidder_${GENERIC}"] == GENERIC.value

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(GENERIC.value)

        and: "Bidder request should be called"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "Response shouldn't contain demand source"
        assert !response.seatbid.first.bid.first.ext.prebid.meta.demandSource

        and: "PBS shouldn't emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(GENERIC)]
        assert !metrics[ADAPTER_RESPONSE_VALIDATION_METRICS.formatted(AMX)]
    }

    private static Account getAccountWithAlternateBidderCode(BidRequest bidRequest) {
        new Account().tap {
            it.uuid = bidRequest.accountId
            it.config = new AccountConfig(status: ACTIVE, alternateBidderCodes: new AlternateBidderCodes().tap {
                it.enabled = true
                it.bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [AMX])]
            })
        }
    }

    private static BidRequest getBidRequestWithAmxBidderAndAlternateBidderCode() {
        getBidRequestWithAmxBidder().tap {
            it.ext.prebid.alternateBidderCodes = new AlternateBidderCodes().tap {
                enabled = true
                bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodesLowerCase: [AMX])]
            }
        }
    }

    private static BidRequest getBidRequestWithAmxBidder() {
        BidRequest.defaultBidRequest.tap {
            it.imp[0].ext.prebid.bidder.tap {
                generic = null
                amx = new Amx()
            }
            ext.prebid.tap {
                returnAllBidStatus = true
                targeting = new Targeting()
            }
        }
    }
}
