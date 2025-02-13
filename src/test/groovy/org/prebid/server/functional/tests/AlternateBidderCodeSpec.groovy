package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AlternateBidderCodes
import org.prebid.server.functional.model.config.BidderConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.AccountStatus.ACTIVE
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.BOGUS
import static org.prebid.server.functional.model.bidder.BidderName.EMPTY
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC_CAMEL_CASE
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.UNKNOWN
import static org.prebid.server.functional.model.bidder.BidderName.WILDCARD
import static org.prebid.server.functional.model.privacy.Metric.ALERT_GENERAL
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID

class AlternateBidderCodeSpec extends BaseSpec {

    private static final WARNING_MESSAGE = "HERE SHOULD BE WARNING"

    def "PBS shouldn't throw out bid and emit response warning when alternate bidder codes not fully configured"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.alternateBidderCodes = requestedAlternateBidderCodes
            setAccountId(PBSUtils.randomString)
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = accountWithAlternateBidderCode(bidRequest).tap {
            config.alternateBidderCodes = accountAlternateBidderCodes
        }
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [GENERIC]

        and: "Response should contain seatbid.seat"
        assert response.seatbid[0].seat == GENERIC

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        then: "Response shouldn't contain warnings"
        assert !response.ext?.warnings

        and: "Alert.general metric shouldn't be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]

        where:
        requestedAlternateBidderCodes                                                                                                          | accountAlternateBidderCodes
        null                                                                                                                                   | null
        new AlternateBidderCodes()                                                                                                      | null
        null                                                                                                                            | new AlternateBidderCodes()
        new AlternateBidderCodes(enabled: true)                                                                                         | null
        new AlternateBidderCodes(enabled: false)                                                                                        | null
        null                                                                                                                            | new AlternateBidderCodes(enabled: true)
        null                                                                                                                            | new AlternateBidderCodes(enabled: false)
        new AlternateBidderCodes(bidders: [(GENERIC): new BidderConfig()])                                                              | null
        new AlternateBidderCodes(bidders: [(UNKNOWN): new BidderConfig()])                                                              | null
        new AlternateBidderCodes(enabled: false, bidders: [(GENERIC): new BidderConfig()])                                              | null
        new AlternateBidderCodes(enabled: false, bidders: [(UNKNOWN): new BidderConfig()])                                              | null
        new AlternateBidderCodes(enabled: true, bidders: [(UNKNOWN): new BidderConfig()])                                               | null
        new AlternateBidderCodes(enabled: true, bidders: [(GENERIC): new BidderConfig()])                                               | null
        null                                                                                                                            | new AlternateBidderCodes(bidders: [(GENERIC): new BidderConfig()])
        null                                                                                                                            | new AlternateBidderCodes(bidders: [(UNKNOWN): new BidderConfig()])
        null                                                                                                                            | new AlternateBidderCodes(enabled: true, bidders: [(GENERIC): new BidderConfig()])
        null                                                                                                                            | new AlternateBidderCodes(enabled: true, bidders: [(UNKNOWN): new BidderConfig()])
        null                                                                                                                            | new AlternateBidderCodes(enabled: false, bidders: [(UNKNOWN): new BidderConfig()])
        null                                                                                                                            | new AlternateBidderCodes(enabled: false, bidders: [(GENERIC): new BidderConfig()])
        new AlternateBidderCodes(bidders: [(GENERIC): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])])                 | null
        new AlternateBidderCodes(bidders: [(UNKNOWN): new BidderConfig(enabled: false, allowedBidderCodes: [GENERIC])])                 | null
        new AlternateBidderCodes(enabled: false, bidders: [(GENERIC): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])]) | null
        new AlternateBidderCodes(enabled: false, bidders: [(UNKNOWN): new BidderConfig(enabled: false, allowedBidderCodes: [GENERIC])]) | null
        new AlternateBidderCodes(enabled: true, bidders: [(GENERIC): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])])  | null
        new AlternateBidderCodes(enabled: true, bidders: [(UNKNOWN): new BidderConfig(enabled: false, allowedBidderCodes: [GENERIC])])  | null
        null                                                                                                                            | new AlternateBidderCodes(bidders: [(GENERIC): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])])
        null                                                                                                                            | new AlternateBidderCodes(bidders: [(UNKNOWN): new BidderConfig(enabled: false, allowedBidderCodes: [GENERIC])])
        null                                                                                                                            | new AlternateBidderCodes(enabled: false, bidders: [(GENERIC): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])])
        null                                                                                                                            | new AlternateBidderCodes(enabled: false, bidders: [(UNKNOWN): new BidderConfig(enabled: false, allowedBidderCodes: [GENERIC])])
        null                                                                                                                            | new AlternateBidderCodes(enabled: true, bidders: [(GENERIC): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])])
        null                                                                                                                            | new AlternateBidderCodes(enabled: true, bidders: [(UNKNOWN): new BidderConfig(enabled: false, allowedBidderCodes: [GENERIC])])
    }

    def "PBS shouldn't throw out bid and emit response warning when alternate bidder codes disabled"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = bidRequestWithAlternateBidderCode().tap {
            ext.prebid.alternateBidderCodes.enabled = requestedAlternateBidderCodes
            setAccountId(PBSUtils.randomString)
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = accountWithAlternateBidderCode(bidRequest).tap {
            config.alternateBidderCodes.enabled = accountAlternateBidderCodes
        }
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [GENERIC]

        and: "Response should contain seatBid.seat"
        assert response.seatbid.seat.flatten() == [GENERIC]

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings"
        assert !response.ext?.warnings

        and: "Alert.general metric shouldn't be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]

        where:
        requestedAlternateBidderCodes | accountAlternateBidderCodes
        false                         | false
        false                         | null
        null                          | false
        false                         | true
    }

    def "PBS shouldn't discard the bid or emit a response warning warning when alternate bidder codes disabled for bidder"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = bidRequestWithAlternateBidderCode().tap {
            ext.prebid.alternateBidderCodes.bidders[GENERIC].enabled = requestedAlternateBidderCodes
            setAccountId(PBSUtils.randomString)
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = accountWithAlternateBidderCode(bidRequest).tap {
            config.alternateBidderCodes.bidders[GENERIC].enabled = accountAlternateBidderCodes
        }
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [GENERIC]

        and: "Response should contain seatBid.seat"
        assert response.seatbid.seat.flatten() == [GENERIC]

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings"
        assert !response.ext?.warnings

        and: "Alert.general metric shouldn't be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]

        where:
        requestedAlternateBidderCodes | accountAlternateBidderCodes
        false                         | false
        false                         | null
        null                          | false
        false                         | true
    }

    def "PBS shouldn't discard the bid or emit a response warning when configured alternate bidder codes with the requested mismatched bidder"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = bidRequestWithAlternateBidderCode().tap {
            ext.prebid.alternateBidderCodes.bidders = requestedAlternateBidderCodes
            setAccountId(PBSUtils.randomString)
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = accountWithAlternateBidderCode(bidRequest).tap {
            config.alternateBidderCodes.bidders = accountAlternateBidderCodes
        }
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [GENERIC]

        and: "Response should contain seatbid.seat"
        assert response.seatbid.seat.flatten() == [GENERIC]

        and: "Response shouldn't contain warnings"
        assert !response.ext?.warnings

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Alert.general metric shouldn't be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]

        where:
        requestedAlternateBidderCodes                                             | accountAlternateBidderCodes
        [(OPENX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])] | null
        null                                                                      | [(OPENX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]
        [(OPENX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])] | [(GENERIC): new BidderConfig(enabled: true, allowedBidderCodes: [UNKNOWN])]
    }

    def "PBS shouldn't discard the bid or emit a response warning when alternate bidder codes are enabled and allowed bidder codes are either a wildcard or empty"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = bidRequestWithAlternateBidderCode().tap {
            ext.prebid.alternateBidderCodes.bidders[GENERIC].allowedBidderCodes = requestedAllowedBidderCodes
            setAccountId(PBSUtils.randomString)
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = accountWithAlternateBidderCode(bidRequest).tap {
            config.alternateBidderCodes.bidders[GENERIC].allowedBidderCodes = accountAllowedBidderCodes
        }
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [GENERIC]

        and: "Response should contain seatbid.seat"
        assert response.seatbid.seat.flatten() == [GENERIC]

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings"
        assert !response.ext?.warnings

        and: "Alert.general metric shouldn't be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]

        where:
        requestedAllowedBidderCodes | accountAllowedBidderCodes
        []                          | null
        null                        | []
        [WILDCARD]                  | null
        [WILDCARD, EMPTY]           | null
        [WILDCARD, EMPTY]           | [WILDCARD, EMPTY]
        null                        | [WILDCARD]
        null                        | [WILDCARD, EMPTY]
        null                        | null
        [WILDCARD]                  | [WILDCARD]
        []                          | []
        [EMPTY]                     | [EMPTY]
        [EMPTY]                     | null
        null                        | [EMPTY]
    }

    def "PBS shouldn't discard the bid or emit a response warning when alternate bidder codes are enabled and the allowed bidder codes is same as bidder's request"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = bidRequestWithAlternateBidderCode().tap {
            ext.prebid.alternateBidderCodes.bidders = requestAlternateBidders
            setAccountId(PBSUtils.randomString)
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = new Account().tap {
            it.uuid = bidRequest.accountId
            it.config = new AccountConfig(status: ACTIVE)
            it.alternateBidderCodes = new AlternateBidderCodes(enabled: true, bidders: accountAlternateBidders)
        }
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [GENERIC]

        and: "Response should contain seatbid.seat"
        assert response.seatbid.seat.flatten() == [GENERIC]

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings"
        assert !response.ext?.warnings

        and: "Alert.general metric shouldn't be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]

        where:
        accountAlternateBidders                                                                           | requestAlternateBidders
        [(GENERIC): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC_CAMEL_CASE])]            | null
        [(GENERIC_CAMEL_CASE): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]            | null
        [(GENERIC_CAMEL_CASE): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC_CAMEL_CASE])] | null
        null                                                                                              | [(GENERIC): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC_CAMEL_CASE])]
        null                                                                                              | [(GENERIC_CAMEL_CASE): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]
        null                                                                                              | [(GENERIC_CAMEL_CASE): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC_CAMEL_CASE])]
    }

    def "PBS shouldn't discard the bid and emit a response warning when default account alternate bidder codes are enabled and the allowed bidder codes does match the bidder's request"() {
        given: "Pbs config with default-account-config"
        def defaultAccountConfig = AccountConfig.defaultAccountConfig.tap {
            alternateBidderCodes = new AlternateBidderCodes().tap {
                it.enabled = true
                it.bidders = [(GENERIC): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]
            }
        }
        def pbsService = pbsServiceFactory.getService(
                ["settings.default-account-config": encode(defaultAccountConfig)])

        and: "Default bid request"
        def bidRequest = BidRequest.getDefaultBidRequest()

        and: "Flash metrics"
        flushMetrics(pbsService)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [GENERIC]

        and: "Response should contain seatbid.seat"
        assert response.seatbid.seat.flatten() == [GENERIC]

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings"
        assert !response.ext?.warnings[PREBID]

        and: "Alert.general metric shouldn't be updated"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]
    }

    def "PBS shouldn't discard bid and not emit a response warning when requested alternate bidder codes are enabled and the allowed bidder code doesn't match with alias of specified bidders"() {
        given: "Default bid request with alias and alternate bidder code"
        def bidRequest = bidRequestWithAlternateBidderCode().tap {
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.generic = null
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Resolved request should contain aliases as in request"
        assert response.ext.debug.resolvedRequest.ext.prebid.aliases == bidRequest.ext.prebid.aliases

        and: "Bidder request should contain request per-alies"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [ALIAS]

        and: "Response should contain seatbid.seat"
        assert response.seatbid.seat.flatten() == [ALIAS]

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings"
        assert !response.ext?.warnings[PREBID]

        and: "Alert.general metric shouldn't be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]
    }

    def "PBS should discard the bid and emit a response warning when account alternate bidder codes are enabled and the allowed bidder codes doesn't match the bidder's request"() {
        given: "Default bid request with alternate bidder codes"
        def bidRequest = bidRequestWithAlternateBidderCode().tap {
            ext.prebid.alternateBidderCodes.bidders[GENERIC].allowedBidderCodes = requestedAllowedBidderCode
            setAccountId(PBSUtils.randomString)
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = accountWithAlternateBidderCode(bidRequest).tap {
            config.alternateBidderCodes.bidders[GENERIC].allowedBidderCodes = accountAllowedBidderCode
        }
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain seat bid"
        assert !response.seatbid

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response should contain warnings"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message == [WARNING_MESSAGE]

        and: "Alert.general metric should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL]

        where:
        requestedAllowedBidderCode | accountAllowedBidderCode
        [UNKNOWN]                  | null
        [BOGUS]                    | null
        null                       | [BOGUS]
        null                       | [UNKNOWN]
        null                       | [UNKNOWN, BOGUS]
        [UNKNOWN, BOGUS]           | [GENERIC]
        [UNKNOWN]                  | [GENERIC]
    }

    def "PBS should discard the bid and emit a response warning when default account alternate bidder codes are enabled and the allowed bidder codes doesn't match the bidder's request"() {
        given: "Pbs config with default-account-config"
        def defaultAccountConfig = AccountConfig.defaultAccountConfig.tap {
            alternateBidderCodes = new AlternateBidderCodes().tap {
                it.enabled = true
                it.bidders = [(GENERIC): new BidderConfig(enabled: true, allowedBidderCodes: allowedBidderCodes)]
            }
        }
        def pbsService = pbsServiceFactory.getService(
                ["settings.default-account-config": encode(defaultAccountConfig)])

        and: "Default bid request"
        def bidRequest = BidRequest.getDefaultBidRequest()

        and: "Flash metrics"
        flushMetrics(pbsService)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain seat bid"
        assert !response.seatbid

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response should contain warnings"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message == [WARNING_MESSAGE]

        and: "Alert.general metric should be updated"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL]

        where:
        allowedBidderCodes << [[BOGUS], [BOGUS, UNKNOWN]]
    }

    def "PBS should discard bid and not emit a response warning when requested alternate bidder codes are enabled and the allowed bidder code does match with alias of specified bidders"() {
        given: "Default bid request with alias and alternate bidder code"
        def bidRequest = bidRequestWithAlternateBidderCode().tap {
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            ext.prebid.alternateBidderCodes.bidders = [(ALIAS): new BidderConfig(enabled: true, allowedBidderCodes: [UNKNOWN])]
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.generic = null
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Resolved request should contain aliases as in request"
        assert response.ext.debug.resolvedRequest.ext.prebid.aliases == bidRequest.ext.prebid.aliases

        and: "Response shouldn't contain seat bid"
        assert !response.seatbid

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response should contain warnings"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message == [WARNING_MESSAGE]

        and: "Alert.general metric should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL]
    }

    //todo: ADD test for it, if pubmatic bidder will be updated with logic for different seat name
    // check for the existence of the seat-defined-bidderCode in the bidadjustmentfactors object. If it exists, apply the adjustment and we're done.
    // otherwise, check for the existence of the adaptercode (seatbid.bid.ext.prebid.meta.adaptercode) in the bidadjustmentfactors object. If it exists, apply the adjustment

    private static Account accountWithAlternateBidderCode(BidRequest bidRequest) {
        new Account().tap {
            it.uuid = bidRequest.accountId
            it.config = new AccountConfig(status: ACTIVE)
            it.config = new AccountConfig(alternateBidderCodes: new AlternateBidderCodes().tap {
                it.enabled = true
                it.bidders = [(GENERIC): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]
            })
        }
    }

    private static BidRequest bidRequestWithAlternateBidderCode() {
        BidRequest.defaultBidRequest.tap {
            it.ext.prebid.alternateBidderCodes = new AlternateBidderCodes().tap {
                it.enabled = true
                it.bidders = [(GENERIC): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]
            }
        }
    }
}
