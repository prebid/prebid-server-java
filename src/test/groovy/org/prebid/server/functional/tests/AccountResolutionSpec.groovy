package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE

class AccountResolutionSpec extends BaseSpec {

    def "PBS should prefer account from AMP request parameter during account resolution"() {
        given: "Default AMP request"
        def accountId = PBSUtils.randomNumber
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            account = accountId
            tagId = PBSUtils.randomString
        }

        and: "AMP stored request"
        def storedRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(PBSUtils.randomString)
        }

        and: "Save stored request into DB"
        storedRequestDao.save(StoredRequest.getStoredRequest(ampRequest, storedRequest))

        when: "PBS processes AMP request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain publisher id from AMP tag id"
        def bidderRequest = bidder.getBidderRequest(storedRequest.id)
        assert bidderRequest.site.publisher.id == null
    }

    def "PBS should prefer account from publisher id during auction account resolution"() {
        given: "Default Bid Request with top level stored request"
        def storedRequestId = PBSUtils.randomString
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.getDefaultBidRequest(distributionChannel).tap {
            setAccountId(accountId)
            ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
        }

        and: "Save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(accountId,
                storedRequestId,
                BidRequest.getDefaultBidRequest(distributionChannel))
        storedRequestDao.save(storedRequest)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain publisher id from request publisher id"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.accountId == accountId

        where:
        distributionChannel << [SITE, APP]
    }
}
