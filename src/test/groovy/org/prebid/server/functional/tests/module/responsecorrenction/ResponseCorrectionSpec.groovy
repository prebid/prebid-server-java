package org.prebid.server.functional.tests.module.responsecorrenction

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountHooksConfiguration
import org.prebid.server.functional.model.config.AppVideoHtml
import org.prebid.server.functional.model.config.PbResponseCorrection
import org.prebid.server.functional.model.config.PbsModulesConfig
import org.prebid.server.functional.model.config.SuppressIbv
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.Native
import org.prebid.server.functional.model.response.auction.Adm
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidExt
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.Meta
import org.prebid.server.functional.model.response.auction.Prebid
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.module.ModuleBaseSpec
import org.prebid.server.functional.util.PBSUtils

import java.time.Instant

import static org.prebid.server.functional.model.request.auction.BidRequest.getDefaultBidRequest
import static org.prebid.server.functional.model.request.auction.BidRequest.getDefaultVideoRequest
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.DOOH
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.*
import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC
import static org.prebid.server.functional.model.response.auction.MediaType.AUDIO
import static org.prebid.server.functional.model.response.auction.MediaType.BANNER
import static org.prebid.server.functional.model.response.auction.MediaType.NATIVE
import static org.prebid.server.functional.model.response.auction.MediaType.VIDEO

class ResponseCorrectionSpec extends ModuleBaseSpec {

    private final static int OPTIMAL_MAX_LENGTH = 20
    private static final Map PBS_CONFIG = ["adapter-defaults.modifying-vast-xml-allowed": "false",
                                           "adapters.generic.modifying-vast-xml-allowed": "false"] +
            getResponseCorrectionConfig()

    private static final PrebidServerService pbsServiceWithResponseCorrectionModule = pbsServiceFactory.getService(PBS_CONFIG)

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(PBS_CONFIG)
    }

    def "PBS shouldn't modify response when in account correction module disabled"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and Video imp"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp[0] = Imp.getDefaultImpression(VIDEO)
        }

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndAppVideoHtml(bidRequest.getAccountId(), responseCorrectionEnabled, appVideoHtmlEnabled)
        accountDao.save(accountWithResponseCorrectionModule)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        assert getLogsByText(logsByTime, bidResponse.seatbid[0].bid[0].id).size() == 0

        and: "Response should contain seatBid"
        assert response.seatbid.size() == 1

        and: "Response should contain single seatBid with proper media type"
        assert response.seatbid.bid.ext.prebid.type.flatten() == [VIDEO]

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings

        where:
        responseCorrectionEnabled | appVideoHtmlEnabled
        false                     | true
        true                      | false
        false                     | false
    }

    def "PBS shouldn't modify response with adm obj when request includes #distributionChannel distribution channel"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request video imp"
        def bidRequest = getDefaultVideoRequest(distributionChannel)

        and: "Set bidder response with adm obj"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].adm = new Adm()
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndAppVideoHtml(bidRequest.getAccountId())
        accountDao.save(accountWithResponseCorrectionModule)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        assert getLogsByText(logsByTime, bidResponse.seatbid[0].bid[0].id).size() == 0

        and: "Response should contain seatBid"
        assert response.seatbid.size() == 1

        and: "Response should contain single seatBid with proper media type"
        assert response.seatbid.bid.ext.prebid.type.flatten() == [VIDEO]

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings

        where:
        distributionChannel << [SITE, DOOH]
    }

    def "PBS shouldn't modify response for excluded bidder when bidder specified in config"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and Video imp"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp[0] = Imp.getDefaultImpression(VIDEO)
        }

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module and excluded bidders"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndAppVideoHtml(bidRequest.getAccountId()).tap {
            config.hooks.modules.pbResponseCorrection.appVideoHtml.excludedBidders = [BidderName.GENERIC]
        }
        accountDao.save(accountWithResponseCorrectionModule)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        assert getLogsByText(logsByTime, bidResponse.seatbid[0].bid[0].id).size() == 0

        and: "Response should contain seatBid"
        assert response.seatbid.size() == 1

        and: "Response should contain single seatBid with proper media type"
        assert response.seatbid.bid.ext.prebid.type.flatten() == [VIDEO]

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings
    }

    def "PBS shouldn't modify response and emit warning when requested video impression respond with adm without VAST keyword"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and Video imp"
        def bidRequest = getDefaultVideoRequest(APP)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndAppVideoHtml(bidRequest.getAccountId())
        accountDao.save(accountWithResponseCorrectionModule)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBS should emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        def bidId = bidResponse.seatbid[0].bid[0].id
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection[0].contains("Bid $bidId of bidder generic has an JSON ADM, that appears to be native" as String)

        and: "Response should contain seatBid"
        assert response.seatbid.size() == 1

        and: "Response should contain single seatBid with proper media type"
        assert response.seatbid.bid.ext.prebid.type.flatten() == [VIDEO]

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings
    }

    def "PBS shouldn't modify response without adm obj when request includes #mediaType media type"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and #mediaType imp"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp[0] = Imp.getDefaultImpression(mediaType)
        }

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndAppVideoHtml(bidRequest.getAccountId())
        accountDao.save(accountWithResponseCorrectionModule)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        assert getLogsByText(logsByTime, bidResponse.seatbid[0].bid[0].id).size() == 0

        and: "Response should contain seatBid"
        assert response.seatbid.size() == 1

        and: "Response should contain single seatBid with proper media type"
        assert response.seatbid.bid.ext.prebid.type.flatten() == [mediaType]

        and: "Response shouldn't contain media type for prebid meta"
        assert !response?.seatbid?.bid?.ext?.prebid?.meta?.mediaType?.flatten()?.size()

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings

        where:
        mediaType << [BANNER, AUDIO]
    }

    def "PBS shouldn't modify response and emit logs when requested impression with native and adm value is asset"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and #mediaType imp"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp[0] = Imp.getDefaultImpression(NATIVE)
        }

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndAppVideoHtml(bidRequest.getAccountId())
        accountDao.save(accountWithResponseCorrectionModule)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBS should emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        def bidId = bidResponse.seatbid[0].bid[0].id
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection[0].contains("Bid $bidId of bidder generic has an JSON ADM, that appears to be native" as String)
        assert responseCorrection.size() == 1

        and: "Response should contain seatBid"
        assert response.seatbid.size() == 1

        and: "Response should contain single seatBid with proper media type"
        assert response.seatbid.bid.ext.prebid.type.flatten() == [NATIVE]

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings
    }

    def "PBS shouldn't modify response when requested video impression respond with empty adm"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and Video imp"
        def bidRequest = getDefaultVideoRequest(APP)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].setAdm(null)
            seatbid[0].bid[0].nurl = PBSUtils.randomString
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndAppVideoHtml(bidRequest.getAccountId())
        accountDao.save(accountWithResponseCorrectionModule)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        assert getLogsByText(logsByTime, bidResponse.seatbid[0].bid[0].id).size() == 0

        and: "Response should contain seatBid"
        assert response.seatbid.size() == 1

        and: "Response should contain single seatBid with proper media type"
        assert response.seatbid.bid.ext.prebid.type.flatten() == [VIDEO]

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings
    }

    def "PBS shouldn't modify response when requested video impression respond with adm VAST keyword"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and Video imp"
        def bidRequest = getDefaultVideoRequest(APP)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].setAdm(PBSUtils.getRandomCase(admValue))
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndAppVideoHtml(bidRequest.getAccountId())
        accountDao.save(accountWithResponseCorrectionModule)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        assert getLogsByText(logsByTime, bidResponse.seatbid[0].bid[0].id).size() == 0

        and: "Response should contain seatBid"
        assert response.seatbid.size() == 1

        and: "Response should contain single seatBid with proper media type"
        assert response.seatbid.bid.ext.prebid.type.flatten() == [VIDEO]

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings

        where:
        admValue << [
                "${PBSUtils.randomString}<${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}VAST ${PBSUtils.randomString}",
                "${PBSUtils.randomString}<${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}VAST ${PBSUtils.randomString}>",
                "${PBSUtils.randomString}${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}<${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}VAST ${PBSUtils.randomString}>",
                "<${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}VAST${' ' * PBSUtils.getRandomNumber(1, OPTIMAL_MAX_LENGTH)}",
                "<${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}VAST${' ' * PBSUtils.getRandomNumber(1, OPTIMAL_MAX_LENGTH)}>",
                "<${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}VAST${' ' * PBSUtils.getRandomNumber(1, OPTIMAL_MAX_LENGTH)}${PBSUtils.randomString}>"
        ]
    }

    def "PBS should modify response when requested video impression respond with invalid adm VAST keyword"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and Video imp"
        def bidRequest = getDefaultVideoRequest(APP)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].setAdm(PBSUtils.getRandomCase(admValue))
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndAppVideoHtml(bidRequest.getAccountId())
        accountDao.save(accountWithResponseCorrectionModule)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBS should emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        def bidId = bidResponse.seatbid[0].bid[0].id
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection.size() == 1
        assert responseCorrection.any {
            it.contains("Bid $bidId of bidder generic: changing media type to banner" as String)
        }

        and: "Response should contain seatBid"
        assert response.seatbid.size() == 1

        and: "Response should contain single seatBid with proper media type"
        assert response.seatbid.bid.ext.prebid.type.flatten() == [BANNER]

        and: "Response should contain single seatBid with proper meta media type"
        assert response.seatbid.bid.ext.prebid.meta.mediaType.flatten() == [VIDEO.value]

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings

        where:
        admValue << [
                "<${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}VAST${PBSUtils.randomString}",
                "<${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}VAST",
                "<${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}VAST>",
                "<${PBSUtils.randomString}VAST${' ' * PBSUtils.getRandomNumber(1, OPTIMAL_MAX_LENGTH)}"
        ]
    }

    def "PBS should modify response when requested #mediaType impression respond with adm VAST keyword"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and #mediaType imp"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp[0] = Imp.getDefaultImpression(mediaType)
        }

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].setAdm(PBSUtils.getRandomCase(admValue))
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndAppVideoHtml(bidRequest.getAccountId())
        accountDao.save(accountWithResponseCorrectionModule)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBS should emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        def bidId = bidResponse.seatbid[0].bid[0].id
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection.size() == 1
        assert responseCorrection.any {
            it.contains("Bid $bidId of bidder generic: changing media type to banner" as String)
        }

        and: "Response should contain seatBid"
        assert response.seatbid.size() == 1

        and: "Response should contain single seatBid with proper media type"
        assert response.seatbid.bid.ext.prebid.type.flatten() == [BANNER]

        and: "Response should contain single seatBid with proper meta media type"
        assert response.seatbid.bid.ext.prebid.meta.mediaType.flatten() == [VIDEO.value]

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings

        where:
        mediaType | admValue
        BANNER    | "${PBSUtils.randomString}<${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}VAST${PBSUtils.randomString}"
        BANNER    | "<${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}VAST${' ' * PBSUtils.getRandomNumber(1, OPTIMAL_MAX_LENGTH)}"
        BANNER    | "<${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}}VAST${' ' * PBSUtils.getRandomNumber(1, OPTIMAL_MAX_LENGTH)}${PBSUtils.randomString}"
        AUDIO     | "${PBSUtils.randomString}<${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}VAST${PBSUtils.randomString}"
        AUDIO     | "<${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}VAST${' ' * PBSUtils.getRandomNumber(1, OPTIMAL_MAX_LENGTH)}"
        AUDIO     | "<${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}}VAST${' ' * PBSUtils.getRandomNumber(1, OPTIMAL_MAX_LENGTH)}${PBSUtils.randomString}"
        NATIVE    | "${PBSUtils.randomString}<${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}VAST${PBSUtils.randomString}"
        NATIVE    | "<${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}VAST${' ' * PBSUtils.getRandomNumber(1, OPTIMAL_MAX_LENGTH)}"
        NATIVE    | "<${' ' * PBSUtils.getRandomNumber(0, OPTIMAL_MAX_LENGTH)}}VAST${' ' * PBSUtils.getRandomNumber(1, OPTIMAL_MAX_LENGTH)}${PBSUtils.randomString}"
    }

    def "PBS shouldn't modify response meta.mediaType to video and emit logs when requested impression with video and adm obj with asset"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and audio imp"
        def bidRequest = getDefaultVideoRequest(APP)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndAppVideoHtml(bidRequest.getAccountId())
        accountDao.save(accountWithResponseCorrectionModule)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBS should emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        def bidId = bidResponse.seatbid[0].bid[0].id
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection.size() == 1
        assert responseCorrection.any {
            it.contains("Bid $bidId of bidder generic has an JSON ADM, that appears to be native" as String)
        }

        and: "Response should contain seatBib"
        assert response.seatbid.size() == 1

        and: "Response should contain single seatBid with proper media type"
        assert response.seatbid.bid.ext.prebid.type.flatten() == [VIDEO]

        and: "Response shouldn't contain media type for prebid meta"
        assert !response?.seatbid?.bid?.ext?.prebid?.meta?.mediaType?.flatten()?.size()

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings
    }

    def "PBS should modify meta.mediaType and type for original response and also emit logs when response contains native meta.mediaType and adm without asset"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and #mediaType imp"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp[0] = Imp.getDefaultImpression(NATIVE)
        }

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].adm = new Adm()
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(mediaType: NATIVE)))
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndAppVideoHtml(bidRequest.getAccountId())
        accountDao.save(accountWithResponseCorrectionModule)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBS should emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        def bidId = bidResponse.seatbid[0].bid[0].id
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection.size() == 2
        assert responseCorrection.any {
            it.contains("Bid $bidId of bidder generic has a JSON ADM, but without assets" as String)
        }
        assert responseCorrection.any {
            it.contains("Bid $bidId of bidder generic: changing media type to banner" as String)
        }

        and: "Response should contain seatBid"
        assert response.seatbid.size() == 1

        and: "Response should contain single seatBid with proper media type"
        assert response.seatbid.bid.ext.prebid.type.flatten() == [BANNER]

        and: "Response should media type for prebid meta"
        assert response.seatbid.bid.ext.prebid.meta.mediaType.flatten() == [VIDEO.value]

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings
    }

    def "PBS shouldn't reject auction with 353 code when enabled response correction is #enabledResponseCorrection and enabled suppress ibv is #enabledSuppressIbv"() {
        given: "Default bid request with banner and APP"
        def bidRequest = getDefaultBidRequest(APP).tap {
            ext.prebid.returnAllBidStatus = true
        }

        and: "Save account with enabled response correction module and suppress ibv"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndSuppressIbv(bidRequest.getAccountId(), enabledResponseCorrection, enabledSuppressIbv)
        accountDao.save(accountWithResponseCorrectionModule)

        and: "Set bidder response with meta media type"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(mediaType: VIDEO.value)))
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Response should contain seatBid"
        assert response.seatbid.size() == 1

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings

        and: "PBS response shouldn't contain seatNonBid"
        assert !response.ext.seatnonbid

        and: "Bidder request should be invoke"
        assert bidder.getBidderRequest(bidRequest.id)

        where:
        enabledResponseCorrection | enabledSuppressIbv
        false                     | true
        true                      | false
        false                     | false
    }

    def "PBS shouldn't reject auction with 353 code when enabled suppress ibv and bid request with imp media type is #mediaType"() {
        given: "Default bid request with APP"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp[0] = Imp.getDefaultImpression(mediaType)
            ext.prebid.returnAllBidStatus = true
        }

        and: "Save account with enabled response correction module and suppress ibv"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndSuppressIbv(bidRequest.getAccountId())
        accountDao.save(accountWithResponseCorrectionModule)

        and: "Set bidder response with meta media type"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(mediaType: VIDEO.value)))
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Response should contain seatBid"
        assert response.seatbid.size() == 1

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings

        and: "PBS response shouldn't contain seatNonBid"
        assert !response.ext.seatnonbid

        and: "Bidder request should be invoke"
        assert bidder.getBidderRequest(bidRequest.id)

        where:
        mediaType << [VIDEO, NATIVE, AUDIO]
    }

    def "PBS shouldn't reject auction with 353 code when enabled suppress ibv and bis response with meta media type is #mediaType"() {
        given: "Default bid request with banner and APP"
        def bidRequest = getDefaultBidRequest(APP).tap {
            ext.prebid.returnAllBidStatus = true
        }

        and: "Save account with enabled response correction module and suppress ibv"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndSuppressIbv(bidRequest.getAccountId())
        accountDao.save(accountWithResponseCorrectionModule)

        and: "Set bidder response with meta media type"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(mediaType: mediaType.value)))
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Response should contain seatBid"
        assert response.seatbid.size() == 1

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings

        and: "PBS response shouldn't contain seatNonBid"
        assert !response.ext.seatnonbid

        and: "Bidder request should be invoke"
        assert bidder.getBidderRequest(bidRequest.id)

        where:
        mediaType << [BANNER, NATIVE, AUDIO]
    }

    def "PBS shouldn't reject auction with 353 code when requested bidder is excluded"() {
        given: "Default bid request with banner and APP"
        def bidRequest = getDefaultBidRequest(APP).tap {
            ext.prebid.returnAllBidStatus = true
        }

        and: "Save account with enabled response correction module and suppress ibv"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndSuppressIbv(bidRequest.getAccountId()).tap {
            config.hooks.modules.pbResponseCorrection.suppressInBannerVideo.excludedBidders = bidderName
        }
        accountDao.save(accountWithResponseCorrectionModule)

        and: "Set bidder response with meta media type"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(mediaType: VIDEO.value)))
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Response should contain seatBid"
        assert response.seatbid.size() == 1

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings

        and: "PBS response shouldn't contain seatNonBid"
        assert !response.ext.seatnonbid

        and: "Bidder request should be invoke"
        assert bidder.getBidderRequest(bidRequest.id)

        where:
        bidderName << [[BidderName.GENERIC_CAMEL_CASE, BidderName.GENERIC],
                       [BidderName.GENERIC],
                       [BidderName.GENERIC, BidderName.OPENX],
                       [BidderName.OPENX, BidderName.GENERIC],
                       [BidderName.GENERIC_CAMEL_CASE],
                       [BidderName.GENERIC, BidderName.GENERIC_CAMEL_CASE],]
    }

    def "PBS should reject auction with 353 code when enabled suppress ibv and imp with media type Banner and bid response meta media type with Video and excluded bidder are not in case"() {
        given: "Default bid request with banner and APP"
        def bidRequest = getDefaultBidRequest(APP).tap {
            ext.prebid.returnAllBidStatus = true
        }

        and: "Save account with enabled response correction module and suppress ibv"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndSuppressIbv(bidRequest.getAccountId()).tap {
            config.hooks.modules.pbResponseCorrection.suppressInBannerVideo.excludedBidders = bidderName
        }
        accountDao.save(accountWithResponseCorrectionModule)

        and: "Set bidder response with meta media type"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(mediaType: VIDEO.value)))
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == BidderName.GENERIC
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_DUE_TO_IN_BANNER_VIDEO

        and: "Seat bid should be empty"
        assert response.seatbid.isEmpty()

        where:
        bidderName << [[], [BidderName.GENER_X], [BidderName.WILDCARD], [BidderName.UNKNOWN, BidderName.BOGUS]]
    }

    def "PBS should reject auction with 353 code when enabled suppress ibv and imp with media type Banner and bid response meta media type with Video"() {
        given: "Default bid request with banner and APP"
        def bidRequest = getDefaultBidRequest(APP).tap {
            ext.prebid.returnAllBidStatus = true
        }

        and: "Save account with enabled response correction module and suppress ibv"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndSuppressIbv(bidRequest.getAccountId())
        accountDao.save(accountWithResponseCorrectionModule)

        and: "Set bidder response with meta media type"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(mediaType: VIDEO.value)))
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == BidderName.GENERIC
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_DUE_TO_IN_BANNER_VIDEO

        and: "Seat bid should be empty"
        assert response.seatbid.isEmpty()
    }

    def "PBS should reject auction with 353 code when enabled suppress ibv and multi-imp with different media type and bid response meta media type with Video"() {
        given: "Default bid request with banner and APP"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp.add(Imp.getDefaultImpression(VIDEO))
            imp.add(Imp.getDefaultImpression(AUDIO))
            imp.add(Imp.getDefaultImpression(NATIVE))
        }

        and: "Save account with enabled response correction module and suppress ibv"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndSuppressIbv(bidRequest.getAccountId())
        accountDao.save(accountWithResponseCorrectionModule)

        and: "Set bidder response with meta media type"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(mediaType: VIDEO.value)))
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == BidderName.GENERIC
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_DUE_TO_IN_BANNER_VIDEO

        and: "Seat bid should be empty"
        assert response.seatbid.isEmpty()
    }

    def "PBS should reject auction with 353 code when enabled suppress ibv and multi-imp with Banner media type and bid response meta media type with Video"() {
        given: "Default bid request with banner and APP"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp.add(Imp.getDefaultImpression(BANNER))
        }

        and: "Save account with enabled response correction module and suppress ibv"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndSuppressIbv(bidRequest.getAccountId())
        accountDao.save(accountWithResponseCorrectionModule)

        and: "Set bidder response with meta media type"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(mediaType: VIDEO.value)))
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for called bidder"
        assert response.ext.seatnonbid.size() == 2

        def seatNonBid = response.ext.seatnonbid
        assert seatNonBid.seat == [BidderName.GENERIC, BidderName.GENERIC]
        assert seatNonBid.nonBid[0].impId == [bidRequest.imp[0].id, bidRequest.imp[1].id]
        assert seatNonBid.nonBid[0].statusCode == [RESPONSE_REJECTED_DUE_TO_IN_BANNER_VIDEO, RESPONSE_REJECTED_DUE_TO_IN_BANNER_VIDEO]

        and: "Seat bid should be empty"
        assert response.seatbid.isEmpty()
    }

    def "PBS should reject auction with 353 code when enabled suppress ibv and multi-imp with multi-bid response media type and bid response meta media type with Video"() {
        given: "Default bid request with banner and APP"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp.add(Imp.getDefaultImpression(BANNER))
        }

        and: "Save account with enabled response correction module and suppress ibv"
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModuleAndSuppressIbv(bidRequest.getAccountId())
        accountDao.save(accountWithResponseCorrectionModule)

        and: "Set bidder response with meta media type"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(mediaType: VIDEO.value)))
            seatbid[0].bid[1].ext = new BidExt(prebid: new Prebid(meta: new Meta(mediaType: BANNER.value)))
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid
        assert seatNonBid.seat == [BidderName.GENERIC]
        assert seatNonBid.nonBid[0].impId == [bidRequest.imp[0].id]
        assert seatNonBid.nonBid[0].statusCode == [RESPONSE_REJECTED_DUE_TO_IN_BANNER_VIDEO]

        and: "Seat bid should be empty"
        assert response.seatbid.isEmpty()
    }

    private static Account accountConfigWithResponseCorrectionModuleAndAppVideoHtml(String accountId, boolean enabledResponseCorrection = true, boolean enabledAppVideoHtml = true) {
        accountConfigWithResponseCorrectionModule(accountId, enabledResponseCorrection, enabledAppVideoHtml, false)
    }

    private static Account accountConfigWithResponseCorrectionModuleAndSuppressIbv(String accountId, boolean enabledResponseCorrection = true, boolean enabledSuppressIbv = true) {
        accountConfigWithResponseCorrectionModule(accountId, enabledResponseCorrection, false, enabledSuppressIbv)
    }

    private static Account accountConfigWithResponseCorrectionModule(String accountId, boolean enabledResponseCorrection, boolean enabledAppVideoHtml = true, boolean enabledSuppressIbv) {
        def pbResponseCorrection = new PbResponseCorrection().tap {
            enabled = enabledResponseCorrection
            appVideoHtml = new AppVideoHtml(enabled: enabledAppVideoHtml)
            suppressInBannerVideo = new SuppressIbv(enabled: enabledSuppressIbv)
        }
        def modulesConfig = new PbsModulesConfig(pbResponseCorrection: pbResponseCorrection)
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: modulesConfig))
        new Account(uuid: accountId, config: accountConfig)
    }
}
