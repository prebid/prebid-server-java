package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.AccountStatus.INACTIVE
import static org.prebid.server.functional.util.SystemProperties.PBS_VERSION

class AuctionSpec extends BaseSpec {

    def "PBS should return version in response header for auction request for #description"() {
        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequestRaw(bidRequest)

        then: "Response header should contain PBS version"
        assert response.headers["x-prebid"] == "pbs-java/$PBS_VERSION"

        where:
        bidRequest                   || description
        BidRequest.defaultBidRequest || "valid bid request"
        new BidRequest()             || "invalid bid request"
    }

    def "PBS should update account.<account-id>.requests.rejected.invalid-account metric when account is inactive"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountId = bidRequest.site.publisher.id
        def account = new Account(uuid: accountId, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 401
        assert exception.responseBody == "Account ${accountId} is inactive"

        and: "account.<account-id>.requests.rejected.invalid-account metric should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics["account.${accountId}.requests.rejected.invalid-account" as String] == 1
    }

    def "PBS should update account.<account-id>.requests.rejected.#metricName metric when stored request is invalid"() {
        given: "Bid request with no stored request id"
        def noIdStoredRequest = new PrebidStoredRequest(id: null)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            updateBidRequestClosure(it, noIdStoredRequest)
        }

        and: "Initial metric count is taken"
        def accountId = bidRequest.site.publisher.id
        def fullMetricName = "account.${accountId}.requests.rejected.$metricName" as String
        def initialMetricCount = getCurrentMetricValue(fullMetricName)

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request fails with an stored request id is not found error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody ==
                "Invalid request format: Stored request processing failed: Id is not found in storedRequest"

        and: "Metric count is updated"
        assert getCurrentMetricValue(fullMetricName) == initialMetricCount + 1

        where:
        metricName               | updateBidRequestClosure
        "invalid-stored-request" | { bidReq, storedReq -> bidReq.ext.prebid.storedRequest = storedReq }
        "invalid-stored-impr"    | { bidReq, storedReq -> bidReq.imp[0].ext.prebid.storedRequest = storedReq }
    }

    def "PBS should copy imp level passThrough to bidresponse.seatbid[].bid[].ext.prebid.passThrough when the passThrough is present"() {
        given: "Default bid request with passThrough"
        def randomString = PBSUtils.randomString
        def passThrough = [(randomString): randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.passThrough = passThrough
        }

        when: "Requesting PBS auction"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same passThrough as on request"
        assert response.seatbid.first().bid.first().ext.prebid.passThrough == passThrough
    }

    def "PBS should copy global level passThrough object to bidresponse.ext.prebid.passThrough when passThrough is present"() {
        given: "Default bid request with passThrough"
        def randomString = PBSUtils.randomString
        def passThrough = [(randomString): randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.passThrough = passThrough
        }

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same passThrough as on request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.passThrough == passThrough
    }
}
