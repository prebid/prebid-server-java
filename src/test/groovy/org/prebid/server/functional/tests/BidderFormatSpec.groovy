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
import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC

class BidderFormatSpec extends BaseSpec {

    @Shared
    private static final RANDOM_NUMBER = PBSUtils.randomNumber

    def "PBS should successfully pass when banner.format weight and height is valid"() {
        given: "Default bid request with banner format"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.format = [new Format(weight: bannerFormatWeight, height: bannerFormatHeight)]
        }

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same banner format as on request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.imp[0]?.banner?.format[0].weight == bannerFormatWeight
        assert bidderRequest?.imp[0]?.banner?.format[0].height == bannerFormatHeight

        where:
        bannerFormatWeight    | bannerFormatHeight
        1                     | 1
        PBSUtils.randomNumber | PBSUtils.randomNumber
    }

    def "PBS should unsuccessfully pass and throw error due to validation banner.format{w.h} when banner.format weight or height is invalid"() {
        given: "Default bid request with banner format"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.format = [new Format(weight: bannerFormatWeight, height: bannerFormatHeight)]
        }

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBs should throw error due to banner.format{w.h} validation"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == "Invalid request format: " +
                "Request imp[0].banner.format[0] must define a valid \"h\" and \"w\" properties"

        where:
        bannerFormatWeight            | bannerFormatHeight
        0                             | PBSUtils.randomNumber
        PBSUtils.randomNumber         | 0
        null                          | PBSUtils.randomNumber
        PBSUtils.randomNumber         | null
        PBSUtils.randomNegativeNumber | PBSUtils.randomNumber
        PBSUtils.randomNumber         | PBSUtils.randomNegativeNumber
    }

    def "PBS should unsuccessfully pass and throw error due to validation banner.format{w.h} when banner.format weight and height is invalid"() {
        given: "Default bid request with banner format"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.format = [new Format(weight: bannerFormatWeight, height: bannerFormatHeight)]
        }

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBs should throw error due to banner.format{w.h} validation"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == "Invalid request format: Request imp[0].banner.format[0] " +
                "should define *either* {w, h} (for static size requirements) " +
                "*or* {wmin, wratio, hratio} (for flexible sizes) to be non-zero positive"

        where:
        bannerFormatWeight            | bannerFormatHeight
        0                             | 0
        0                             | null
        0                             | PBSUtils.randomNegativeNumber
        null                          | null
        null                          | PBSUtils.randomNegativeNumber
        PBSUtils.randomNegativeNumber | PBSUtils.randomNegativeNumber
    }

    def "PBS should successfully pass when banner weight and height is valid"() {
        given: "Default bid request with banner format"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = new Banner(weight: bannerFormatWeight, height: bannerFormatHeight)
        }

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same banner{w.h} as on request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.imp[0]?.banner?.weight == bannerFormatWeight
        assert bidderRequest?.imp[0]?.banner?.height == bannerFormatHeight

        where:
        bannerFormatWeight    | bannerFormatHeight
        1                     | 1
        PBSUtils.randomNumber | PBSUtils.randomNumber
    }

    def "PBS should unsuccessfully pass and throw error due to validation banner{w.h} when banner{w.h} is invalid"() {
        given: "Default bid request with banner{w.h}"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner = new Banner(weight: bannerFormatWeight, height: bannerFormatHeight)
        }

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBs should throw error due to banner{w.h} validation"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == "Invalid request format: " +
                "request.imp[0].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements"

        where:
        bannerFormatWeight            | bannerFormatHeight
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
                banner = new Banner(format: [new Format(weight: RANDOM_NUMBER, height: RANDOM_NUMBER)])
                ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: BidderName.GENERIC)]
            }
        }

        and: "Stored bid response with biggest W and H than in bidRequest in DB"
        def storedBidId = UUID.randomUUID()
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].tap {
                it.id = storedBidId
                it.weight = responseWeight
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
                "bid response size='${responseWeight}x${responseHeight}'"

        and: "Bid response should contain weight and height from stored response"
        def bid = bidResponse.seatbid[0].bid[0]
        assert bid.weight == responseWeight
        assert bid.height == responseHeight

        and: "PBs shouldn't perform a bidder request due to stored bid response"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
        accountCretiveMaxSize | configCreativeMaxSize | responseWeight    | responseHeight
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
                banner = new Banner(format: [new Format(weight: RANDOM_NUMBER, height: RANDOM_NUMBER)])
                ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: BidderName.GENERIC)]
            }
        }

        and: "Stored bid response with biggest W and H than in bidRequest in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].tap {
                it.weight = responseWeight
                it.height = responseHeight
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

        and: "Bid response should contain weight and height from stored response"
        def bid = bidResponse.seatbid[0].bid[0]
        assert bid.weight == responseWeight
        assert bid.height == responseHeight

        and: "PBs shouldn't perform a bidder request due to stored bid response"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
        accountCretiveMaxSize | configCreativeMaxSize | responseWeight    | responseHeight
        null                  | SKIP.value            | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        null                  | SKIP.value            | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        null                  | SKIP.value            | RANDOM_NUMBER     | RANDOM_NUMBER + 1
        SKIP                  | null                  | RANDOM_NUMBER + 1 | RANDOM_NUMBER + 1
        SKIP                  | null                  | RANDOM_NUMBER + 1 | RANDOM_NUMBER
        SKIP                  | null                  | RANDOM_NUMBER     | RANDOM_NUMBER + 1
    }

    def "PBS should emit error and metrics and remove bid response from consideration when banner-creative-max-size: enforce and bid response W or H is larger that request W or H"() {
        given: "PBS with banner creative max size"
        def pbsService = pbsServiceFactory.getService(["auction.validations.banner-creative-max-size": configCreativeMaxSize])

        and: "Default bid request with banner format"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
                banner = new Banner(format: [new Format(weight: RANDOM_NUMBER, height: RANDOM_NUMBER)])
                ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: BidderName.GENERIC)]
            }
        }

        and: "Stored bid response with biggest W and H than in bidRequest in DB"
        def storedBidId = UUID.randomUUID()
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].tap {
                it.id = storedBidId
                it.weight = responseWeight
                it.height = responseHeight
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
        assert metrics["account.${bidRequest.accountId}.response.validation.size.err"] == 1
        assert metrics["adapter.generic.response.validation.size.err"] == 1

        and: "Response should contain error"
        assert bidResponse.ext?.errors[GENERIC]*.code == [5]
        assert bidResponse.ext?.errors[GENERIC]*.message[0]
                == "BidId `${storedBidId}` validation messages: " +
                "Error: BidResponse validation `enforce`: bidder `${GENERIC.value}` response triggers creative size " +
                "validation for bid ${storedBidId}, account=${bidRequest.accountId}, " +
                "referrer=${bidRequest.site.page}, max imp size='${RANDOM_NUMBER}x${RANDOM_NUMBER}', " +
                "bid response size='${responseWeight}x${responseHeight}'"

        and: "Pbs should discard seatBid due to validation"
        assert !bidResponse.seatbid

        and: "PBs shouldn't perform a bidder request due to stored bid response"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
        accountCretiveMaxSize | configCreativeMaxSize | responseWeight    | responseHeight
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
                banner = new Banner(format: [new Format(weight: RANDOM_NUMBER, height: RANDOM_NUMBER)])
                ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: BidderName.GENERIC)]
            }
        }

        and: "Stored bid response with biggest W and H than in bidRequest in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].tap {
                weight = RANDOM_NUMBER
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

        and: "Bid response should contain weight and height from stored response"
        def bid = bidResponse.seatbid[0].bid[0]
        assert bid.weight == RANDOM_NUMBER
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
                video = new Video(weight: RANDOM_NUMBER, height: RANDOM_NUMBER, mimes: [PBSUtils.randomString])
                ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: BidderName.GENERIC)]
            }
        }

        and: "Stored bid response with biggest W and H than in bidRequest in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].tap {
                weight = responseWeight
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
        accountCretiveMaxSize | configCreativeMaxSize | responseWeight    | responseHeight
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
                banner = new Banner(format: [new Format(weight: RANDOM_NUMBER, height: RANDOM_NUMBER)])
                ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: BidderName.GENERIC)]
            }
        }

        and: "Stored bid response with biggest W and H than in bidRequest in DB"
        def storedBidId = UUID.randomUUID()
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].tap {
                it.id = storedBidId
                it.weight = responseWeight
                it.height = responseHeight
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
        assert metrics["account.${bidRequest.accountId}.response.validation.size.err"] == 1
        assert metrics["adapter.generic.response.validation.size.err"] == 1

        and: "Bid response should contain error"
        assert bidResponse.ext?.errors[GENERIC]*.code == [5]
        assert bidResponse.ext?.errors[GENERIC]*.message[0]
                == "BidId `${storedBidId}` validation messages: " +
                "Error: BidResponse validation `enforce`: bidder `generic` response triggers creative size " +
                "validation for bid ${storedBidId}, account=${bidRequest.accountId}, " +
                "referrer=${bidRequest.site.page}, max imp size='${RANDOM_NUMBER}x${RANDOM_NUMBER}', " +
                "bid response size='${responseWeight}x${responseHeight}'"

        and: "Pbs should discard seatBid due to validation"
        assert !bidResponse.seatbid

        and: "PBs shouldn't perform a bidder request due to stored bid response"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
        accountCretiveMaxSize | configCreativeMaxSize | responseWeight    | responseHeight
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
        secure | secureMarkup
        1      | SKIP.value
        1      | ENFORCE.value
        1      | WARN.value
        0      | SKIP.value
        0      | ENFORCE.value
        0      | WARN.value
    }

    def "PBS should emit metrics and error when imp[0].secure = 1 and config WARN and bid response adm contain #url"() {
        given: "PBS with secure-markUp: warn"
        def pbsService = pbsServiceFactory.getService(["auction.validations.secure-markup": WARN.value])

        and: "Default bid request with secure and banner or video or nativeObj"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].secure = 1
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
            imp[0].secure = 1
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
            imp[0].secure = 1
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
        url       | secure | secureMarkup
        "http%3A" | 0      | SKIP.value
        "http"    | 0      | SKIP.value
        "https"   | 1      | SKIP.value
        "http%3A" | 0      | WARN.value
        "http"    | 0      | WARN.value
        "https"   | 1      | WARN.value
        "http%3A" | 0      | ENFORCE.value
        "http"    | 0      | ENFORCE.value
        "https"   | 1      | ENFORCE.value
    }

    def "PBS should ignore specified secureMarkup #secureMarkup validation when secure is 0"() {
        given: "PBS with secure-markUp"
        def pbsService = pbsServiceFactory.getService(["auction.validations.secure-markup": secureMarkup])

        and: "Default bid request with stored bid response and secure"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
                secure = 0
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
