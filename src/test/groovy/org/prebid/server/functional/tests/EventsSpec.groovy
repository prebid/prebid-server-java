package org.prebid.server.functional.tests

import org.prebid.server.functional.model.ChannelType
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.AccountAnalyticsConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Events
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.util.PBSUtils

import java.time.Instant

import static org.prebid.server.functional.model.ChannelType.AMP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.DOOH
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE

class EventsSpec extends BaseSpec {

    def "PBS should generate event tracker URLs when events are enabled for account"() {
        given: "BidRequest with enabled events"
        def accountId = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultBidRequest(distributionChannel).tap {
            setAccountId(accountId as String)
            enableEvents()
        }

        and: "Save account in DB"
        def account = new Account(uuid: accountId, eventsEnabled: true)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain publisher id in events"
        assert bidResponse.seatbid[0].bid[0].ext.prebid.events.win
        assert bidResponse.seatbid[0].bid[0].ext.prebid.events.imp

        where:
        distributionChannel << [SITE, APP, DOOH]
    }

    def "PBS should not generate event tracker URLs when events are disabled for account"() {
        given: "BidRequest with enabled events"
        def accountId = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultBidRequest(distributionChannel).tap {
            setAccountId(accountId as String)
            enableEvents()
        }

        and: "Save account in DB"
        def account = new Account(uuid: accountId, eventsEnabled: false)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain publisher id in events"
        assert !bidResponse.seatbid[0].bid[0].ext.prebid.events?.win
        assert !bidResponse.seatbid[0].bid[0].ext.prebid.events?.imp

        where:
        distributionChannel << [SITE, APP, DOOH]
    }

    def "PBS should resolve publisher id for events when events are enabled for account"() {
        given: "BidRequest with enabled events"
        def accountId = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultBidRequest(distributionChannel).tap {
            setAccountId(accountId as String)
            enableEvents()
        }

        and: "Save account in DB"
        def account = new Account(uuid: accountId, eventsEnabled: true)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain publisher id in events"
        def bidResponseEvents = bidResponse.seatbid[0].bid[0].ext.prebid.events
        assert bidResponseEvents.win.contains("a=${accountId}")
        assert bidResponseEvents.imp.contains("a=${accountId}")

        where:
        distributionChannel << [SITE, APP, DOOH]
    }

    def "PBS should resolve publisher id from stored request for events when events enabled"() {
        given: "BidRequest with stored auction response"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest(distributionChannel).tap {
            ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
            setAccountId(null)
        }

        and: "Stored response with account id"
        def accountId = PBSUtils.randomNumber as String
        def storedRequest = BidRequest.getDefaultBidRequest(distributionChannel).tap {
            setAccountId(accountId)
            enableEvents()
        }
        storedRequestDao.save(StoredRequest.getStoredRequest(accountId, storedRequestId, storedRequest))

        and: "Save account in DB"
        def account = new Account(uuid: accountId, eventsEnabled: true)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain publisher id in events"
        def bidResponseEvents = bidResponse.seatbid[0].bid[0].ext.prebid.events
        assert bidResponseEvents.win.contains("a=${accountId}")
        assert bidResponseEvents.imp.contains("a=${accountId}")

        where:
        distributionChannel << [SITE, APP, DOOH]
    }

    def "Account-level analytics settings should apply when request events config is absent"() {
        given: "BidRequest without events config"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.getDefaultBidRequest(requestType).tap {
            setAccountId(accountId)
        }

        and: "Account with analytics events disabled for corresponding channel"
        def account = getAccountWithAuctionEvent(accountId, [(accountConfigChannelType): false])
        accountDao.save(account)

        when: "Auction request is processed"
        def bidResponse = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Events should not be present in response due to stored request disablement"
        assert !bidResponse.seatbid[0].bid[0].ext?.prebid?.events

        where:
        requestType | accountConfigChannelType
        SITE        | ChannelType.WEB
        APP         | ChannelType.APP
        DOOH        | ChannelType.DOOH
    }

    def "Request level events config should override account-level analytics settings"() {
        given: "BidRequest with events config"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.getDefaultBidRequest(requestType).tap {
            setAccountId(accountId)
            ext.prebid.events = new Events(enabled: requestEventEnablement)
        }

        and: "Account with analytics events disabled for corresponding channel"
        def account = getAccountWithAuctionEvent(accountId, [(accountConfigChannelType): false])
        accountDao.save(account)

        when: "Auction request is processed"
        def bidResponse = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Events should be present in response despite account-level disablement"
        def bidResponseEvents = bidResponse.seatbid[0].bid[0].ext.prebid.events
        assert bidResponseEvents.win.contains("a=${accountId}")
        assert bidResponseEvents.imp.contains("a=${accountId}")

        where:
        requestEventEnablement | requestType | accountConfigChannelType
        null                   | SITE        | ChannelType.WEB
        null                   | APP         | ChannelType.APP
        null                   | DOOH        | ChannelType.DOOH

        true                   | SITE        | ChannelType.WEB
        true                   | APP         | ChannelType.APP
        true                   | DOOH        | ChannelType.DOOH
    }

    def "Request-level events disabled should override account-level analytics settings"() {
        given: "BidRequest with events explicitly disabled at request level"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.getDefaultBidRequest(requestType).tap {
            setAccountId(accountId)
            ext.prebid.events = new Events(enabled: false)
        }

        and: "Account with analytics events enabled for corresponding channel"
        def account = getAccountWithAuctionEvent(accountId, [(accountConfigChannelType): true])
        accountDao.save(account)

        when: "Auction request is processed"
        def bidResponse = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Events should not be present in response due to request-level disablement"
        assert !bidResponse.seatbid[0].bid[0].ext?.prebid?.events

        where:
        requestType | accountConfigChannelType
        SITE        | ChannelType.WEB
        APP         | ChannelType.APP
        DOOH        | ChannelType.DOOH
    }

    def "Stored request events config should override account-level analytics settings when request config is absent"() {
        given: "BidRequest referencing stored request without events config"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest(distributionChannel).tap {
            ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
            setAccountId(null)
        }

        and: "Stored request with account id and events disabled"
        def accountId = PBSUtils.randomNumber as String
        def storedRequest = BidRequest.getDefaultBidRequest(distributionChannel).tap {
            setAccountId(accountId)
            ext.prebid.events = new Events(enabled: false)
        }
        storedRequestDao.save(StoredRequest.getStoredRequest(accountId, storedRequestId, storedRequest))

        and: "Account with analytics events enabled for corresponding channel"
        def account = getAccountWithAuctionEvent(accountId, [(accountConfigChannelType): true])
        accountDao.save(account)

        when: "Auction request is processed"
        def bidResponse = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Events should not be present in response due to stored request disablement"
        assert !bidResponse.seatbid[0].bid[0].ext?.prebid?.events

        where:
        distributionChannel | accountConfigChannelType
        SITE                | ChannelType.WEB
        APP                 | ChannelType.APP
        DOOH                | ChannelType.DOOH
    }

    def "AMP request-level events config should override account analytics settings"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Stored AMP request with events enabled at request level"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            setAccountId(accountId)
            ext.prebid.events = requestEventToggle
        }

        and: "AMP request referencing stored request"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = ampStoredRequest.accountId
        }

        and: "Stored request saved in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Account with analytics events disabled for AMP channel"
        def account = getAccountWithAuctionEvent(accountId, [(AMP): false])
        accountDao.save(account)

        and: "Trace logging enabled for account"
        defaultPbsService.sendAccountTracelogConfig(accountId, BidderName.GENERIC)

        when: "AMP request is processed"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Events should be present in trace logs bid response"
        def logs = defaultPbsService.getLogsByTime(startTime)
        def loggedBidResponse = getTraceLoggedResponse(logs, ampStoredRequest.id)
        verifyAll(loggedBidResponse?.seatbid[0]?.bid[0]?.ext?.prebid?.events) {
            it.win?.contains("a=${accountId}")
            it.imp?.contains("a=${accountId}")
        }

        where:
        requestEventToggle << [new Events(), new Events(enabled: true)]
    }

    def "AMP request-level events disabled should override account analytics settings"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Stored AMP request with events explicitly disabled"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            setAccountId(accountId)
            ext.prebid.events = new Events(enabled: false)
        }

        and: "AMP request referencing stored request"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Stored request saved in DB"
        storedRequestDao.save(StoredRequest.getStoredRequest(ampRequest, ampStoredRequest))

        and: "Account with analytics events enabled for AMP channel"
        def account = getAccountWithAuctionEvent(accountId, [(AMP): true])
        accountDao.save(account)

        and: "Trace logging enabled for account"
        defaultPbsService.sendAccountTracelogConfig(accountId, BidderName.GENERIC)

        when: "AMP request is processed"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Events should not be present in trace logs due to request-level disablement"
        def logs = defaultPbsService.getLogsByTime(startTime)
        def loggedBidResponse = getTraceLoggedResponse(logs, ampStoredRequest.id)
        assert !loggedBidResponse?.seatbid[0]?.bid[0]?.ext?.prebid?.events
    }

    def "AMP request without events config should fallback to account analytics settings"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Stored AMP request with null events config"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            setAccountId(accountId)
            ext.prebid.events = null
        }

        and: "AMP request referencing stored request"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = ampStoredRequest.accountId
        }

        and: "Stored request saved in DB"
        storedRequestDao.save(StoredRequest.getStoredRequest(ampRequest, ampStoredRequest))

        and: "Account with analytics events disabled for AMP channel"
        def account = getAccountWithAuctionEvent(accountId, [(AMP): false])
        accountDao.save(account)

        and: "Trace logging enabled for account"
        defaultPbsService.sendAccountTracelogConfig(accountId, BidderName.GENERIC)

        when: "AMP request is processed"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Events should not be present in trace logs due to fallback to account-level disablement"
        def logs = defaultPbsService.getLogsByTime(startTime)
        def loggedBidResponse = getTraceLoggedResponse(logs, ampStoredRequest.id)
        assert !loggedBidResponse?.seatbid[0]?.bid[0]?.ext?.prebid?.events
    }

    private static Account getAccountWithAuctionEvent(String accountId, Map<ChannelType, Boolean> auctionEvents) {
        def analyticsConfig = new AccountAnalyticsConfig(auctionEvents: auctionEvents)
        new Account(uuid: accountId, eventsEnabled: true, config: new AccountConfig(analytics: analyticsConfig))
    }
}
