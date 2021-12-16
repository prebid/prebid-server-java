package org.prebid.server.functional.tests

import org.apache.commons.lang3.StringUtils
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature
import spock.lang.Unroll

@PBSTest
class DebugSpec extends BaseSpec {

    private static final String overrideToken = PBSUtils.randomString

    def "PBS should return debug information when debug flag is #debug and test flag is #test"() {
        given: "Default BidRequest with test flag"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = debug
        bidRequest.test = test

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain ext.debug"
        assert response.ext?.debug

        where:
        debug | test
        1     | null
        1     | 0
        null  | 1
    }

    def "PBS shouldn't return debug information when debug flag is #debug and test flag is #test"() {
        given: "Default BidRequest with test flag"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = test
        bidRequest.test = test

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain ext.debug"
        assert !response.ext?.debug

        where:
        debug | test
        0     | null
        null  | 0
    }

    def "PBS should not return debug information when bidder-level setting debug.allowed = false"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.debug.allow": "false"])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain ext.debug"
        assert !response.ext?.debug?.httpcalls

        and: "Response should contain specific code and text in ext.warnings.general"
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999] // [10003]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["Debug turned off for bidder: $BidderName.GENERIC.value" as String]
    }

    def "PBS should return debug information when bidder-level setting debug.allowed = true"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.debug.allow": "true"])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain ext.debug"
        assert response.ext?.debug?.httpcalls[BidderName.GENERIC.value]

        and: "Response should not contain ext.warnings"
        assert !response.ext?.warnings
    }

    def "PBS should not return debug information when bidder-level setting debug.allowed = false is overridden by account-level setting debug-allowed = false"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.debug.allow": "false"])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1

        and: "Account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(debugAllow: false))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain ext.debug"
        assert !response.ext?.debug

        and: "Response should contain specific code and text in ext.warnings.general"
        //TODO change to 10002 after updating debug warnings
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        //TODO possibly change message after clarifications
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["Debug turned off for account"]
    }

    def "PBS should not return debug information when bidder-level setting debug.allowed = false is overridden by account-level setting debug-allowed = true"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.debug.allow": "false"])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1

        and: "Account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(debugAllow: true))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain ext.debug"
        assert !response.ext?.debug?.httpcalls

        and: "Response should contain specific code and text in ext.warnings.general"
        //TODO change to 10003 after updating debug warnings
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["Debug turned off for bidder: $BidderName.GENERIC.value" as String]
    }

    def "PBS should not return debug information when bidder-level setting debug.allowed = true is overridden by account-level setting debug-allowed = false"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.debug.allow": "true"])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1

        and: "Account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(debugAllow: false))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain ext.debug"
        assert !response.ext?.debug

        and: "Response should contain specific code and text in ext.warnings.general"
        //TODO change to 10002 after updating debug warnings
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } == ["Debug turned off for account"]
    }

    def "PBS should use default values = true for bidder-level setting debug.allow and account-level setting debug-allowed when they are not specified"() {
        given: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain ext.debug"
        assert response.ext?.debug?.httpcalls[BidderName.GENERIC.value]

        and: "Response should not contain ext.warnings"
        assert !response.ext?.warnings
    }

    def "PBS should return debug information when bidder-level setting debug.allowed = #debugAllowedConfig and account-level setting debug-allowed = #debugAllowedAccount is overridden by x-pbs-debug-override header"() {
        given: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1

        and: "Account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(debugAllow: debugAllowedAccount))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbdService.sendAuctionRequest(bidRequest, ["x-pbs-debug-override": overrideToken])

        then: "Response should contain ext.debug"
        assert response.ext?.debug?.httpcalls[BidderName.GENERIC.value]

        and: "Response should not contain ext.warnings"
        assert !response.ext?.warnings

        where:
        debugAllowedConfig | debugAllowedAccount | pbdService
        false              | true                | pbsServiceFactory.getService(["debug.override-token"        : overrideToken,
                                                                                 "adapters.generic.debug.allow": "false"])
        true               | false               | pbsServiceFactory.getService(["debug.override-token"        : overrideToken,
                                                                                 "adapters.generic.debug.allow": "true"])
        false              | false               | pbsServiceFactory.getService(["debug.override-token"        : overrideToken,
                                                                                 "adapters.generic.debug.allow": "false"])
    }

    def "PBS should not return debug information when x-pbs-debug-override header is incorrect"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["debug.override-token": overrideToken])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1

        and: "Account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(debugAllow: false))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest, ["x-pbs-debug-override": headerValue])

        then: "Response should not contain ext.debug"
        assert !response.ext?.debug

        and: "Response should contain specific code and text in ext.warnings.general"
        //TODO change to 10002 after updating debug warnings
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } == ["Debug turned off for account"]

        where:
        headerValue << [StringUtils.swapCase(overrideToken), PBSUtils.randomString]
    }

    @PendingFeature
    def "PBS AMP should return debug information when request flag is #requestDebug and store request flag is #storedRequestDebug"() {
        given: "Default AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            debug = requestDebug
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            ext.prebid.debug = storedRequestDebug
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain debug information"
        assert response.ext?.debug

        where:
        requestDebug || storedRequestDebug
        1            || 0
        1            || 1
        1            || null
        null         || 1
    }

    def "PBS AMP shouldn't return debug information when request flag is #requestDebug and stored request flag is #storedRequestDebug"() {
        given: "Default AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            debug = requestDebug
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            ext.prebid.debug = storedRequestDebug
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response shouldn't contain debug information"
        assert !response.ext?.debug

        where:
        requestDebug || storedRequestDebug
        0            || 1
        0            || 0
        0            || null
        null         || 0
        null         || null
    }
}
