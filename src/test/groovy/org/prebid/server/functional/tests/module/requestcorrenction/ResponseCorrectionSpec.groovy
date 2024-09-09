package org.prebid.server.functional.tests.module.requestcorrenction

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


    def "PBs shouldn't modify response when in account correction module disabled"() {
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
        def modulesConfig = new PbsModulesConfig(pbResponseCorrection: new PbResponseCorrection(
                enabled: responseCorrectionEnabled,
                appVideoHtml: new AppVideoHtml(enabled: appVideoHtmlEnabled)))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: modulesConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        assert getLogsByText(logsByTime, bidResponse.seatbid[0].bid[0].id).size() == 0

        and: "Response should contain seatBid and video media type"
        assert response.seatbid.size() == 1
        assert response.seatbid[0].bid[0].ext.prebid.type == VIDEO

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

    def "PBs shouldn't modify response when requested #distributionChannel and with adm obj"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request video imp"
        def bidRequest = getDefaultBidRequest(distributionChannel).tap {
            imp[0] = Imp.getDefaultImpression(VIDEO)
        }

        and: "Set bidder response with adm obj"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].adm = new Adm(assets: null)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        accountDao.save(accountConfigWithResponseCorrectionModule(bidRequest))

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        def responseCorrection = getLogsByText(logsByTime, bidResponse.seatbid[0].bid[0].id)
        assert responseCorrection.size() == 0

        and: "Response should contain seatBid and video media type"
        assert response.seatbid.size() == 1
        assert response.seatbid[0].bid[0].ext.prebid.type == VIDEO

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings

        where:
        distributionChannel << [SITE, DOOH]
    }

    def "PBs shouldn't modify response for excluded bidder when bidder specified in config"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and Video imp"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp[0] = Imp.getDefaultImpression(VIDEO)
        }

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def bidId = bidResponse.seatbid[0].bid[0].id
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def modulesConfig = new PbsModulesConfig(
                pbResponseCorrection: new PbResponseCorrection(
                        enabled: true, appVideoHtml: new AppVideoHtml(enabled: true, excludedBidders: [GENERIC])))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: modulesConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        assert getLogsByText(logsByTime, bidId).size() == 0

        and: "Response should contain seatBid and video media type"
        assert response.seatbid.size() == 1
        assert response.seatbid[0].bid[0].ext.prebid.type == VIDEO

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings
    }

    def "PBs shouldn't modify response and emit warning when requested video impression respond with adm without VAST keyword"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and Video imp"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp[0] = Imp.getDefaultImpression(VIDEO)
        }

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def bidId = bidResponse.seatbid[0].bid[0].id
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def modulesConfig = new PbsModulesConfig(
                pbResponseCorrection: new PbResponseCorrection(enabled: true,
                        appVideoHtml: new AppVideoHtml(enabled: true)))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: modulesConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBs should emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection[0].contains("Bid $bidId of bidder generic has an JSON ADM, that appears to be native" as String)

        and: "Response should contain seatBid and video media type"
        assert response.seatbid.size() == 1
        assert response.seatbid[0].bid[0].ext.prebid.type == VIDEO

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings
    }

    def "PBs shouldn't modify response and not emit warning when requested impression with #mediaType without adm"() {
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
        def modulesConfig = new PbsModulesConfig(
                pbResponseCorrection: new PbResponseCorrection(
                        enabled: true, appVideoHtml: new AppVideoHtml(enabled: true)))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: modulesConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        def responseCorrection = getLogsByText(logsByTime, bidResponse.seatbid[0].bid[0].id)
        assert responseCorrection.size() == 0

        and: "Response should contain seatBid type"
        assert response.seatbid.size() == 1
        assert response.seatbid[0].bid[0].ext.prebid.type == mediaType
        assert !response?.seatbid[0]?.bid[0]?.ext?.prebid?.meta?.mediaType

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings

        where:
        mediaType << [BANNER, AUDIO]
    }

    def "PBs shouldn't modify response and emit logs when requested impression with native and adm value is asset"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and #mediaType imp"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp[0] = Imp.getDefaultImpression(NATIVE)
        }

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def bidId = bidResponse.seatbid[0].bid[0].id
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def modulesConfig = new PbsModulesConfig(
                pbResponseCorrection: new PbResponseCorrection(
                        enabled: true, appVideoHtml: new AppVideoHtml(enabled: true)))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: modulesConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection[0].contains("Bid $bidId of bidder generic has an JSON ADM, that appears to be native" as String)
        assert responseCorrection.size() == 1

        and: "Response should contain seatBid type"
        assert response.seatbid.size() == 1
        assert response.seatbid[0].bid[0].ext.prebid.type == NATIVE

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings
    }

    def "PBs shouldn't modify response when requested video impression respond with empty adm"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and Video imp"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp[0] = Imp.getDefaultImpression(VIDEO)
        }

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].setAdm(null)
            seatbid[0].bid[0].nurl = PBSUtils.randomString
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def modulesConfig = new PbsModulesConfig(
                pbResponseCorrection: new PbResponseCorrection(
                        enabled: true, appVideoHtml: new AppVideoHtml(enabled: true)))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: modulesConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        def responseCorrection = getLogsByText(logsByTime, bidResponse.seatbid[0].bid[0].id)
        assert responseCorrection.size() == 0

        and: "Response should contain seatBid and video media type"
        assert response.seatbid.size() == 1
        assert response.seatbid[0].bid[0].ext.prebid.type == VIDEO

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings
    }

    def "PBs shouldn't modify response when requested video impression respond with adm VAST keyword"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and Video imp"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp[0] = Imp.getDefaultImpression(VIDEO)
        }

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].setAdm(PBSUtils.getRandomCase("<${PBSUtils.randomString}VAST${PBSUtils.randomString}"))
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def modulesConfig = new PbsModulesConfig(
                pbResponseCorrection: new PbResponseCorrection(
                        enabled: true, appVideoHtml: new AppVideoHtml(enabled: true)))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: modulesConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        def responseCorrection = getLogsByText(logsByTime, bidResponse.seatbid[0].bid[0].id)
        assert responseCorrection.size() == 0

        and: "Response should contain seatBid and video media type"
        assert response.seatbid.size() == 1
        assert response.seatbid[0].bid[0].ext.prebid.type == VIDEO

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings
    }

    def "PBs should modify response when requested #mediaType impression respond with adm VAST keyword"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and #mediaType imp"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp[0] = Imp.getDefaultImpression(mediaType)
        }

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].setAdm(PBSUtils.getRandomCase("<${PBSUtils.randomString}VAST${PBSUtils.randomString}"))
        }
        def bidId = bidResponse.seatbid[0].bid[0].id
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def modulesConfig = new PbsModulesConfig(
                pbResponseCorrection: new PbResponseCorrection(
                        enabled: true, appVideoHtml: new AppVideoHtml(enabled: true)))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: modulesConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection.size() == 1
        assert responseCorrection.any {
            it.contains("Bid $bidId of bidder generic: changing media type to banner" as String)
        }

        and: "Response should contain seatBid and video media type"
        assert response.seatbid.size() == 1
        assert response.seatbid[0].bid[0].ext.prebid.type == BANNER
        assert response.seatbid[0].bid[0].ext.prebid.meta.mediaType == VIDEO.value

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings

        where:
        mediaType << [BANNER, AUDIO, NATIVE]
    }

    def "PBs shouldn't modify response meta.mediaType to video and emit logs when requested impression with video and adm obj with asset"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and audio imp"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp[0] = Imp.getDefaultImpression(VIDEO)
        }

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def bidId = bidResponse.seatbid[0].bid[0].id
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def modulesConfig = new PbsModulesConfig(
                pbResponseCorrection: new PbResponseCorrection(
                        enabled: true, appVideoHtml: new AppVideoHtml(enabled: true)))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: modulesConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBs should emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection.size() == 1
        assert responseCorrection.any {
            it.contains("Bid $bidId of bidder generic has an JSON ADM, that appears to be native" as String)
        }

        and: "Response should contain seatBid type and mediaType"
        assert response.seatbid.size() == 1
        assert response.seatbid[0].bid[0].ext.prebid.type == VIDEO

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings
    }

    def "PBs shouldn't modify response meta.mediaType to video and and emit logs when requested impression with video and adm obj with asset"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and audio imp"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp[0] = Imp.getDefaultImpression(VIDEO)
        }

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def bidId = bidResponse.seatbid[0].bid[0].id
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def modulesConfig = new PbsModulesConfig(
                pbResponseCorrection: new PbResponseCorrection(
                        enabled: true, appVideoHtml: new AppVideoHtml(enabled: true)))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: modulesConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBs should emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection.size() == 1
        assert responseCorrection.any {
            it.contains("Bid $bidId of bidder generic has an JSON ADM, that appears to be native" as String)
        }

        and: "Response should contain seatBid type and mediaType"
        assert response.seatbid.size() == 1
        assert response.seatbid[0].bid[0].ext.prebid.type == VIDEO
        assert !response?.seatbid[0]?.bid[0]?.ext?.prebid?.meta?.mediaType

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings
    }

    def "PBs should modify response meta.mediaType to video and type to banner and emit logs when requested impression with native and adm value is not asset"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Default bid request with APP and #mediaType imp"
        def bidRequest = getDefaultBidRequest(APP).tap {
            imp[0] = Imp.getDefaultImpression(NATIVE)
        }

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].adm = new Adm(assets: null)
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(mediaType: NATIVE)))
        }
        def bidId = bidResponse.seatbid[0].bid[0].id
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account with enabled response correction module"
        def modulesConfig = new PbsModulesConfig(
                pbResponseCorrection: new PbResponseCorrection(
                        enabled: true, appVideoHtml: new AppVideoHtml(enabled: true)))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: modulesConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithResponseCorrectionModule.sendAuctionRequest(bidRequest)

        then: "PBs should emit log"
        def logsByTime = pbsServiceWithResponseCorrectionModule.getLogsByTime(start)
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection.size() == 2
        assert responseCorrection.any {
            it.contains("Bid $bidId of bidder generic has a JSON ADM, but without assets" as String)
            it.contains("Bid $bidId of bidder generic: changing media type to banner" as String)
        }

        and: "Response should contain seatBid type and mediaType"
        assert response.seatbid.size() == 1
        assert response.seatbid[0].bid[0].ext.prebid.type == BANNER
        assert response.seatbid[0].bid[0].ext.prebid.meta.mediaType == VIDEO.value

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "Response shouldn't contain warnings"
        assert !response.ext.warnings
    }

    private static Account accountConfigWithResponseCorrectionModule(BidRequest bidRequest, Boolean enabledResponseCorrection = true,Boolean enabledAppVideoHtml = true) {
        def modulesConfig = new PbsModulesConfig(pbResponseCorrection: new PbResponseCorrection(
                enabled: enabledResponseCorrection, appVideoHtml: new AppVideoHtml(enabled: true)))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: modulesConfig))
        new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
    }
}
