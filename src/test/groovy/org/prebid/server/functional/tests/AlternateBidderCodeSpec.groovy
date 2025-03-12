package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AlternateBidderCodes
import org.prebid.server.functional.model.config.BidderConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.Amx
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.response.auction.BidExt
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils
import spock.lang.IgnoreRest
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
        def bidRequest = bidRequestWithAmxBidder().tap {
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
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [ALIAS]

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${ALIAS}"]
        assert targeting["hb_size_${ALIAS}"]
        assert targeting["hb_bidder"] == ALIAS.value
        assert targeting["hb_bidder_${ALIAS}"] == ALIAS.value

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBS shouldn't emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics["adapter.${ALIAS}.response.validation.seat"]
    }

    def "PBS should populate meta demand source when bid response with demand source"() {
        given: "Default bid request with amx bidder"
        def bidRequest = bidRequestWithAmxBidder()

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

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBS shouldn't emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics["adapter.${AMX}.response.validation.seat"]
    }

    def "PBS shouldn't populate meta demand source when bid response without demand source"() {
        given: "Default bid request with amx bidder"
        def bidRequest = bidRequestWithAmxBidder()

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

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBS shouldn't emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics["adapter.${AMX}.response.validation.seat"]
    }

    def "PBS shouldn't discard bid for amx bidder same seat in response as seat in bid.ext.bidderCode"() {
        given: "Default bid request with amx bidder"
        def bidRequest = bidRequestWithAmxBidder()

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

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBS shouldn't emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics["adapter.${AMX}.response.validation.seat"]

        where:
        bidderCode << [AMX, AMX_CAMEL_CASE]
    }

    def "PBS should discard bid for amx bidder when imp[].bidder isn't same as in bid.ext.bidderCode"() {
        given: "Default bid request with amx bidder"
        def bidRequest = bidRequestWithAmxBidder()

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
        assert seatNonBid.seat == AMX.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_GENERAL

        and: "Response should contain error"
        def error = response.ext?.errors[ErrorType.AMX][0]
        assert error.code == 5
        assert error.message == "BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: " +
                "Error: invalid bidder code ${bidderName} was set by the adapter ${AMX} for the account ${bidRequest.accountId}"

        and: "PBS should emit logs"
        def logs = pbsServiceWithAmxBidder.getLogsByValue(bidRequest.accountId)
        assert logs.contains("invalid bidder code ${bidderName} was set by the adapter ${AMX} for the account ${bidRequest.accountId}")

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should emit metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert metrics["adapter.${AMX}.response.validation.seat"]

        where:
        bidderName << [BOGUS, UNKNOWN, WILDCARD]
    }

    def "PBS should discard bid amx alias requested when imp[].bidder isn't same as in bid.ext.bidderCode"() {
        given: "Default bid request with amx bidder"
        def bidRequest = bidRequestWithAmxBidder().tap {
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
        assert seatNonBid.seat == ALIAS.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_GENERAL

        and: "Response should contain error"
        def error = response.ext?.errors[ErrorType.ALIAS][0]
        assert error.code == 5
        assert error.message == "BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: " +
                "Error: invalid bidder code ${bidderName} was set by the adapter ${ALIAS} for the account ${bidRequest.accountId}"

        and: "PBS should emit logs"
        def logs = pbsServiceWithAmxBidder.getLogsByValue(bidRequest.accountId)
        assert logs.contains("invalid bidder code ${bidderName} was set by the adapter ${ALIAS} for the account ${bidRequest.accountId}")

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert metrics["adapter.${ALIAS}.response.validation.seat"]

        where:
        bidderName << [BOGUS, UNKNOWN, WILDCARD]
    }

    //todo: need confirm
    @IgnoreRest
    def "PBS shouldn't discard bid amx alias requested when imp[].bidder is same as in bid.ext.bidderCode and alternate bidder code allow"() {
        given: "Default bid request with amx bidder"
        def bidRequest = bidRequestWithAmxBidderAndAlternateBidderCode().tap {
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.amx = null
            ext.prebid.aliases = [(ALIAS.value): AMX]
            ext.prebid.alternateBidderCodes = requestAlternateBidderCode
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = accountWithAlternateBidderCode(bidRequest).tap {
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
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [ALIAS]

        and: "Response should contain bidder targeting"
        def targeting = response.seatbid[0].bid[0].ext.prebid.targeting
        assert targeting["hb_pb_${GENERIC}"]
        assert targeting["hb_size_${GENERIC}"]
        assert targeting["hb_bidder"] == GENERIC.value
        assert targeting["hb_bidder_${GENERIC}"] == GENERIC.value

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBS shouldn't emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics["adapter.${ALIAS}.response.validation.seat"]

        where:
        requestAlternateBidderCode                                                                                                  | accountAlternateBidderCodes
        new AlternateBidderCodes(enabled: true, bidders: [(ALIAS): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]) | null
        null                                                                                                                        | new AlternateBidderCodes(enabled: true, bidders: [(ALIAS): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])])
    }

    def "PBS shouldn't discard bid amx alias requested when imp[].bidder is same as in bid.ext.bidderCode"() {
        given: "Default bid request with amx bidder"
        def bidRequest = bidRequestWithAmxBidder().tap {
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

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBS shouldn't emit validation metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics["adapter.${ALIAS}.response.validation.seat"]

        where:
        bidderCode << [ALIAS, ALIAS_CAMEL_CASE]
    }

    def "PBS shouldn't discard the bid or emit a response warning when alternate bidder codes not fully configured"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = bidRequestWithAmxBidderAndAlternateBidderCode().tap {
            ext.prebid.alternateBidderCodes = requestedAlternateBidderCodes
            setAccountId(PBSUtils.randomString)
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = accountWithAlternateBidderCode(bidRequest).tap {
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

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        then: "Response shouldn't contain warnings,errors and seatnonbid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "Metric shouldn't be updated"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics["adapter.${AMX}.response.validation.seat"]

        where:
        requestedAlternateBidderCodes                                                                                               | accountAlternateBidderCodes
        null                                                                                                                        | null
        new AlternateBidderCodes()                                                                                                  | null
        null                                                                                                                        | new AlternateBidderCodes()
        new AlternateBidderCodes(enabled: true)                                                                                     | null
        new AlternateBidderCodes(enabled: false)                                                                                    | null
        null                                                                                                                        | new AlternateBidderCodes(enabled: true)
        null                                                                                                                        | new AlternateBidderCodes(enabled: false)
        new AlternateBidderCodes(bidders: [(AMX): new BidderConfig()])                                                              | null
        new AlternateBidderCodes(bidders: [(UNKNOWN): new BidderConfig()])                                                          | null
        new AlternateBidderCodes(enabled: false, bidders: [(AMX): new BidderConfig()])                                              | null
        new AlternateBidderCodes(enabled: false, bidders: [(UNKNOWN): new BidderConfig()])                                          | null
        new AlternateBidderCodes(enabled: true, bidders: [(UNKNOWN): new BidderConfig()])                                           | null
        new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig()])                                               | null
        null                                                                                                                        | new AlternateBidderCodes(bidders: [(AMX): new BidderConfig()])
        null                                                                                                                        | new AlternateBidderCodes(bidders: [(UNKNOWN): new BidderConfig()])
        null                                                                                                                        | new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig()])
        null                                                                                                                        | new AlternateBidderCodes(enabled: true, bidders: [(UNKNOWN): new BidderConfig()])
        null                                                                                                                        | new AlternateBidderCodes(enabled: false, bidders: [(UNKNOWN): new BidderConfig()])
        null                                                                                                                        | new AlternateBidderCodes(enabled: false, bidders: [(AMX): new BidderConfig()])
        new AlternateBidderCodes(bidders: [(AMX): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])])                 | null
        new AlternateBidderCodes(bidders: [(UNKNOWN): new BidderConfig(enabled: false, allowedBidderCodes: [AMX])])                 | null
        new AlternateBidderCodes(enabled: false, bidders: [(AMX): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])]) | null
        new AlternateBidderCodes(enabled: false, bidders: [(UNKNOWN): new BidderConfig(enabled: false, allowedBidderCodes: [AMX])]) | null
        new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])])  | null
        new AlternateBidderCodes(enabled: true, bidders: [(UNKNOWN): new BidderConfig(enabled: false, allowedBidderCodes: [AMX])])  | null
        null                                                                                                                        | new AlternateBidderCodes(bidders: [(AMX): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])])
        null                                                                                                                        | new AlternateBidderCodes(bidders: [(UNKNOWN): new BidderConfig(enabled: false, allowedBidderCodes: [AMX])])
        null                                                                                                                        | new AlternateBidderCodes(enabled: false, bidders: [(AMX): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])])
        null                                                                                                                        | new AlternateBidderCodes(enabled: false, bidders: [(UNKNOWN): new BidderConfig(enabled: false, allowedBidderCodes: [AMX])])
        null                                                                                                                        | new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])])
        null                                                                                                                        | new AlternateBidderCodes(enabled: true, bidders: [(UNKNOWN): new BidderConfig(enabled: false, allowedBidderCodes: [AMX])])
    }

    def "PBS shouldn't discard bid when alternate bidder code allows bidder codes fully configured and bidder requested in uppercase"() {
        given: "Default bid request with AMX bidder"
        def bidRequest = bidRequestWithAmxBidderAndAlternateBidderCode().tap {
            imp[0].ext.prebid.bidder.amx = null
            imp[0].ext.prebid.bidder.tap {
                amxUpperCase = new Amx()
                amx = null
            }
            setAccountId(PBSUtils.randomString)
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

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        then: "Response shouldn't contain warnings,errors and seatnonbid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "Metric shouldn't be updated"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics["adapter.${AMX}.response.validation.seat"]

        where:
        accountAlternateBidderCodes << [
                new AccountConfig(alternateBidderCodesSnakeCase: new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig(enabled: true, allowedBidderCodesKebabCase: [GENERIC])])),
                new AccountConfig(alternateBidderCodes: new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig(enabled: true, allowedBidderCodesKebabCase: [GENERIC])])),
                new AccountConfig(alternateBidderCodesSnakeCase: new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]))]
    }

    def "PBS shouldn't discard bid when alternate bidder code allows bidder codes fully configured with different case"() {
        given: "Default bid request with amx bidder"
        def bidRequest = bidRequestWithAmxBidder().tap {
            setAccountId(PBSUtils.randomString)
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = accountWithAlternateBidderCode(bidRequest).tap {
            config = accountAlternateBidderCodes
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
        assert targeting["hb_pb_${AMX}"]
        assert targeting["hb_size_${AMX}"]
        assert targeting["hb_bidder"] == AMX.value
        assert targeting["hb_bidder_${AMX}"] == AMX.value

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        then: "Response shouldn't contain warnings,errors and seatnonbid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "Metric shouldn't be updated"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics["adapter.${AMX}.response.validation.seat"]

        where:
        accountAlternateBidderCodes << [
                new AccountConfig(alternateBidderCodesSnakeCase: new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig(enabled: true, allowedBidderCodesKebabCase: [GENERIC])])),
                new AccountConfig(alternateBidderCodes: new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig(enabled: true, allowedBidderCodesKebabCase: [GENERIC])])),
                new AccountConfig(alternateBidderCodesSnakeCase: new AlternateBidderCodes(enabled: true, bidders: [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]))]
    }

    def "PBS should take precede of request and discard the bid and emit a response error when alternate bidder codes enabled and bidder came with different bidder code"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = bidRequestWithAmxBidderAndAlternateBidderCode().tap {
            setAccountId(PBSUtils.randomString)
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = accountWithAlternateBidderCode(bidRequest).tap {
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
        assert seatNonBid.seat == AMX.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_GENERAL

        and: "Response should contain error"
        def error = response.ext?.errors[ErrorType.AMX][0]
        assert error.code == 5
        assert error.message == "BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: " +
                "Error: invalid bidder code ${UNKNOWN} was set by the adapter ${AMX} for the account ${bidRequest.accountId}"

        and: "PBS should emit logs"
        def logs = pbsServiceWithAmxBidder.getLogsByValue(bidRequest.accountId)
        assert logs.contains("invalid bidder code ${UNKNOWN} was set by the adapter ${AMX} for the account ${bidRequest.accountId}")

        and: "PBS should emit metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert metrics["adapter.${AMX}.response.validation.seat"]

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)
    }

    def "PBS should discard the bid and emit a response warning when alternate bidder codes disabled and bidder came with different bidderCode"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = bidRequestWithAmxBidderAndAlternateBidderCode().tap {
            ext.prebid.alternateBidderCodes.enabled = requestedAlternateBidderCodes
            setAccountId(PBSUtils.randomString)
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = accountWithAlternateBidderCode(bidRequest).tap {
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
        assert seatNonBid.seat == AMX.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_GENERAL

        and: "Response should contain error"
        def error = response.ext?.errors[ErrorType.AMX][0]
        assert error.code == 5
        assert error.message == "BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: " +
                "Error: invalid bidder code ${UNKNOWN} was set by the adapter ${AMX} for the account ${bidRequest.accountId}"

        and: "PBS should emit logs"
        def logs = pbsServiceWithAmxBidder.getLogsByValue(bidRequest.accountId)
        assert logs.contains("invalid bidder code ${UNKNOWN} was set by the adapter ${AMX} for the account ${bidRequest.accountId}")

        and: "PBS should emit metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert metrics["adapter.${AMX}.response.validation.seat"]

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
        def bidRequest = bidRequestWithAmxBidder().tap {
            setAccountId(PBSUtils.randomString)
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = accountWithAlternateBidderCode(bidRequest).tap {
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

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and errors and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBs metric shouldn't be updated"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics["adapter.${AMX}.response.validation.seat"]

        where:
        accountAllowedBidderCodes << [[WILDCARD], [WILDCARD, EMPTY], [EMPTY, WILDCARD], null]
    }

    def "PBS shouldn't discard the bid or emit a response warning when request alternate bidder codes are enabled and allowed bidder codes are either a wildcard or empty"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = bidRequestWithAmxBidderAndAlternateBidderCode().tap {
            ext.prebid.alternateBidderCodes.bidders[AMX].allowedBidderCodes = requestedAllowedBidderCodes
            setAccountId(PBSUtils.randomString)
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

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and errors and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBs metric shouldn't be updated"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics["adapter.${AMX}.response.validation.seat"]

        where:
        requestedAllowedBidderCodes << [[WILDCARD], [WILDCARD, EMPTY], [EMPTY, WILDCARD], null]
    }

    def "PBS shouldn't discard the bid or emit a response warning when request alternate bidder codes are enabled and the allowed bidder codes is same as bidder's request"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = bidRequestWithAmxBidderAndAlternateBidderCode().tap {
            ext.prebid.alternateBidderCodes.bidders = requestAlternateBidders
            setAccountId(PBSUtils.randomString)
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

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and errors and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBs metric shouldn't be updated"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics["adapter.${AMX}.response.validation.seat"]

        where:
        requestAlternateBidders << [[(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])],
                                    [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC_CAMEL_CASE])],
                                    [(AMX_CAMEL_CASE): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC_CAMEL_CASE])],
                                    [(AMX_CAMEL_CASE): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]]
    }

    def "PBS shouldn't discard the bid or emit a response warning when account alternate bidder codes are enabled and the allowed bidder codes is same as bidder's request"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = bidRequestWithAmxBidder().tap {
            setAccountId(PBSUtils.randomString)
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = accountWithAlternateBidderCode(bidRequest).tap {
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

        and: "Response shouldn't contain warnings and errors and seatNonBid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "PBs metric shouldn't be updated"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert !metrics["adapter.${AMX}.response.validation.seat"]

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
        def bidRequest = bidRequestWithAmxBidderAndAlternateBidderCode().tap {
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

        and: "Response should contain seatbid.seat"
        assert response.seatbid.seat.flatten() == [AMX]

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings and errors and seatnonbid"
        assert !response.ext?.warnings
        assert !response.ext?.errors
        assert !response.ext?.seatnonbid

        and: "Alert.general metric shouldn't be updated"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert !metrics["adapter.${AMX}.response.validation.seat"]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(config)
    }

    def "PBS should discard the bid and emit a response warning when request alternate bidder codes are enabled and the allowed bidder codes doesn't match the bidder's request"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = bidRequestWithAmxBidderAndAlternateBidderCode().tap {
            setAccountId(PBSUtils.randomString)
        }

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
        assert seatNonBid.seat == AMX.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_GENERAL

        and: "Response should contain error"
        def error = response.ext?.errors[ErrorType.AMX][0]
        assert error.code == 5
        assert error.message == "BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: " +
                "Error: invalid bidder code ${requestedAllowedBidderCode} was set by the adapter ${AMX} for the account ${bidRequest.accountId}"

        and: "PBS should emit logs"
        def logs = pbsServiceWithAmxBidder.getLogsByValue(bidRequest.accountId)
        assert logs.contains("invalid bidder code ${requestedAllowedBidderCode} was set by the adapter ${AMX} for the account ${bidRequest.accountId}")

        and: "PBS should emit metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert metrics["adapter.${AMX}.response.validation.seat"]

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        where:
        requestedAllowedBidderCode << [UNKNOWN, BOGUS]
    }

    def "PBS should discard the bid and emit a response warning when account alternate bidder codes are enabled and the allowed bidder codes doesn't match the bidder's request"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = bidRequestWithAmxBidder().tap {
            setAccountId(PBSUtils.randomString)
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = accountWithAlternateBidderCode(bidRequest)
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
        assert seatNonBid.seat == AMX.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_GENERAL

        and: "Response should contain error"
        def error = response.ext?.errors[ErrorType.AMX][0]
        assert error.code == 5
        assert error.message == "BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: " +
                "Error: invalid bidder code ${requestedAllowedBidderCode} was set by the adapter ${AMX} for the account ${bidRequest.accountId}"

        and: "PBS should emit logs"
        def logs = pbsServiceWithAmxBidder.getLogsByValue(bidRequest.accountId)
        assert logs.contains("invalid bidder code ${requestedAllowedBidderCode} was set by the adapter ${AMX} for the account ${bidRequest.accountId}")

        and: "PBS should emit metrics"
        def metrics = pbsServiceWithAmxBidder.sendCollectedMetricsRequest()
        assert metrics["adapter.${AMX}.response.validation.seat"]

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
        def pbsService = pbsServiceFactory.getService(AMX_CONFIG +
                ["settings.default-account-config": encode(defaultAccountConfig)])

        and: "Default bid request"
        def bidRequest = bidRequestWithAmxBidder()

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
        assert seatNonBid.seat == AMX.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_GENERAL

        and: "Response should contain error"
        def error = response.ext?.errors[ErrorType.AMX][0]
        assert error.code == 5
        assert error.message == "BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: " +
                "Error: invalid bidder code ${allowedBidderCodes} was set by the adapter ${AMX} for the account ${bidRequest.accountId}"

        and: "PBS should emit logs"
        def logs = pbsService.getLogsByValue(bidRequest.accountId)
        assert logs.contains("invalid bidder code ${allowedBidderCodes} was set by the adapter ${AMX} for the account ${bidRequest.accountId}")

        and: "PBS should emit metrics"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics["adapter.${AMX}.response.validation.seat"]

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        where:
        allowedBidderCodes << [BOGUS, UNKNOWN]
    }

    private static Account accountWithAlternateBidderCode(BidRequest bidRequest) {
        new Account().tap {
            it.uuid = bidRequest.accountId
            it.config = new AccountConfig(status: ACTIVE)
            it.config = new AccountConfig(alternateBidderCodes: new AlternateBidderCodes().tap {
                it.enabled = true
                it.bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [AMX])]
            })
        }
    }

    private static BidRequest bidRequestWithAmxBidderAndAlternateBidderCode() {
        bidRequestWithAmxBidder().tap {
            it.ext.prebid.alternateBidderCodes = new AlternateBidderCodes().tap {
                enabled = true
                bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [AMX])]
            }
        }
    }

    private static BidRequest bidRequestWithAmxBidder() {
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
