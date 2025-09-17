package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountBidValidationConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.BidValidationEnforcement
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.auction.Asset
import org.prebid.server.functional.model.request.auction.Audio
import org.prebid.server.functional.model.request.auction.Banner
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Format
import org.prebid.server.functional.model.request.auction.Native
import org.prebid.server.functional.model.request.auction.StoredBidResponse
import org.prebid.server.functional.model.request.auction.Video
import org.prebid.server.functional.model.response.auction.Adm
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature
import spock.lang.Shared

import static org.prebid.server.functional.model.AccountStatus.ACTIVE
import static org.prebid.server.functional.model.config.BidValidationEnforcement.ENFORCE
import static org.prebid.server.functional.model.config.BidValidationEnforcement.SKIP
import static org.prebid.server.functional.model.config.BidValidationEnforcement.WARN
import static org.prebid.server.functional.model.request.auction.SecurityLevel.NON_SECURE
import static org.prebid.server.functional.model.request.auction.SecurityLevel.SECURE
import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC

class BidderFormatSpec extends BaseSpec {

    @Shared
    private static final RANDOM_NUMBER = PBSUtils.randomNumber

<<<<<<< HEAD
    def "PBS should successfully pass when banner.format width and height is valid"() {
        given: "Default bid request with banner format"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.format = [new Format(width: bannerFormatWidth, height: bannerFormatHeight)]
=======
    def "PBS should successfully pass when banner.format weight and height is valid"() {
        given: "Default bid request with banner format"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.format = [new Format(weight: bannerFormatWeight, height: bannerFormatHeight)]
>>>>>>> 04d9d4a13 (Initial commit)
        }

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same banner format as on request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
<<<<<<< HEAD
        assert bidderRequest?.imp[0]?.banner?.format[0].width == bannerFormatWidth
        assert bidderRequest?.imp[0]?.banner?.format[0].height == bannerFormatHeight

        where:
        bannerFormatWidth    | bannerFormatHeight
=======
        assert bidderRequest?.imp[0]?.banner?.format[0].weight == bannerFormatWeight
        assert bidderRequest?.imp[0]?.banner?.format[0].height == bannerFormatHeight

        where:
        bannerFormatWeight    | bannerFormatHeight
>>>>>>> 04d9d4a13 (Initial commit)
        1                     | 1
        PBSUtils.randomNumber | PBSUtils.randomNumber
    }

<<<<<<< HEAD
    def "PBS should unsuccessfully pass and throw error due to validation banner.format{w.h} when banner.format width or height is invalid"() {
        given: "Default bid request with banner format"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.format = [new Format(width: bannerFormatWidth, height: bannerFormatHeight)]
=======
    def "PBS should unsuccessfully pass and throw error due to validation banner.format{w.h} when banner.format weight or height is invalid"() {
        given: "Default bid request with banner format"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.format = [new Format(weight: bannerFormatWeight, height: bannerFormatHeight)]
>>>>>>> 04d9d4a13 (Initial commit)
        }

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBs should throw error due to banner.format{w.h} validation"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == "Invalid request format: " +
                "request.imp[0].banner.format[0] must define a valid \"h\" and \"w\" properties"

        where:
<<<<<<< HEAD
        bannerFormatWidth            | bannerFormatHeight
=======
        bannerFormatWeight            | bannerFormatHeight
>>>>>>> 04d9d4a13 (Initial commit)
        0                             | PBSUtils.randomNumber
        PBSUtils.randomNumber         | 0
        null                          | PBSUtils.randomNumber
        PBSUtils.randomNumber         | null
        PBSUtils.randomNegativeNumber | PBSUtils.randomNumber
        PBSUtils.randomNumber         | PBSUtils.randomNegativeNumber
    }

<<<<<<< HEAD
    def "PBS should unsuccessfully pass and throw error due to validation banner.format{w.h} when banner.format width and height is invalid"() {
        given: "Default bid request with banner format"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.format = [new Format(width: bannerFormatWidth, height: bannerFormatHeight)]
=======
    def "PBS should unsuccessfully pass and throw error due to validation banner.format{w.h} when banner.format weight and height is invalid"() {
        given: "Default bid request with banner format"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.format = [new Format(weight: bannerFormatWeight, height: bannerFormatHeight)]
>>>>>>> 04d9d4a13 (Initial commit)
        }

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBs should throw error due to banner.format{w.h} validation"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == "Invalid request format: request.imp[0].banner.format[0] " +
                "should define *either* {w, h} (for static size requirements) " +
                "*or* {wmin, wratio, hratio} (for flexible sizes) to be non-zero positive"

        where:
<<<<<<< HEAD
        bannerFormatWidth            | bannerFormatHeight
=======
        bannerFormatWeight            | bannerFormatHeight
>>>>>>> 04d9d4a13 (Initial commit)
        0                             | 0
        0                             | null
        0                             | PBSUtils.randomNegativeNumber
        null                          | null
        null                          | PBSUtils.randomNegativeNumber
        PBSUtils.randomNegativeNumber | PBSUtils.randomNegativeNumber
    }

<<<<<<< HEAD
    def "PBS should successfully pass when banner width and height is valid"() {
        given: "Default bid request with banner format"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = new Banner(width: bannerFormatWidth, height: bannerFormatHeight)
=======
    def "PBS should successfully pass when banner weight and height is valid"() {
        given: "Default bid request with banner format"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = new Banner(weight: bannerFormatWeight, height: bannerFormatHeight)
>>>>>>> 04d9d4a13 (Initial commit)
        }

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same banner{w.h} as on request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
<<<<<<< HEAD
        assert bidderRequest?.imp[0]?.banner?.width == bannerFormatWidth
        assert bidderRequest?.imp[0]?.banner?.height == bannerFormatHeight

        where:
        bannerFormatWidth    | bannerFormatHeight
=======
        assert bidderRequest?.imp[0]?.banner?.weight == bannerFormatWeight
        assert bidderRequest?.imp[0]?.banner?.height == bannerFormatHeight

        where:
        bannerFormatWeight    | bannerFormatHeight
>>>>>>> 04d9d4a13 (Initial commit)
        1                     | 1
        PBSUtils.randomNumber | PBSUtils.randomNumber
    }

    def "PBS should unsuccessfully pass and throw error due to validation banner{w.h} when banner{w.h} is invalid"() {
        given: "Default bid request with banner{w.h}"
        def bidRequest = BidRequest.defaultBidRequest.tap {
<<<<<<< HEAD
            imp[0].banner = new Banner(width: bannerFormatWidth, height: bannerFormatHeight)
=======
            imp[0].banner = new Banner(weight: bannerFormatWeight, height: bannerFormatHeight)
>>>>>>> 04d9d4a13 (Initial commit)
        }

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBs should throw error due to banner{w.h} validation"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == "Invalid request format: " +
                "request.imp[0].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements"

        where:
<<<<<<< HEAD
        bannerFormatWidth            | bannerFormatHeight
=======
        bannerFormatWeight            | bannerFormatHeight
>>>>>>> 04d9d4a13 (Initial commit)
        0                             | 0
        0                             | PBSUtils.randomNumber
        PBSUtils.randomNumber         | 0
        null                          | null
        null                          | PBSUtils.randomNumber
        PBSUtils.randomNumber         | null
        PBSUtils.randomNegativeNumber | PBSUtils.randomNegativeNumber
        PBSUtils.randomNegativeNumber | PBSUtils.randomNumber
        PBSUtils.randomNumber         | PBSUtils.randomNegativeNumber
    }

    def "PBS should emit error and metrics when banner-creative-max-size: warn and bid response W or H is larger that request W or H"() {
        given: "PBS with banner creative max size"
        def pbsService = pbsServiceFactory.getService(["auction.validations.banner-creative-max-size": configCreativeMaxSize])

        and: "Default bid request with banner format"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
<<<<<<< HEAD
                banner = new Banner(format: [new Format(width: RANDOM_NUMBER, height: RANDOM_NUMBER)])
=======
                banner = new Banner(format: [new Format(weight: RANDOM_NUMBER, height: RANDOM_NUMBER)])
>>>>>>> 04d9d4a13 (Initial commit)
                ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: BidderName.GENERIC)]
            }
        }

        and: "Stored bid response with biggest W and H than in bidRequest in DB"
        def storedBidId = UUID.randomUUID()
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].tap {
                it.id = storedBidId
<<<<<<< HEAD
                it.width = responseWidth
=======
                it.weight = responseWeight
>>>>>>> 04d9d4a13 (Initial commit)
                it.height = responseHeight
            }
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        and: "Account in the DB with specified banner max size enforcement"
        def account = getAccountWithSpecifiedBannerMax(bidRequest.accountId, accountCretiveMaxSize)
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(pbsService)

        when: "Requesting PBS auction"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Corresponding metric should increments"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics["account.${bidRequest.accountId}.response.validation.size.warn"] == 1
        assert metrics["adapter.generic.response.validation.size.warn"] == 1

        and: "Response should contain error"
        assert bidResponse.ext?.errors[GENERIC]*.code == [5]
        assert bidResponse.ext?.errors[GENERIC]*.message[0]
                == "BidId `${storedBidId}` validation messages: " +
                "Warning: BidResponse validation `warn`: bidder `${GENERIC}` response triggers creative size " +
                "validation for bid ${storedBidId}, account=${bidRequest.accountId}, " +
                "referrer=${bidRequest.site.page}, max imp size='${RANDOM_NUMBER}x${RANDOM_NUMBER}', " +
<<<<<<< HEAD
                "bid response size='${responseWidth}x${responseHeight}'"

        and: "Bid response should contain width and height from stored response"
        def bid = bidResponse.seatbid[0].bid[0]
        assert bid.width == responseWidth
=======
                "bid response size='${responseWeight}x${responseHeight}'"

        and: "Bid response should contain weight and height from stored response"
        def bid = bidResponse.seatbid[0].bid[0]
        assert bid.weight == responseWeight
>>>>>>> 04d9d4a13 (Initial commit)
        assert bid.height == responseHeight

        and: "PBs shouldn't perform a bidder request due to stored bid response"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
<<<<<<< HEAD
        accountCretiveMaxSize | configCreativeMaxSize | responseWidth    | responseHeight
=======
        accountCretiveMaxSize | configCreativeMaxSize | responseWeight    | responseHeight
>>>>>>> 04d9d4a13 (Initial commit)
        null                  | WARN.value            | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        null                  | WARN.value            | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        null                  | WARN.value            | RANDOM_NUMBER     | RANDOM_NUMBER + 1
        WARN                  | null                  | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        WARN                  | null                  | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        WARN                  | null                  | RANDOM_NUMBER     | RANDOM_NUMBER + 1
    }

    def "PBS shouldn't emit error and metrics when banner-creative-max-size: skip and bid response W or H is larger that request W or H"() {
        given: "PBS with banner creative max size"
        def pbsService = pbsServiceFactory.getService(["auction.validations.banner-creative-max-size": configCreativeMaxSize])

        and: "Default bid request with banner format"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
<<<<<<< HEAD
                banner = new Banner(format: [new Format(width: RANDOM_NUMBER, height: RANDOM_NUMBER)])
=======
                banner = new Banner(format: [new Format(weight: RANDOM_NUMBER, height: RANDOM_NUMBER)])
>>>>>>> 04d9d4a13 (Initial commit)
                ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: BidderName.GENERIC)]
            }
        }

        and: "Stored bid response with biggest W and H than in bidRequest in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].tap {
<<<<<<< HEAD
                it.width = responseWidth
=======
                it.weight = responseWeight
>>>>>>> 04d9d4a13 (Initial commit)
                it.height = responseHeight
            }
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        and: "Account in the DB with specified banner max size enforcement"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(bidValidationsSnakeCase:
                new AccountBidValidationConfig(bannerMaxSizeEnforcementSnakeCase: accountCretiveMaxSizeSnakeCase, bannerMaxSizeEnforcement: accountCretiveMaxSize), debugAllow: true))
        def account = new Account(status: ACTIVE, uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "Requesting PBS auction"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Corresponding metric shouldn't increments"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.size.warn"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.size.err"]

        and: "Response should contain error"
        assert !bidResponse.ext?.errors

<<<<<<< HEAD
        and: "Bid response should contain width and height from stored response"
        def bid = bidResponse.seatbid[0].bid[0]
        assert bid.width == responseWidth
=======
        and: "Bid response should contain weight and height from stored response"
        def bid = bidResponse.seatbid[0].bid[0]
        assert bid.weight == responseWeight
>>>>>>> 04d9d4a13 (Initial commit)
        assert bid.height == responseHeight

        and: "PBs shouldn't perform a bidder request due to stored bid response"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
<<<<<<< HEAD
        accountCretiveMaxSizeSnakeCase | accountCretiveMaxSize | configCreativeMaxSize | responseWidth    | responseHeight
=======
        accountCretiveMaxSizeSnakeCase | accountCretiveMaxSize | configCreativeMaxSize | responseWeight    | responseHeight
>>>>>>> 04d9d4a13 (Initial commit)
        null                           | null                  | SKIP.value            | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        null                           | null                  | SKIP.value            | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        null                           | null                  | SKIP.value            | RANDOM_NUMBER     | RANDOM_NUMBER + 1
        null                           | SKIP                  | null                  | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        null                           | SKIP                  | null                  | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        null                           | SKIP                  | null                  | RANDOM_NUMBER     | RANDOM_NUMBER + 1
        null                           | null                  | SKIP.value            | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        null                           | null                  | SKIP.value            | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        null                           | null                  | SKIP.value            | RANDOM_NUMBER     | RANDOM_NUMBER + 1
        SKIP                           | null                  | null                  | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        SKIP                           | null                  | null                  | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        SKIP                           | null                  | null                  | RANDOM_NUMBER     | RANDOM_NUMBER + 1
    }

    def "PBS should emit error and metrics and remove bid response from consideration when banner-creative-max-size: enforce and bid response W or H is larger that request W or H"() {
        given: "PBS with banner creative max size"
        def pbsService = pbsServiceFactory.getService(["auction.validations.banner-creative-max-size": configCreativeMaxSize])

        and: "Default bid request with banner format"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
<<<<<<< HEAD
                banner = new Banner(format: [new Format(width: RANDOM_NUMBER, height: RANDOM_NUMBER)])
=======
                banner = new Banner(format: [new Format(weight: RANDOM_NUMBER, height: RANDOM_NUMBER)])
>>>>>>> 04d9d4a13 (Initial commit)
                ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: BidderName.GENERIC)]
            }
        }

        and: "Stored bid response with biggest W and H than in bidRequest in DB"
        def storedBidId = UUID.randomUUID()
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].tap {
                it.id = storedBidId
<<<<<<< HEAD
                it.width = responseWidth
=======
                it.weight = responseWeight
>>>>>>> 04d9d4a13 (Initial commit)
                it.height = responseHeight
            }
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        and: "Account in the DB with specified banner max size enforcement"
        def account = getAccountWithSpecifiedBannerMax(bidRequest.accountId, accountCretiveMaxSize)
        accountDao.save(account)

        and:
        flushMetrics(pbsService)

        when: "Requesting PBS auction"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Corresponding metric should increments"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics["account.${bidRequest.accountId}.response.validation.size.err"] == 1
        assert metrics["adapter.generic.response.validation.size.err"] == 1

        and: "Response should contain error"
        assert bidResponse.ext?.errors[GENERIC]*.code == [5]
        assert bidResponse.ext?.errors[GENERIC]*.message[0]
                == "BidId `${storedBidId}` validation messages: " +
                "Error: BidResponse validation `enforce`: bidder `${GENERIC.value}` response triggers creative size " +
                "validation for bid ${storedBidId}, account=${bidRequest.accountId}, " +
                "referrer=${bidRequest.site.page}, max imp size='${RANDOM_NUMBER}x${RANDOM_NUMBER}', " +
<<<<<<< HEAD
                "bid response size='${responseWidth}x${responseHeight}'"
=======
                "bid response size='${responseWeight}x${responseHeight}'"
>>>>>>> 04d9d4a13 (Initial commit)

        and: "Pbs should discard seatBid due to validation"
        assert !bidResponse.seatbid

        and: "PBs shouldn't perform a bidder request due to stored bid response"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
<<<<<<< HEAD
        accountCretiveMaxSize | configCreativeMaxSize | responseWidth    | responseHeight
=======
        accountCretiveMaxSize | configCreativeMaxSize | responseWeight    | responseHeight
>>>>>>> 04d9d4a13 (Initial commit)
        null                  | ENFORCE.value         | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        null                  | ENFORCE.value         | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        null                  | ENFORCE.value         | RANDOM_NUMBER     | RANDOM_NUMBER + 1
        ENFORCE               | null                  | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        ENFORCE               | null                  | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        ENFORCE               | null                  | RANDOM_NUMBER     | RANDOM_NUMBER + 1
    }

    def "PBS shouldn't emit error and metrics when banner-creative-max-size #configCreativeMaxSize and bid response W or H is same that request W or H"() {
        given: "PBS with banner creative max size"
        def pbsService = pbsServiceFactory.getService(["auction.validations.banner-creative-max-size": configCreativeMaxSize])

        and: "Default bid request with banner format"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
<<<<<<< HEAD
                banner = new Banner(format: [new Format(width: RANDOM_NUMBER, height: RANDOM_NUMBER)])
=======
                banner = new Banner(format: [new Format(weight: RANDOM_NUMBER, height: RANDOM_NUMBER)])
>>>>>>> 04d9d4a13 (Initial commit)
                ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: BidderName.GENERIC)]
            }
        }

        and: "Stored bid response with biggest W and H than in bidRequest in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].tap {
<<<<<<< HEAD
                width = RANDOM_NUMBER
=======
                weight = RANDOM_NUMBER
>>>>>>> 04d9d4a13 (Initial commit)
                height = RANDOM_NUMBER
            }
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        and: "Account in the DB with specified banner max size enforcement"
        def account = getAccountWithSpecifiedBannerMax(bidRequest.accountId, accountCretiveMaxSize)
        accountDao.save(account)

        when: "Requesting PBS auction"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Corresponding metric shouldn't increments"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.size.warn"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.size.err"]

        and: "Response should contain error"
        assert !bidResponse.ext?.errors

<<<<<<< HEAD
        and: "Bid response should contain width and height from stored response"
        def bid = bidResponse.seatbid[0].bid[0]
        assert bid.width == RANDOM_NUMBER
=======
        and: "Bid response should contain weight and height from stored response"
        def bid = bidResponse.seatbid[0].bid[0]
        assert bid.weight == RANDOM_NUMBER
>>>>>>> 04d9d4a13 (Initial commit)
        assert bid.height == RANDOM_NUMBER

        and: "PBs shouldn't perform a bidder request due to stored bid response"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
        accountCretiveMaxSize | configCreativeMaxSize
        null                  | SKIP.value
        SKIP                  | null
        ENFORCE               | null
        null                  | ENFORCE.value
        WARN                  | null
        null                  | WARN.value
    }

    def "PBS shouldn't emit error and metrics when media type isn't banner and banner-creative-max-size #configCreativeMaxSize and bid response W or H is larger that request W or H"() {
        given: "PBS with banner creative max size"
        def pbsService = pbsServiceFactory.getService(["auction.validations.banner-creative-max-size": configCreativeMaxSize])

        and: "Default bid request with video W and H"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultVideoRequest().tap {
            imp[0].tap {
<<<<<<< HEAD
                video = new Video(width: RANDOM_NUMBER, height: RANDOM_NUMBER, mimes: [PBSUtils.randomString])
=======
                video = new Video(weight: RANDOM_NUMBER, height: RANDOM_NUMBER, mimes: [PBSUtils.randomString])
>>>>>>> 04d9d4a13 (Initial commit)
                ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: BidderName.GENERIC)]
            }
        }

        and: "Stored bid response with biggest W and H than in bidRequest in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].tap {
<<<<<<< HEAD
                width = responseWidth
=======
                weight = responseWeight
>>>>>>> 04d9d4a13 (Initial commit)
                height = responseHeight
            }
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        and: "Account in the DB with specified banner max size enforcement"
        def account = getAccountWithSpecifiedBannerMax(bidRequest.accountId, accountCretiveMaxSize)
        accountDao.save(account)

        when: "Requesting PBS auction"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Corresponding metric should increments"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.size.err"]
        assert !metrics["adapter.generic.response.validation.size.err"]

        and: "Response shouldn't contain error"
        assert !bidResponse.ext?.errors

        and: "Pbs should contain seatBid.bid"
        assert bidResponse.seatbid.bid

        and: "PBs shouldn't perform a bidder request due to stored bid response"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
<<<<<<< HEAD
        accountCretiveMaxSize | configCreativeMaxSize | responseWidth    | responseHeight
=======
        accountCretiveMaxSize | configCreativeMaxSize | responseWeight    | responseHeight
>>>>>>> 04d9d4a13 (Initial commit)
        null                  | ENFORCE.value         | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        null                  | ENFORCE.value         | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        null                  | ENFORCE.value         | RANDOM_NUMBER     | RANDOM_NUMBER + 1
        ENFORCE               | null                  | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        ENFORCE               | null                  | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        ENFORCE               | null                  | RANDOM_NUMBER     | RANDOM_NUMBER + 1
        null                  | SKIP.value            | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        null                  | SKIP.value            | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        null                  | SKIP.value            | RANDOM_NUMBER     | RANDOM_NUMBER + 1
        SKIP                  | null                  | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        SKIP                  | null                  | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        SKIP                  | null                  | RANDOM_NUMBER     | RANDOM_NUMBER + 1
        null                  | WARN.value            | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        null                  | WARN.value            | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        null                  | WARN.value            | RANDOM_NUMBER     | RANDOM_NUMBER + 1
        WARN                  | null                  | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        WARN                  | null                  | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        WARN                  | null                  | RANDOM_NUMBER     | RANDOM_NUMBER + 1
    }

    def "PBS should emit error and metrics and remove bid response from consideration and account value should take precedence over host when banner-creative-max-size enforce and bid response W or H is larger that request W or H"() {
        given: "PBS with banner creative max size"
        def pbsService = pbsServiceFactory.getService(["auction.validations.banner-creative-max-size": configCreativeMaxSize])

        and: "Default bid request with banner format"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
<<<<<<< HEAD
                banner = new Banner(format: [new Format(width: RANDOM_NUMBER, height: RANDOM_NUMBER)])
=======
                banner = new Banner(format: [new Format(weight: RANDOM_NUMBER, height: RANDOM_NUMBER)])
>>>>>>> 04d9d4a13 (Initial commit)
                ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: BidderName.GENERIC)]
            }
        }

        and: "Stored bid response with biggest W and H than in bidRequest in DB"
        def storedBidId = UUID.randomUUID()
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].tap {
                it.id = storedBidId
<<<<<<< HEAD
                it.width = responseWidth
=======
                it.weight = responseWeight
>>>>>>> 04d9d4a13 (Initial commit)
                it.height = responseHeight
            }
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        and: "Account in the DB with specified banner max size enforcement"
        def account = getAccountWithSpecifiedBannerMax(bidRequest.accountId, accountCretiveMaxSize)
        accountDao.save(account)

        and:
        flushMetrics(pbsService)

        when: "Requesting PBS auction"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Corresponding metric should increments"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics["account.${bidRequest.accountId}.response.validation.size.err"] == 1
        assert metrics["adapter.generic.response.validation.size.err"] == 1

        and: "Bid response should contain error"
        assert bidResponse.ext?.errors[GENERIC]*.code == [5]
        assert bidResponse.ext?.errors[GENERIC]*.message[0]
                == "BidId `${storedBidId}` validation messages: " +
                "Error: BidResponse validation `enforce`: bidder `generic` response triggers creative size " +
                "validation for bid ${storedBidId}, account=${bidRequest.accountId}, " +
                "referrer=${bidRequest.site.page}, max imp size='${RANDOM_NUMBER}x${RANDOM_NUMBER}', " +
<<<<<<< HEAD
                "bid response size='${responseWidth}x${responseHeight}'"
=======
                "bid response size='${responseWeight}x${responseHeight}'"
>>>>>>> 04d9d4a13 (Initial commit)

        and: "Pbs should discard seatBid due to validation"
        assert !bidResponse.seatbid

        and: "PBs shouldn't perform a bidder request due to stored bid response"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
<<<<<<< HEAD
        accountCretiveMaxSize | configCreativeMaxSize | responseWidth    | responseHeight
=======
        accountCretiveMaxSize | configCreativeMaxSize | responseWeight    | responseHeight
>>>>>>> 04d9d4a13 (Initial commit)
        ENFORCE               | WARN.value            | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        ENFORCE               | WARN.value            | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        ENFORCE               | WARN.value            | RANDOM_NUMBER     | RANDOM_NUMBER + 1
        ENFORCE               | null                  | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        ENFORCE               | null                  | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        ENFORCE               | null                  | RANDOM_NUMBER     | RANDOM_NUMBER + 1
        ENFORCE               | SKIP.value            | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        ENFORCE               | SKIP.value            | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        ENFORCE               | SKIP.value            | RANDOM_NUMBER     | RANDOM_NUMBER + 1
    }

    @PendingFeature(reason = "Waiting for confirmation")
    def "PBS shouldn't make a validation for audio media type when secure is #secure and secure markUp is #secureMarkup"() {
        given: "PBS with secure-markUp: #secureMarkup"
        def pbsService = pbsServiceFactory.getService(["auction.validations.secure-markup": secureMarkup])

        and: "Audio bid request"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].secure = secure
            imp[0].banner = null
            imp[0].video = null
            imp[0].audio = Audio.defaultAudio
            imp[0].ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: BidderName.GENERIC)]
        }

        and: "Stored bid response in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].adm = new Adm(assets: [Asset.getImgAsset("http://secure-assets.${PBSUtils.randomString}.com")])
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "Requesting PBS auction"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Corresponding metric shouldn't be increments"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.secure.warn"]
        assert !metrics["adapter.${BidderName.GENERIC.value}.response.validation.secure.warn"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.secure.err"]
        assert !metrics["adapter.${BidderName.GENERIC.value}.response.validation.secure.err"]

        and: "Bid response should contain error"
        assert !bidResponse.ext?.errors

        and: "Pbs should contain seatBid"
        assert bidResponse.seatbid

        and: "PBs shouldn't perform a bidder request due to stored bid response"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
        secure     | secureMarkup
        SECURE     | SKIP.value
        SECURE     | ENFORCE.value
        SECURE     | WARN.value
        NON_SECURE | SKIP.value
        NON_SECURE | ENFORCE.value
        NON_SECURE | WARN.value
    }

    def "PBS should emit metrics and error when imp[0].secure = 1 and config WARN and bid response adm contain #url"() {
        given: "PBS with secure-markUp: warn"
        def pbsService = pbsServiceFactory.getService(["auction.validations.secure-markup": WARN.value])

        and: "Default bid request with secure and banner or video or nativeObj"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].secure = SECURE
            imp[0].banner = banner
            imp[0].video = video
            imp[0].nativeObj = nativeObj
            imp[0].ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: BidderName.GENERIC)]
        }

        and: "Stored bid response in DB"
        def storedBidId = UUID.randomUUID()
        def adm = new Adm(assets: [Asset.getImgAsset("${url}://secure-assets.${PBSUtils.randomString}.com")])
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].tap {
                it.id = storedBidId
                it.adm = adm
            }
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "Requesting PBS auction"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Corresponding metric should increments"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics["account.${bidRequest.accountId}.response.validation.secure.warn"] == 1
        assert metrics["adapter.${BidderName.GENERIC.value}.response.validation.secure.warn"] == 1

        and: "Bid response should contain error"
        assert bidResponse.ext?.errors[GENERIC]*.code == [5]
        assert bidResponse.ext?.errors[GENERIC]*.message[0]
                == "BidId `${storedBidId}` validation messages: " +
                "Warning: BidResponse validation `warn`: bidder `${BidderName.GENERIC.value}` response triggers secure creative " +
                "validation for bid ${storedBidId}, account=${bidRequest.accountId}, referrer=${bidRequest.site.page}," +
                " adm=${encode(adm)}"

        and: "Pbs should contain seatBid"
        assert bidResponse.seatbid

        and: "PBs shouldn't perform a bidder request due to stored bid response"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
        url       | banner               | video              | nativeObj
        "http%3A" | Banner.defaultBanner | null               | null
        "http"    | Banner.defaultBanner | null               | null
        "http"    | null                 | Video.defaultVideo | null
        "http%3A" | null                 | Video.defaultVideo | null
        "http"    | null                 | null               | Native.defaultNative
        "http%3A" | null                 | null               | Native.defaultNative
    }

    def "PBS should emit metrics and error when imp[0].secure = 1, banner and config SKIP and bid response adm contain #url"() {
        given: "PBS with secure-markUp: skip"
        def pbsService = pbsServiceFactory.getService(["auction.validations.secure-markup": SKIP.value])

        and: "Default bid request with secure and banner or video or nativeObj"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].secure = SECURE
            imp[0].banner = banner
            imp[0].video = video
            imp[0].nativeObj = nativeObj
            imp[0].ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: BidderName.GENERIC)]
        }

        and: "Stored bid response in DB with adm"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].adm = new Adm(assets: [Asset.getImgAsset("${url}://secure-assets.${PBSUtils.randomString}.com")])
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "Requesting PBS auction"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Corresponding metric should increments"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.secure.warn"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.secure.err"]
        assert !metrics["adapter.${BidderName.GENERIC.value}.response.validation.secure.warn"]
        assert !metrics["adapter.${BidderName.GENERIC.value}.response.validation.secure.err"]

        and: "Bid response shouldn't contain error"
        assert !bidResponse.ext?.errors

        and: "Pbs should contain seatBid"
        assert bidResponse.seatbid

        and: "PBs shouldn't perform a bidder request due to stored bid response"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
        url       | banner               | video              | nativeObj
        "http%3A" | Banner.defaultBanner | null               | null
        "http"    | Banner.defaultBanner | null               | null
        "http"    | null                 | Video.defaultVideo | null
        "http%3A" | null                 | Video.defaultVideo | null
        "http"    | null                 | null               | Native.defaultNative
        "http%3A" | null                 | null               | Native.defaultNative
    }

    def "PBS should emit metrics and error and remove bid response when imp[0].secure = 1, banner and config ENFORCE and bid response adm contain #url"() {
        given: "PBS with secure-markUp: enforce"
        def pbsService = pbsServiceFactory.getService(["auction.validations.secure-markup": ENFORCE.value])

        and: "Default bid request with secure and banner or video or nativeObj"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].secure = SECURE
            imp[0].banner = banner
            imp[0].video = video
            imp[0].nativeObj = nativeObj
            imp[0].ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: BidderName.GENERIC)]
        }

        and: "Stored bid response in DB"
        def storedBidId = UUID.randomUUID()
        def adm = new Adm(assets: [Asset.getImgAsset("${url}://secure-assets.${PBSUtils.randomString}.com")])
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].tap {
                it.id = storedBidId
                it.adm = adm
            }
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "Requesting PBS auction"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Corresponding metric should increments"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics["account.${bidRequest.accountId}.response.validation.secure.err"] == 1
        assert metrics["adapter.${BidderName.GENERIC.value}.response.validation.secure.err"] == 1

        and: "Bid response should contain error"
        assert bidResponse.ext?.errors[GENERIC]*.code == [5]
        assert bidResponse.ext?.errors[GENERIC]*.message[0]
                == "BidId `${storedBidId}` validation messages: " +
                "Error: BidResponse validation `enforce`: bidder `${BidderName.GENERIC.value}` response triggers secure creative " +
                "validation for bid ${storedBidId}, account=${bidRequest.accountId}, referrer=${bidRequest.site.page}," +
                " adm=${encode(adm)}"

        and: "Pbs shouldn't contain seatBid"
        assert !bidResponse.seatbid

        and: "PBs shouldn't perform a bidder request due to stored bid response"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
        url       | banner               | video              | nativeObj
        "http%3A" | Banner.defaultBanner | null               | null
        "http"    | Banner.defaultBanner | null               | null
        "http"    | null                 | Video.defaultVideo | null
        "http%3A" | null                 | Video.defaultVideo | null
        "http"    | null                 | null               | Native.defaultNative
        "http%3A" | null                 | null               | Native.defaultNative
    }

    def "PBS shouldn't emit errors and metrics when imp[0].secure = #secure and bid response adm contain #url"() {
        given: "PBS with secure-markUp"
        def pbsService = pbsServiceFactory
                .getService(["auction.validations.secure-markup": secureMarkup])

        and: "Default bid request with secure"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
                it.secure = secure
                it.ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: BidderName.GENERIC)]
            }
        }

        and: "Stored bid response in DB with adm"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].adm = new Adm(assets: [Asset.getImgAsset("${url}://secure-assets.${PBSUtils.randomString}.com")])
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "Requesting PBS auction"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Corresponding metric shouldn't increments"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.secure.warn"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.secure.err"]
        assert !metrics["adapter.${BidderName.GENERIC.value}.response.validation.secure.warn"]
        assert !metrics["adapter.${BidderName.GENERIC.value}.response.validation.secure.err"]

        and: "Bid response shouldn't contain error"
        assert !bidResponse.ext?.errors

        and: "Pbs should contain seatBid"
        assert bidResponse.seatbid

        and: "PBs shouldn't perform a bidder request due to stored bid response"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
        url       | secure     | secureMarkup
        "http%3A" | NON_SECURE | SKIP.value
        "http"    | NON_SECURE | SKIP.value
        "https"   | SECURE     | SKIP.value
        "http%3A" | NON_SECURE | WARN.value
        "http"    | NON_SECURE | WARN.value
        "https"   | SECURE     | WARN.value
        "http%3A" | NON_SECURE | ENFORCE.value
        "http"    | NON_SECURE | ENFORCE.value
        "https"   | SECURE     | ENFORCE.value
    }

    def "PBS should ignore specified secureMarkup #secureMarkup validation when secure is 0"() {
        given: "PBS with secure-markUp"
        def pbsService = pbsServiceFactory.getService(["auction.validations.secure-markup": secureMarkup])

        and: "Default bid request with stored bid response and secure"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
                secure = NON_SECURE
                ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: BidderName.GENERIC)]
            }
        }

        and: "Stored bid response in DB with adm"
        def adm = new Adm(assets: [Asset.getImgAsset("${url}://secure-assets.${PBSUtils.randomString}.com")])
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].adm = adm
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "Requesting PBS auction"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Corresponding metric shouldn't increments"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.secure.warn"]
        assert !metrics["adapter.${BidderName.GENERIC.value}.response.validation.secure.warn"]

        and: "Bid response shouldn't contain error"
        assert !bidResponse.ext?.errors

        and: "Pbs should contain seatBid"
        assert bidResponse.seatbid

        and: "PBs shouldn't perform a bidder request due to stored bid response"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
        secureMarkup  | url
        WARN.value    | "http"
        WARN.value    | "http%3A"
        WARN.value    | "https"
        ENFORCE.value | "http"
        ENFORCE.value | "http%3A"
        ENFORCE.value | "https"
        SKIP.value    | "https"
        SKIP.value    | "http%3A"
        SKIP.value    | "https"
    }

    private static Account getAccountWithSpecifiedBannerMax(String accountId, BidValidationEnforcement bannerMaxSizeEnforcement) {
        def accountConfig = new AccountConfig(
                auction: new AccountAuctionConfig(
                        bidValidations: new AccountBidValidationConfig(bannerMaxSizeEnforcement: bannerMaxSizeEnforcement),
                        debugAllow: true))
        new Account(status: ACTIVE, uuid: accountId, config: accountConfig)
    }
}
