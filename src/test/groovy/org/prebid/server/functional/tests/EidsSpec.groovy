package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.model.request.auction.EidPermission
import org.prebid.server.functional.model.request.auction.ExtRequestPrebidData
import org.prebid.server.functional.model.request.auction.Uid
import org.prebid.server.functional.model.request.auction.UidExt
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.WILDCARD
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class EidsSpec extends BaseSpec {

    def "PBS shouldn't populate user.id from user.ext data"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(ext: new UserExt(eids: [new Eid(source: PBSUtils.randomString,
                    uids: [new Uid(id: PBSUtils.randomString, ext: new UidExt(stype: PBSUtils.randomString))])]))
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain user.id"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user.id
    }

    def "PBS should send same eids as in original request"() {
        given: "Default basic BidRequest with generic bidder"
        def eids = [new Eid(source: PBSUtils.randomString, uids: [new Uid(id: PBSUtils.randomString)])]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(eids: eids)
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain requested eids"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.eids == eids
    }

    def "PBS eids should be passed only to permitted bidders"() {
        given: "Default bid request with generic bidder and eids"
        def eids = [new Eid(source: PBSUtils.randomString, uids: [new Uid(id: PBSUtils.randomString)])]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(eids: eids)
            ext.prebid.data = new ExtRequestPrebidData(eidpermissions:
                    [new EidPermission(source: PBSUtils.randomString, bidders: [eidsBidder])])
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain requested eids"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.eids == eids

        where:
        eidsBidder << [WILDCARD, GENERIC]
    }

    def "PBS eids shouldn't be passed to restricted bidders"() {
        given: "Default bid request with generic bidder"
        def sourceId = PBSUtils.randomString
        def eids = [new Eid(source: sourceId, uids: [new Uid(id: sourceId)])]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(eids: eids)
            ext.prebid.data = new ExtRequestPrebidData(eidpermissions: [new EidPermission(source: sourceId, bidders: [OPENX])])
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain requested eids"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user.eids
    }

    def "PBs eid permissions should affect only specified on source"() {
        given: "PBs with openx bidder"
        def pbsService = pbsServiceFactory.getService(
                ["adapters.openx.enabled" : "true",
                 "adapters.openx.endpoint": "$networkServiceContainer.rootUri/auction".toString()])

        and: "Default bid request with eidpremissions and openx bidder"
        def eidSource = PBSUtils.randomString
        def openxEid = new Eid(source: eidSource, uids: [new Uid(id: PBSUtils.randomString)])
        def genericEid = new Eid(source: PBSUtils.randomString, uids: [new Uid(id: PBSUtils.randomString)])
        def eids = [openxEid, genericEid]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(eids: eids)
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            ext.prebid.data = new ExtRequestPrebidData(eidpermissions: [new EidPermission(source: eidSource, bidders: [OPENX])])
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain two bidder request"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.size() == 2

        and: "Generic bidder should contain one eid"
        assert bidderRequests.user.eids.sort().first == [genericEid]

        and: "Openx bidder should contain two eids"
        assert bidderRequests.user.eids.sort().last.sort() == eids.sort()
    }

    def "PBs eid permissions for non existing source should not stop auction"() {
        given: "PBs with openx bidder"
        def pbsService = pbsServiceFactory.getService(
                ["adapters.openx.enabled" : "true",
                 "adapters.openx.endpoint": "$networkServiceContainer.rootUri/auction".toString()])

        and: "Default bid request with eidpremissions and openx bidder"
        def firstEid = new Eid(source: PBSUtils.randomString, uids: [new Uid(id: PBSUtils.randomString)])
        def secondEid = new Eid(source: PBSUtils.randomString, uids: [new Uid(id: PBSUtils.randomString)])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(eids: [firstEid, secondEid])
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            ext.prebid.data = new ExtRequestPrebidData(
                    eidpermissions: [new EidPermission(source: PBSUtils.randomString, bidders: [OPENX])])
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain two bidder request"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.size() == 2

        and: "Openx and Generic bidder should contain two eid"
        bidderRequests.user.eids.each {
            assert it.sort() == [secondEid, firstEid].sort()
        }
    }

    def "PBs missing bidders in eid permissions should throw an error"() {
        given: "Default request with eidpremissions and openx bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(eids: [new Eid(source: PBSUtils.randomString, uids: [new Uid(id: PBSUtils.randomString)])])
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            ext.prebid.data = new ExtRequestPrebidData(
                    eidpermissions: [new EidPermission(source: PBSUtils.randomString, bidders: eidsBidder),
                                     new EidPermission(source: PBSUtils.randomString)])
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should throw error"
        def exception = thrown(PrebidServerException)
        assert exception.responseBody == "Invalid request format: request.ext.prebid.data.eidpermissions[].bidders[] " +
                "required values but was empty or null"

        where:
        eidsBidder << [[WILDCARD], [], null]
    }

    def "PBs eid permissions should honor bidder alias"() {
        given: "Default request with eidpremissions and openx bidder"
        def sourceId = PBSUtils.randomString
        def eid = new Eid(source: sourceId, uids: [new Uid(id: PBSUtils.randomString)])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(eids: [eid])
            imp[0].ext.prebid.bidder.alias = new Generic()
            ext.prebid.tap {
                data = new ExtRequestPrebidData(eidpermissions: [new EidPermission(source: sourceId, bidders: [ALIAS])])
                aliases = [(ALIAS.value): GENERIC]
            }
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain two bidder request"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        def sortedEids = bidderRequests.user.sort { it.eids }
        assert bidderRequests.size() == 2

        and: "Generic bidder shouldn't contain eids"
        assert !sortedEids[0].eids

        and: "Alias bidder should contain one eids"
        assert sortedEids[1].eids == [eid]
    }
}
