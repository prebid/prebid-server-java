package org.prebid.server.functional.tests

import org.prebid.server.functional.model.AccountStatus
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.StoredImp
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils

import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP

class AccountResolutionSpec extends BaseSpec {

    def "PBS should resolve account from AMP request account when present account at AMP request"() {
        given: "Default AMP request"
        def randomAccountId = PBSUtils.randomNumber
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            account = randomAccountId
        }

        and: "Amp stored request"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = PBSUtils.randomString
        }

        and: "Save stored request into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain publisher id from AMP tag id"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.site.publisher.id == randomAccountId as String
    }

    def "PBS should suppress top level stored request when present publisher id"() {
        given: "Default bid with top level stored request"
        def bidRequest = clouseRequest.tap {
            ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomNumber)
        }

        and: "Stored request"
        def storedBidRequest = BidRequest.defaultBidRequest

        and: "Save stored request into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(bidRequest, storedBidRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain publisher id from request publisher id"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert clouserBidderRequest(bidderRequest) == expectedRequest

        where:
        clouseRequest                        | clouserBidderRequest           | expectedRequest
        BidRequest.defaultBidRequest         | { it -> it.site.publisher.id } | clouseRequest.site.publisher.id
        BidRequest.getDefaultBidRequest(APP) | { it -> it.app.publisher.id }  | clouseRequest.app.publisher.id
    }

    def "PBS should throw error when not found account id in publisher and account is #account"() {
        given: "Pbs config with enforce-valid-account"
        def pbsService = pbsServiceFactory.getService(
                ["settings.enforce-valid-account" : "true",
                 "settings.default-account-config": encode(accountConfig)]
        )

        and: "BidRequest with stored request and without publisher id"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
            site.publisher.id = null
        }

        and: "Save stored request into DB"
        def storedRequest = StoredImp.getDbStoredImp(bidRequest, BidRequest.getDefaultBidRequest().imp[0])
        storedImpDao.save(storedRequest)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should reject the entire auction"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == UNAUTHORIZED.code()
        assert exception.responseBody == "Unauthorized account id: "

        where:
        account        | accountConfig
        "not existing" | null
        "inactive"     | new AccountConfig(status: AccountStatus.INACTIVE)
    }

    def "PBS should resolve accountId from stored request imp id when imp stored request is present"() {
        given: "BidRequest with imp stored request id and without publisher id and imp[0].id"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
            imp[0].ext.prebid.bidder.generic = null
            imp[0].id = null
            site.publisher.id = null
        }

        and: "Stored imp with stored request id"
        def storedImp = BidRequest.getDefaultBidRequest().imp[0].tap {
            id = storedRequestId
        }

        and: "Save stored imp in DB"
        def storedRequest = StoredImp.getDbStoredImp(bidRequest, storedImp)
        storedImpDao.save(storedRequest)

        when: "PBS processes auction request"
        def bidResponse = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain imp id from imp stored request"
        def bidResponseDebug = bidResponse?.ext?.debug
        assert bidResponseDebug?.httpcalls[GENERIC.value]
        assert bidResponseDebug?.resolvedRequest?.imp?.first()?.id == storedRequestId
    }
}
