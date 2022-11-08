package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.model.db.StoredRequest

import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
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
        distributionChannel << [SITE, APP]
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
        distributionChannel << [SITE, APP]
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
        distributionChannel << [SITE, APP]
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
        distributionChannel << [SITE, APP]
    }
}
