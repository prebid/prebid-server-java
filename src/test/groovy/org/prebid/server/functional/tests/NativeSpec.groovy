package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.auction.Asset
import org.prebid.server.functional.model.request.auction.AssetImage
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Native
import org.prebid.server.functional.model.request.auction.NativeRequest
import org.prebid.server.functional.model.request.auction.StoredAuctionResponse
import org.prebid.server.functional.model.response.auction.Adm
import org.prebid.server.functional.model.response.auction.BidExt
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.model.response.auction.Prebid
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.response.auction.MediaType.NATIVE

class NativeSpec extends BaseSpec {

    def "PBS should emit error when stored response asset doesn't contain id"() {
        given: "BidRequest with generic bidder, native"
        def asset = new Asset(required: 1, img: AssetImage.defaultAssetImage)
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            imp[0].banner = null
            imp[0].nativeObj = new Native(request: new NativeRequest(assets: [asset]))
            imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedResponseId)
        }

        and: "Stored auction response in DB"
        def storedAuctionResponse = SeatBid.getStoredResponse(bidRequest).tap {
            bid[0].ext = new BidExt(prebid: new Prebid(type: NATIVE))
            bid[0].adm = new Adm(assets: [asset])
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId,
                storedAuctionResponse: storedAuctionResponse)
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
            imp[0].nativeObj = new Native(request: new NativeRequest(assets: [asset]))
            imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedResponseId)
        }

        and: "Stored auction response in DB"
        def storedAuctionResponse = SeatBid.getStoredResponse(bidRequest).tap {
            bid[0].ext = new BidExt(prebid: new Prebid(type: NATIVE))
            bid[0].adm = new Adm(assets: [asset])
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId,
                storedAuctionResponse: storedAuctionResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain error"
        assert !response.ext?.errors
    }

    def "PBS should pass layout and adUnit to bidder request when field is populated"() {
        given: "Bid request with native"
        def layoutRandomNumber = PBSUtils.randomNumber
        def adUnitRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            imp[0].nativeObj = new Native(request: new NativeRequest(
                    assets: [new Asset(required: 1, id: 1, img: AssetImage.defaultAssetImage)],
                    layout: layoutRandomNumber,
                    adUnit: adUnitRandomNumber))
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain layout and adUnit field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        def nativeRequest = decode(bidderRequest.imp[0].nativeObj.request, NativeRequest)
        assert nativeRequest.layout == layoutRandomNumber
        assert nativeRequest.adUnit == adUnitRandomNumber
    }
}
