package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.auction.Asset
import org.prebid.server.functional.model.request.auction.AssetImage
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Native
import org.prebid.server.functional.model.request.auction.Request
import org.prebid.server.functional.model.request.auction.StoredAuctionResponse
import org.prebid.server.functional.model.response.auction.Adm
import org.prebid.server.functional.model.response.auction.BidExt
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.model.response.auction.Prebid
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.response.auction.MediaType.NATIVE

@PBSTest
class NativeSpec extends BaseSpec {

    def "PBS should emit error when stored response asset doesn't contain id"() {
        given: "BidRequest with generic bidder, native"
        def asset = new Asset(required: 1, img: AssetImage.defaultAssetImage)
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            imp[0].banner = null
            imp[0].nativeObj = new Native(request: new Request(assets: [asset]))
            imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedResponseId)
        }

        and: "Stored auction response in DB"
        def storedAuctionResponse = SeatBid.getStoredResponse(bidRequest).tap {
            bid[0].ext = new BidExt(prebid: new Prebid(type: NATIVE))
            bid[0].adm = new Adm(assets: [asset])
        }
        def storedResponse = new StoredResponse(resid: storedResponseId, storedAuctionResponse: storedAuctionResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain error"
        assert response.ext?.errors[ErrorType.GENERIC]*.code == [3]
        assert response.ext?.errors[ErrorType.GENERIC]*.message ==
                ["Response has an Image asset with ID:'' present that doesn't exist in the request" as String]
    }

    def "PBS should not emit error when stored response asset contains id"() {
        given: "BidRequest with generic bidder, native"
        def asset = new Asset(required: 1, id: 1, img: AssetImage.defaultAssetImage)
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            imp[0].banner = null
            imp[0].nativeObj = new Native(request: new Request(assets: [asset]))
            imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedResponseId)
        }

        and: "Stored auction response in DB"
        def storedAuctionResponse = SeatBid.getStoredResponse(bidRequest).tap {
            bid[0].ext = new BidExt(prebid: new Prebid(type: NATIVE))
            bid[0].adm = new Adm(assets: [asset])
        }
        def storedResponse = new StoredResponse(resid: storedResponseId, storedAuctionResponse: storedAuctionResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain error"
        assert !response.ext?.errors
    }
}
