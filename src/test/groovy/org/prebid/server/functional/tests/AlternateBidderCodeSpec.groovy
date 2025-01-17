package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AlternateBidderCodes
import org.prebid.server.functional.model.config.BidderConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.UNKNOWN
import static org.prebid.server.functional.model.privacy.Metric.ALERT_GENERAL
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class AlternateBidderCodeSpec extends BaseSpec {

    def "PBS shouldn't throw out bid and emit response warning when alternate bidder codes disabled"() {
        given: "Pbs config with alternate bidder codes"
        def pbsService = pbsServiceFactory.getService(
                ["settings.default-account-config": encode(AccountConfig.defaultAccountConfig.tap {
                    alternateBidderCodes = configuredBidderCode(defaultAccountAlternateBidderCodes)
                })])

        and: "Default bid request with alternate bidder codes"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.alternateBidderCodes.enabled = configuredBidderCode(requestedAlternateBidderCodes)
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = new Account().tap {
            uuid = bidRequest.accountId
            alternateBidderCodes = configuredBidderCode(accountAlternateBidderCodes)
        }
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(pbsService)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [GENERIC]

        and: "Response should contain seatbid.seat"
        assert response.seatbid[0].seat = GENERIC

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings"
        assert !response.ext?.warnings

        and: "Alert.general metric shouldn't be updated"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]

        where:
        requestedAlternateBidderCodes | defaultAccountAlternateBidderCodes | accountAlternateBidderCodes
        false                         | false                              | false
        null                          | false                              | false
        false                         | null                               | false
        false                         | false                              | null
        false                         | true                               | false
        true                          | true                               | false
        true                          | null                               | false
    }

    def "PBS shouldn't throw out bid and emit response warning when alternate bidder codes not fully configured"() {
        given: "Pbs config with alternate bidder codes"
        def pbsService = pbsServiceFactory.getService(
                ["settings.default-account-config": encode(AccountConfig.defaultAccountConfig.tap {
                    alternateBidderCodes = configuredBidderCode(defaultAccountAlternateBidderCodes)
                })])

        and: "Default bid request with alternate bidder codes"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.alternateBidderCodes.enabled = configuredBidderCode(requestedAlternateBidderCodes)
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = new Account().tap {
            uuid = bidRequest.accountId
            alternateBidderCodes = configuredBidderCode(accountAlternateBidderCodes)
        }
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(pbsService)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [GENERIC]

        and: "Response should contain seatbid.seat"
        assert response.seatbid[0].seat = GENERIC
        
        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        then: "Response shouldn't contain warnings"
        assert !response.ext?.warnings

        and: "Alert.general metric shouldn't be updated"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]

        where:
        requestedAlternateBidderCodes                                                                                   | defaultAccountAlternateBidderCodes                                                                              | accountAlternateBidderCodes
        new AlternateBidderCodes()                                                                                      | null                                                                                                            | null
        null                                                                                                            | new AlternateBidderCodes()                                                                                      | null
        null                                                                                                            | null                                                                                                            | new AlternateBidderCodes()
        new AlternateBidderCodes(enabled: true)                                                                         | null                                                                                                            | null
        null                                                                                                            | new AlternateBidderCodes(enabled: true)                                                                         | null
        null                                                                                                            | null                                                                                                            | new AlternateBidderCodes(enabled: true)
        null                                                                                                            | null                                                                                                            | null
        new AlternateBidderCodes(bidders: [(GENERIC): new BidderConfig()])                                              | null                                                                                                            | null
        null                                                                                                            | new AlternateBidderCodes(bidders: [(GENERIC): new BidderConfig()])                                              | null
        null                                                                                                            | null                                                                                                            | new AlternateBidderCodes(bidders: [(GENERIC): new BidderConfig()])
        new AlternateBidderCodes(bidders: [(GENERIC): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])]) | null                                                                                                            | null
        null                                                                                                            | new AlternateBidderCodes(bidders: [(GENERIC): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])]) | null
        null                                                                                                            | null                                                                                                            | new AlternateBidderCodes(bidders: [(GENERIC): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])])
    }

    def "PBS shouldn't throw out bid and emit response warning when alternate bidder codes enabled and bidder disabled "() {
        given: "Pbs config with alternate bidder codes"
        def pbsService = pbsServiceFactory.getService(
                ["settings.default-account-config": encode(AccountConfig.defaultAccountConfig.tap {
                    alternateBidderCodes = defaultAccountAlternateBidderCodes
                })])

        and: "Default bid request with alternate bidder codes"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.alternateBidderCodes = requestedAlternateBidderCodes
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = new Account().tap {
            uuid = bidRequest.accountId
            alternateBidderCodes = accountAlternateBidderCodes
        }
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(pbsService)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [GENERIC]

        and: "Response should contain seatbid.seat"
        assert response.seatbid[0].seat = GENERIC

        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Response shouldn't contain warnings"
        assert !response.ext?.warnings

        and: "Alert.general metric shouldn't be updated"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]

        where:
        requestedAlternateBidderCodes               | defaultAccountAlternateBidderCodes          | accountAlternateBidderCodes
        disabledGenericBidderConfiguredBidderCode() | disabledGenericBidderConfiguredBidderCode() | disabledGenericBidderConfiguredBidderCode()
        null                                        | disabledGenericBidderConfiguredBidderCode() | disabledGenericBidderConfiguredBidderCode()
        disabledGenericBidderConfiguredBidderCode() | null                                        | disabledGenericBidderConfiguredBidderCode()
        disabledGenericBidderConfiguredBidderCode() | disabledGenericBidderConfiguredBidderCode() | null
    }

    def "PBS shouldn't throw out bid and emit response warning when configured alternate bidder codes with requested mismatch bidder"() {
        given: "Pbs config with alternate bidder codes and openx"
        def pbsService = pbsServiceFactory.getService(
                ["adapters.openx.enabled"         : "true",
                 "adapters.openx.endpoint"        : "$networkServiceContainer.rootUri/auction".toString(),
                 "settings.default-account-config": encode(AccountConfig.defaultAccountConfig.tap {
                     alternateBidderCodes = defaultAccountAlternateBidderCodes
                 })])

        and: "Default bid request with openx bidder and alternate bidder codes"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            ext.prebid.alternateBidderCodes = requestedAlternateBidderCodes
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = new Account().tap {
            uuid = bidRequest.accountId
            alternateBidderCodes = accountAlternateBidderCodes
        }
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(pbsService)
        
        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [OPENX]

        and: "Response should contain seatbid.seat"
        assert response.seatbid[0].seat = OPENX

        and: "Response shouldn't contain warnings"
        assert !response.ext?.warnings
        
        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Alert.general metric shouldn't be updated"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]

        where:
        requestedAlternateBidderCodes | defaultAccountAlternateBidderCodes | accountAlternateBidderCodes
        enabledConfiguredBidderCode() | enabledConfiguredBidderCode()      | enabledConfiguredBidderCode()
        null                          | enabledConfiguredBidderCode()      | enabledConfiguredBidderCode()
        enabledConfiguredBidderCode() | null                               | enabledConfiguredBidderCode()
        enabledConfiguredBidderCode() | enabledConfiguredBidderCode()      | null
    }

    def "PBS shouldn't throw out bid and emit response warning when alternate bidder codes enabled and bidder disabled"() {
        given: "Pbs config with alternate bidder codes"
        def pbsService = pbsServiceFactory.getService(
                ["settings.default-account-config": encode(AccountConfig.defaultAccountConfig.tap {
                    alternateBidderCodes = defaultAccountAlternateBidderCodes
                })])

        and: "Default bid request with alternate bidder codes"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.alternateBidderCodes = requestedAlternateBidderCodes
        }

        and: "Save account config into DB with alternate bidder codes"
        def account = new Account().tap {
            uuid = bidRequest.accountId
            alternateBidderCodes = accountAlternateBidderCodes
        }
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(pbsService)
        
        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [GENERIC]

        and: "Response should contain seatbid.seat"
        assert response.seatbid[0].seat = GENERIC

        and: "Response shouldn't contain warnings"
        assert !response.ext?.warnings
        
        and: "Bidder request should be valid"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Alert.general metric shouldn't be updated"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert !metrics[ALERT_GENERAL]

        where:
        requestedAlternateBidderCodes               | defaultAccountAlternateBidderCodes          | accountAlternateBidderCodes
        disabledGenericBidderConfiguredBidderCode() | disabledGenericBidderConfiguredBidderCode() | disabledGenericBidderConfiguredBidderCode()
        null                                        | disabledGenericBidderConfiguredBidderCode() | disabledGenericBidderConfiguredBidderCode()
        disabledGenericBidderConfiguredBidderCode() | null                                        | disabledGenericBidderConfiguredBidderCode()
        disabledGenericBidderConfiguredBidderCode() | disabledGenericBidderConfiguredBidderCode() | null
    }

    private static AlternateBidderCodes enabledConfiguredBidderCode(Boolean alternateBidderCodesEnabled = true) {
        new AlternateBidderCodes().tap {
            it.enabled = alternateBidderCodesEnabled
            it.bidders = [(GENERIC): new BidderConfig(enabled: true, allowedBidderCodes: [UNKNOWN])]
        }
    }

    private static AlternateBidderCodes disabledGenericBidderConfiguredBidderCode() {
        new AlternateBidderCodes().tap {
            it.enabled = true
            it.bidders = [(GENERIC): new BidderConfig(enabled: false, allowedBidderCodes: [UNKNOWN])]
        }
    }
}
