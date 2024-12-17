package org.prebid.server.functional.tests.bidder.openx

import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.InterestGroupAuctionBuyer
import org.prebid.server.functional.model.response.auction.InterestGroupAuctionIntent
import org.prebid.server.functional.model.response.auction.InterestGroupAuctionSeller
import org.prebid.server.functional.model.response.auction.OpenxBidResponse
import org.prebid.server.functional.model.response.auction.OpenxBidResponseExt
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Shared

import static org.prebid.server.functional.model.request.auction.AuctionEnvironment.NOT_SUPPORTED
import static org.prebid.server.functional.model.request.auction.AuctionEnvironment.DEVICE_ORCHESTRATED
import static org.prebid.server.functional.model.request.auction.PaaFormant.IAB
import static org.prebid.server.functional.model.request.auction.PaaFormant.ORIGINAL
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer

class OpenxSpec extends BaseSpec {

    private static final Map OPENX_CONFIG = ["adapters.openx.enabled" : "true",
                                             "adapters.openx.endpoint": "$networkServiceContainer.rootUri/auction".toString()]

    @Shared
    PrebidServerService pbsService = pbsServiceFactory.getService(OPENX_CONFIG)

    def "PBS should populate fledge config when bid response with fledge and imp[0].ext.ae = 1"() {
        given: "Default basic BidRequest with ae and openx bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.ae = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
        }

        and: "Default bid response with fledge config"
        def impId = bidRequest.imp[0].id
        def fledgeConfig = [(PBSUtils.randomString): PBSUtils.randomString]
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest).tap {
            ext = new OpenxBidResponseExt().tap {
                fledgeAuctionConfigs = [(impId): fledgeConfig]
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain fledge config"
        def auctionConfigs = response.ext?.prebid?.fledge?.auctionConfigs
        assert auctionConfigs?.size() == 1
        assert auctionConfigs[0].impId == impId
        assert auctionConfigs[0].bidder == bidResponse.seatbid[0].seat.value
        assert auctionConfigs[0].adapter == bidResponse.seatbid[0].seat.value
        assert auctionConfigs[0].config == fledgeConfig
    }

    def "PBS shouldn't populate fledge config when imp[0].ext.ae = 0"() {
        given: "Default basic BidRequest without ae"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.ae = NOT_SUPPORTED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
        }

        and: "Default bid response"
        def impId = bidRequest.imp[0].id
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest).tap {
            ext = new OpenxBidResponseExt().tap {
                fledgeAuctionConfigs = [(impId): [(PBSUtils.randomString): PBSUtils.randomString]]
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain fledge config"
        assert !response.ext.prebid.fledge
    }

    def "PBS shouldn't populate fledge config when imp[0].ext.ae = 1 and bid response didn't return fledge config"() {
        given: "Default basic BidRequest without ae"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.ae = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
        }

        and: "Default bid response"
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest).tap {
            ext = new OpenxBidResponseExt().tap {
                fledgeAuctionConfigs = null
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain fledge config"
        assert !response.ext.prebid.fledge
    }

    def "PBS should populate fledge and iab output config when bid response with fledge and paa formant iab"() {
        given: "Default bid request with openx"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.ae = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            ext.prebid.paaFormat = requestPaaFormant
        }

        and: "Default bid response with fledge config"
        def impId = bidRequest.imp[0].id
        def fledgeConfig = [(PBSUtils.randomString): PBSUtils.randomString]
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest).tap {
            ext = new OpenxBidResponseExt().tap {
                fledgeAuctionConfigs = [(impId): fledgeConfig]
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(paaformat: accountPaaFormat))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain fledge config"
        def auctionConfigs = response.ext?.prebid?.fledge?.auctionConfigs
        assert auctionConfigs?.size() == 1
        assert auctionConfigs[0].impId == impId
        assert auctionConfigs[0].bidder == bidResponse.seatbid[0].seat.value
        assert auctionConfigs[0].adapter == bidResponse.seatbid[0].seat.value
        assert auctionConfigs[0].config == fledgeConfig

        and: "PBS response should contain igs config"
        def interestGroupAuctionSeller = response.ext.interestGroupAuctionIntent[0].interestGroupAuctionSeller[0]
        assert interestGroupAuctionSeller.impId == impId
        assert interestGroupAuctionSeller.config == fledgeConfig
        assert interestGroupAuctionSeller.ext.bidder == bidResponse.seatbid[0].seat.value
        assert interestGroupAuctionSeller.ext.adapter == bidResponse.seatbid[0].seat.value

        where:
        accountPaaFormat | requestPaaFormant
        IAB              | IAB
        null             | IAB
        IAB              | null
    }

    def "PBS should populate iab output config when bid response with igi and paa format iab"() {
        given: "Default basic BidRequest with ae and openx bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.ae = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            ext.prebid.paaFormat = requestPaaFormant
        }

        and: "Default bid response with igs config"
        def impId = bidRequest.imp[0].id
        def igsConfig = [(PBSUtils.randomString): PBSUtils.randomString]
        def igbConfig = [(PBSUtils.randomString): PBSUtils.randomString]
        def igbOrigin = PBSUtils.randomString
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest).tap {
            ext = new OpenxBidResponseExt().tap {
                interestGroupAuctionIntent = [new InterestGroupAuctionIntent(
                        impId: impId,
                        interestGroupAuctionSeller: [new InterestGroupAuctionSeller(impId: impId, config: igsConfig)],
                        interestGroupAuctionBuyer: [new InterestGroupAuctionBuyer(origin: PBSUtils.randomString, pbs: igbConfig)])]
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(paaformat: accountPaaFormat))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain igs config"
        def interestGroupAuctionSeller = response.ext.interestGroupAuctionIntent[0].interestGroupAuctionSeller[0]
        assert interestGroupAuctionSeller.impId == impId
        assert interestGroupAuctionSeller.config == igsConfig
        assert interestGroupAuctionSeller.ext.bidder == bidResponse.seatbid[0].seat.value
        assert interestGroupAuctionSeller.ext.adapter == bidResponse.seatbid[0].seat.value

        and: "PBS response should contain igb config"
        def interestGroupAuctionBuyer = response.ext.interestGroupAuctionIntent[0].interestGroupAuctionBuyer[0]
        assert interestGroupAuctionBuyer.ext.bidder == bidResponse.seatbid[0].seat.value
        assert interestGroupAuctionBuyer.ext.adapter == bidResponse.seatbid[0].seat.value
        assert interestGroupAuctionBuyer.origin == igbOrigin
        assert interestGroupAuctionBuyer.pbs == igbConfig

        and: "PBS shouldn't contain warning"
        assert !bidResponse.ext.warnings[PREBID]

        and: "PBS shouldn't emit log"
        assert !pbsService.getLogsByValue("DROP igb obj")

        and: "PBS shouldn't emit metric"
        def metricsRequest = pbsService.sendCollectedMetricsRequest()
        assert !metricsRequest["Some name of metric for dropped igb object"]

        where:
        accountPaaFormat | requestPaaFormant
        IAB              | IAB
        null             | IAB
        IAB              | null
    }

    def "PBS should populate igb when bidder supplies interest group auction intent and paa formant iab"() {
        given: "Default basic bidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.ae = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            ext.prebid.paaFormat = IAB
        }

        and: "Default bid response with igs config"
        def impId = bidRequest.imp[0].id
        def igbConfig = [(PBSUtils.randomString): PBSUtils.randomString]
        def igbOrigin = PBSUtils.randomString
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest).tap {
            ext = new OpenxBidResponseExt().tap {
                interestGroupAuctionIntent = [new InterestGroupAuctionIntent(
                        impId: impId,
                        interestGroupAuctionSeller: [new InterestGroupAuctionSeller(impId: igsImpId, config: igsConfig)],
                        interestGroupAuctionBuyer: [new InterestGroupAuctionBuyer(origin: PBSUtils.randomString, pbs: igbConfig)])]
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain igs config"
        def interestGroupAuctionSeller = response.ext.interestGroupAuctionIntent[0].interestGroupAuctionSeller[0]
        assert interestGroupAuctionSeller.impId == igsImpId
        assert interestGroupAuctionSeller.config == igsConfig
        assert interestGroupAuctionSeller.ext.bidder == bidResponse.seatbid[0].seat.value
        assert interestGroupAuctionSeller.ext.adapter == bidResponse.seatbid[0].seat.value

        and: "PBS response should contain igb config"
        def interestGroupAuctionBuyer = response.ext.interestGroupAuctionIntent[0].interestGroupAuctionBuyer[0]
        assert interestGroupAuctionBuyer.ext.bidder == bidResponse.seatbid[0].seat.value
        assert interestGroupAuctionBuyer.ext.adapter == bidResponse.seatbid[0].seat.value
        assert interestGroupAuctionBuyer.origin == igbOrigin
        assert interestGroupAuctionBuyer.pbs == igbConfig

        and: "PBS shouldn't contain warning"
        assert !bidResponse.ext.warnings[PREBID]

        and: "PBS shouldn't emit log"
        assert !pbsService.getLogsByValue("DROP igb obj")

        and: "PBS shouldn't emit metric"
        def metricsRequest = pbsService.sendCollectedMetricsRequest()
        assert !metricsRequest["Some name of metric for dropped igb object"]

        where:
        igsImpId             | igsConfig
        bidRequest.imp[0].id | [(PBSUtils.randomString): PBSUtils.randomString]
        null                 | [(PBSUtils.randomString): PBSUtils.randomString]
        bidRequest.imp[0].id | null
    }

    def "PBS should drop igb object and emit warning and log metric when bidder supplies iab formant without igi.impId"() {
        given: "Default basic BidRequest with ae and openx bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.ae = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            ext.prebid.paaFormat = requestPaaFormant
        }

        and: "Default bid response with interest group auction intent"
        def impId = bidRequest.imp[0].id
        def config = [(PBSUtils.randomString): PBSUtils.randomString]
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest).tap {
            ext = new OpenxBidResponseExt().tap {
                interestGroupAuctionIntent = [new InterestGroupAuctionIntent(
                        impId: null,
                        interestGroupAuctionSeller: [new InterestGroupAuctionSeller(impId: impId, config: config)],
                        interestGroupAuctionBuyer: [new InterestGroupAuctionBuyer(origin: PBSUtils.randomString,
                                pbs: [(PBSUtils.randomString): PBSUtils.randomString])]
                )]
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(paaformat: accountPaaFormat))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain igs config"
        def interestGroupAuctionSeller = response.ext.interestGroupAuctionIntent[0].interestGroupAuctionSeller[0]
        assert interestGroupAuctionSeller.impId == impId
        assert interestGroupAuctionSeller.config == config
        assert interestGroupAuctionSeller.ext.bidder == bidResponse.seatbid[0].seat.value
        assert interestGroupAuctionSeller.ext.adapter == bidResponse.seatbid[0].seat.value

        and: "PBS response shouldn't contain igb config"
        assert !response.ext.interestGroupAuctionIntent[0].interestGroupAuctionBuyer[0]

        and: "PBS should contain warning"
        def warningEntries = bidResponse.ext.warnings[PREBID]
        assert warningEntries[0].code == 11
        assert warningEntries[0].message == "Some message"

        and: "PBS should emit log"
        def logsByValue = pbsService.getLogsByValue("DROP igb obj")
        assert logsByValue == "Some sentence"

        and: "PBS should emit metric"
        def metricsRequest = pbsService.sendCollectedMetricsRequest()
        assert metricsRequest["Some name of metric for dropped igb object"] == 1

        where:
        accountPaaFormat | requestPaaFormant
        IAB              | IAB
        null             | IAB
        IAB              | null
    }

    def "PBS should drop igs and emit warning and log metric when bidder supplies iab formant without igs[].{impId,config}"() {
        given: "Default bid request with openx bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.ae = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            ext.prebid.paaFormat = requestPaaFormant
        }

        and: "Default bid response with igs config"
        def impId = bidRequest.imp[0].id
        def igbOrigin = PBSUtils.randomString
        def igbConfig = [(PBSUtils.randomString): PBSUtils.randomString]
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest).tap {
            ext = new OpenxBidResponseExt().tap {
                interestGroupAuctionIntent = [new InterestGroupAuctionIntent(
                        impId: impId,
                        interestGroupAuctionSeller: [new InterestGroupAuctionSeller(impId: null, config: null)],
                        interestGroupAuctionBuyer: [new InterestGroupAuctionBuyer(origin: igbOrigin, pbs: igbConfig)]
                )]
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(paaformat: accountPaaFormat))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain igs config"
        assert !response.ext.interestGroupAuctionIntent[0].interestGroupAuctionSeller[0]

        and: "PBS response should contain igb config"
        def interestGroupAuctionBuyer = response.ext.interestGroupAuctionIntent[0].interestGroupAuctionBuyer[0]
        assert interestGroupAuctionBuyer.ext.bidder == bidResponse.seatbid[0].seat.value
        assert interestGroupAuctionBuyer.ext.adapter == bidResponse.seatbid[0].seat.value
        assert interestGroupAuctionBuyer.origin == igbOrigin
        assert interestGroupAuctionBuyer.pbs == igbConfig

        and: "PBS should contain warning"
        def warningEntries = bidResponse.ext.warnings[PREBID]
        assert warningEntries[0].code == 11
        assert warningEntries[0].message == "Some message"

        and: "PBS should emit log"
        def logsByValue = pbsService.getLogsByValue("DROP igb obj")
        assert logsByValue == "Some sentence"

        and: "Alert.general metric should be updated"
        def metricsRequest = pbsService.sendCollectedMetricsRequest()
        assert metricsRequest["alerts.general" as String] == 1

        where:
        accountPaaFormat | requestPaaFormant
        IAB              | IAB
        null             | IAB
        IAB              | null
    }

    def "PBS should populate fledge when bidder supplies auction configs in response and paa formant original"() {
        given: "Default bid request with opex bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.ae = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            ext.prebid.paaFormat = requestPaaFormant
        }

        and: "Default bid response with fledge config"
        def impId = bidRequest.imp[0].id
        def fledgeConfig = [(PBSUtils.randomString): PBSUtils.randomString]
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest).tap {
            ext = new OpenxBidResponseExt().tap {
                fledgeAuctionConfigs = [(impId): fledgeConfig]
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(paaformat: accountPaaFormat))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain fledge config"
        def auctionConfigs = response.ext?.prebid?.fledge?.auctionConfigs
        assert auctionConfigs?.size() == 1
        assert auctionConfigs[0].impId == impId
        assert auctionConfigs[0].bidder == bidResponse.seatbid[0].seat.value
        assert auctionConfigs[0].adapter == bidResponse.seatbid[0].seat.value
        assert auctionConfigs[0].config == fledgeConfig

        and: "PBS response shouldn't contain igs config"
        assert !response.ext.interestGroupAuctionIntent[0].interestGroupAuctionSeller[0]

        where:
        accountPaaFormat | requestPaaFormant
        ORIGINAL         | ORIGINAL
        null             | ORIGINAL
        ORIGINAL         | null
    }

    def "PBS shouldn't populate fledge config when bidder supplies interest group auction intent without impId and config and paa format original"() {
        given: "Default bid request with openx"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.ae = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            ext.prebid.paaFormat = requestPaaFormant
        }

        and: "Default bid response with igs and empty igs"
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest).tap {
            ext = new OpenxBidResponseExt().tap {
                interestGroupAuctionIntent = [new InterestGroupAuctionIntent(
                        impId: bidRequest.imp[0].id,
                        interestGroupAuctionSeller: [new InterestGroupAuctionSeller(impId: null, config: null)],
                        interestGroupAuctionBuyer: [new InterestGroupAuctionBuyer(origin: PBSUtils.randomString, pbs: [(PBSUtils.randomString): PBSUtils.randomString])]
                )]
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(paaformat: accountPaaFormat))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(pbsService)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain fledge config"
        assert !response.ext?.prebid?.fledge?.auctionConfigs

        and: "PBS response shouldn't contain igs"
        assert !response.ext.interestGroupAuctionIntent[0].interestGroupAuctionSeller[0]

        and: "PBS response shouldn't contain igb"
        assert !response.ext.interestGroupAuctionIntent[0].interestGroupAuctionBuyer[0]

        and: "PBS should contain warning"
        def warningEntries = bidResponse.ext.warnings[PREBID]
        assert warningEntries[0].code == 11
        assert warningEntries[0].message == "Some error warning"

        and: "PBS should emit log"
        def logsByValue = pbsService.getLogsByValue("DROP igb obj")
        assert logsByValue == "Some warning message"

        and: "Alert.general metric should be updated"
        def metricsRequest = pbsService.sendCollectedMetricsRequest()
        assert metricsRequest["alerts.general"] == 1

        where:
        accountPaaFormat | requestPaaFormant
        ORIGINAL         | ORIGINAL
        null             | ORIGINAL
        ORIGINAL         | null
    }

    def "PBS should populate fledge auction config when bidder supplies interest group auction intent and paa format original"() {
        given: "Default bid request with openx"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.ae = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            ext.prebid.paaFormat = requestPaaFormant
        }

        and: "Default bid response with interest group auction intent"
        def impId = bidRequest.imp[0].id
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest).tap {
            ext = new OpenxBidResponseExt().tap {
                interestGroupAuctionIntent = [new InterestGroupAuctionIntent(
                        impId: impId,
                        interestGroupAuctionSeller: [new InterestGroupAuctionSeller(impId: igsImpId, config: igsConfig)],
                        interestGroupAuctionBuyer: [new InterestGroupAuctionBuyer(origin: PBSUtils.randomString, pbs: [(PBSUtils.randomString): PBSUtils.randomString])]
                )]
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Save account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(paaformat: accountPaaFormat))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(pbsService)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain igs config"
        def interestGroupAuctionSeller = response.ext.interestGroupAuctionIntent[0].interestGroupAuctionSeller[0]
        assert interestGroupAuctionSeller.impId == igsImpId
        assert interestGroupAuctionSeller.config == igsConfig
        assert interestGroupAuctionSeller.ext.bidder == bidResponse.seatbid[0].seat.value
        assert interestGroupAuctionSeller.ext.adapter == bidResponse.seatbid[0].seat.value

        and: "PBS response should contain fledge config"
        def auctionConfigs = response.ext?.prebid?.fledge?.auctionConfigs
        assert auctionConfigs?.size() == 1
        assert auctionConfigs[0].impId == igsImpId
        assert auctionConfigs[0].bidder == bidResponse.seatbid[0].seat.value
        assert auctionConfigs[0].adapter == bidResponse.seatbid[0].seat.value
        assert auctionConfigs[0].config == igsConfig

        and: "PBS response shouldn't contain igb config"
        assert !response.ext.interestGroupAuctionIntent[0].interestGroupAuctionBuyer[0]

        and: "Alerts.general metrics shouldn't be populated"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert !metrics["alerts.general"]

        and: "Bid response shouldn't contain warnings"
        assert !bidResponse.ext.warnings

        and: "PBS shouldn't emit log"
        assert !pbsService.getLogsByValue("DROP igs obj")

        where:
        accountPaaFormat | requestPaaFormant | igsImpId             | igsConfig
        ORIGINAL         | ORIGINAL          | bidRequest.imp[0].id | [(PBSUtils.randomString): PBSUtils.randomString]
        null             | ORIGINAL          | null                 | [(PBSUtils.randomString): PBSUtils.randomString]
        ORIGINAL         | null              | bidRequest.imp[0].id | null
    }
}
