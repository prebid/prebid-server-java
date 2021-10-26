package org.prebid.server.functional

import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountBidValidationConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Format
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Unroll

import static org.prebid.server.functional.model.config.BidValidationEnforcement.ENFORCE
import static org.prebid.server.functional.model.config.BidValidationEnforcement.SKIP
import static org.prebid.server.functional.model.config.BidValidationEnforcement.WARN

class BannerFormatValidationSpec extends BaseSpec {

    @Unroll
    def "Auction request should pass format validation when the correct values are passed"() {
        given: "Default BidRequest with format"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.format = [new Format(w: firstWidth, h: firstHeight), new Format(w: secondWidth, h: secondHeight)]
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain seatbid[0].bid"
        assert response.seatbid[0].bid

        where:
        firstWidth            | firstHeight           | secondWidth           | secondHeight
        PBSUtils.randomNumber | PBSUtils.randomNumber | 1                     | 1
        1                     | 1                     | PBSUtils.randomNumber | PBSUtils.randomNumber
    }

    @Unroll
    def "PBS should reject request when imp[].banner.format contains incorrect values"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.format = [new Format(w: width, h: height)]
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody.contains("Invalid request format: Request imp[0].banner.format[0]")

        where:
        width                         | height
        0                             | PBSUtils.randomNumber
        PBSUtils.randomNumber         | 0
        0                             | 0
        0                             | PBSUtils.randomNegativeNumber
        PBSUtils.randomNegativeNumber | 0
        PBSUtils.randomNumber         | PBSUtils.randomNegativeNumber
        PBSUtils.randomNegativeNumber | PBSUtils.randomNumber
    }

    @Unroll
    def "PBS should reject request when imp[].banner contains incorrect values"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = [w: width, h: height]
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody.contains("Invalid request format: request.imp[0].banner ")

        where:
        width                         | height
        0                             | PBSUtils.randomNumber
        PBSUtils.randomNumber         | 0
        0                             | 0
        0                             | PBSUtils.randomNegativeNumber
        PBSUtils.randomNegativeNumber | 0
        PBSUtils.randomNumber         | PBSUtils.randomNegativeNumber
        PBSUtils.randomNegativeNumber | PBSUtils.randomNumber
    }

    @Unroll
    def "PBS should emit warning when banner-creative-max-size = warn in account config"() {
        given: "Default basic BidRequest with w,h"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.format[0].w = requestWidth
            imp[0].banner.format[0].h = requestHeight
        }

        and: "Save account config into DB"
        def bidValidations = new AccountBidValidationConfig(bannerMaxSizeEnforcement: WARN)
        def auction = new AccountAuctionConfig(bidValidations: bidValidations)
        def account = new Account(uuid: bidRequest.site.publisher.id, config: new AccountConfig(auction: auction))
        accountDao.save(account)

        and: "Default basic bid with w,h"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].w = bidderWidth
            seatbid[0].bid[0].h = bidderHeight
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain warning"
        assert response.ext?.errors[ErrorType.GENERIC]*.message ==
                ["BidResponse validation `warn`: bidder `generic` response triggers creative size validation " +
                         "for bid ${bidResponse.seatbid[0].bid[0].id}, account=$bidRequest.site.publisher.id, " +
                         "referrer=$bidRequest.site.page, max imp size='${bidRequest.imp[0].banner.format[0].w}x" +
                         "${bidRequest.imp[0].banner.format[0].h}', bid response size=" +
                         "'${bidResponse.seatbid[0].bid[0].w}x${bidResponse.seatbid[0].bid[0].h}'"]

        and: "Response should contain seatbid"
        assert response.seatbid

        and: "Response bid should contain sizes from bidder response"
        assert response.seatbid[0].bid[0]?.w == bidResponse.seatbid[0].bid[0].w
        assert response.seatbid[0].bid[0]?.h == bidResponse.seatbid[0].bid[0].h

        where:
        requestWidth                       | requestHeight                      | bidderWidth      | bidderHeight
        PBSUtils.getRandomNumber(200, 300) | PBSUtils.getRandomNumber(200, 300) | requestWidth + 1 | requestHeight
        PBSUtils.getRandomNumber(200, 300) | PBSUtils.getRandomNumber(200, 300) | requestWidth     | requestHeight + 1
    }

    @Unroll
    def "PBS should emit error when banner-creative-max-size = enforce in account config"() {
        given: "Default basic BidRequest with w,h"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.format[0].w = requestWidth
            imp[0].banner.format[0].h = requestHeight
        }

        and: "Save account config into DB"
        def bidValidations = new AccountBidValidationConfig(bannerMaxSizeEnforcement: ENFORCE)
        def auction = new AccountAuctionConfig(bidValidations: bidValidations)
        def account = new Account(uuid: bidRequest.site.publisher.id, config: new AccountConfig(auction: auction))
        accountDao.save(account)

        and: "Default basic bid with w,h"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].w = bidderWidth
            seatbid[0].bid[0].h = bidderHeight
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain empty seatbid"
        assert response.seatbid.isEmpty()

        and: "Response should contain warning"
        assert response.ext?.errors[ErrorType.GENERIC]*.message ==
                ["BidResponse validation `enforce`: bidder `generic` response triggers creative size validation " +
                         "for bid ${bidResponse.seatbid[0].bid[0].id}, account=$bidRequest.site.publisher.id, " +
                         "referrer=$bidRequest.site.page, max imp size='${bidRequest.imp[0].banner.format[0].w}x" +
                         "${bidRequest.imp[0].banner.format[0].h}', bid response size=" +
                         "'${bidResponse.seatbid[0].bid[0].w}x${bidResponse.seatbid[0].bid[0].h}'"]


        where:
        requestWidth                       | requestHeight                      | bidderWidth      | bidderHeight
        PBSUtils.getRandomNumber(200, 300) | PBSUtils.getRandomNumber(200, 300) | requestWidth + 1 | requestHeight
        PBSUtils.getRandomNumber(200, 300) | PBSUtils.getRandomNumber(200, 300) | requestWidth     | requestHeight + 1
    }

    @Unroll
    def "PBS should not emit error for valid max size when auction.validations.banner-creative-max-size = #bidValidation"() {
        given: "Default basic BidRequest with w,h"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Save account config into DB"
        def bidValidations = new AccountBidValidationConfig(bannerMaxSizeEnforcement: bidValidation)
        def auction = new AccountAuctionConfig(bidValidations: bidValidations)
        def account = new Account(uuid: bidRequest.site.publisher.id, config: new AccountConfig(auction: auction))
        accountDao.save(account)

        and: "Default basic bid with w,h"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].w = bidRequest.imp[0].banner.format[0].w
            seatbid[0].bid[0].h = bidRequest.imp[0].banner.format[0].h
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain seatbid"
        assert response.seatbid

        and: "Response should contain warning"
        assert !response.ext?.errors

        and: "Response bid should contain sizes from bidder response"
        assert response.seatbid[0].bid[0]?.w == bidResponse.seatbid[0].bid[0].w
        assert response.seatbid[0].bid[0]?.h == bidResponse.seatbid[0].bid[0].h

        where:
        bidValidation << [ENFORCE, SKIP, WARN]
    }
}
