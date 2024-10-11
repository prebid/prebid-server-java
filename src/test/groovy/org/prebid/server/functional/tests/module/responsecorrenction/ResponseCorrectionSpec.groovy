package org.prebid.server.functional.tests.module.responsecorrenction

import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountHooksConfiguration
import org.prebid.server.functional.model.config.AppVideoHtml
import org.prebid.server.functional.model.config.PbResponseCorrection
import org.prebid.server.functional.model.config.PbsModulesConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.response.auction.Adm
import org.prebid.server.functional.model.response.auction.BidExt
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.Meta
import org.prebid.server.functional.model.response.auction.Prebid
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.module.ModuleBaseSpec
import org.prebid.server.functional.util.PBSUtils

import java.time.Instant

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.auction.BidRequest.getDefaultBidRequest
import static org.prebid.server.functional.model.request.auction.BidRequest.getDefaultVideoRequest
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.DOOH
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.response.auction.MediaType.AUDIO
import static org.prebid.server.functional.model.response.auction.MediaType.BANNER
import static org.prebid.server.functional.model.response.auction.MediaType.NATIVE
import static org.prebid.server.functional.model.response.auction.MediaType.VIDEO

class ResponseCorrectionSpec extends ModuleBaseSpec {

    private final PrebidServerService pbsServiceWithResponseCorrectionModule = pbsServiceFactory.getService(
            ["adapter-defaults.modifying-vast-xml-allowed": "false",
             "adapters.generic.modifying-vast-xml-allowed": "false"] +
                    responseCorrectionConfig)
    private final int OPTIMAL_MAX_LENGTH = 20

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
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModule(bidRequest, responseCorrectionEnabled, appVideoHtmlEnabled)
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
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModule(bidRequest)
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
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModule(bidRequest).tap {
            config.hooks.modules.pbResponseCorrection.appVideoHtml.excludedBidders = [GENERIC]
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
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModule(bidRequest)
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
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModule(bidRequest)
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
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModule(bidRequest)
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
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModule(bidRequest)
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
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModule(bidRequest)
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
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModule(bidRequest)
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
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModule(bidRequest)
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
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModule(bidRequest)
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
        def accountWithResponseCorrectionModule = accountConfigWithResponseCorrectionModule(bidRequest)
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

    private static Account accountConfigWithResponseCorrectionModule(BidRequest bidRequest, Boolean enabledResponseCorrection = true, Boolean enabledAppVideoHtml = true) {
        def modulesConfig = new PbsModulesConfig(pbResponseCorrection: new PbResponseCorrection(
                enabled: enabledResponseCorrection, appVideoHtml: new AppVideoHtml(enabled: enabledAppVideoHtml)))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: modulesConfig))
        new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
    }
}
