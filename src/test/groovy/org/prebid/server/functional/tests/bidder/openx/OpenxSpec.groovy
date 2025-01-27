package org.prebid.server.functional.tests.bidder.openx

import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.PaaFormat
import org.prebid.server.functional.model.response.auction.InterestGroupAuctionBuyer
import org.prebid.server.functional.model.response.auction.InterestGroupAuctionBuyerExt
import org.prebid.server.functional.model.response.auction.InterestGroupAuctionIntent
import org.prebid.server.functional.model.response.auction.InterestGroupAuctionSeller
import org.prebid.server.functional.model.response.auction.OpenxBidResponse
import org.prebid.server.functional.model.response.auction.OpenxBidResponseExt
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Shared

import java.time.Instant

import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.OPENX_ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.WILDCARD
import static org.prebid.server.functional.model.request.auction.AuctionEnvironment.DEVICE_ORCHESTRATED
import static org.prebid.server.functional.model.request.auction.AuctionEnvironment.NOT_SUPPORTED
import static org.prebid.server.functional.model.request.auction.PaaFormat.IAB
import static org.prebid.server.functional.model.request.auction.PaaFormat.ORIGINAL
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer

class OpenxSpec extends BaseSpec {

    private static final Map OPENX_CONFIG = ["adapters.openx.enabled" : "true",
                                             "adapters.openx.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
    private static final Map OPENX_ALIAS_CONFIG = ["adapters.openx.aliases.openxalias.enabled" : "true",
                                                   "adapters.openx.aliases.openxalias.endpoint": "$networkServiceContainer.rootUri/auction".toString()]

    @Shared
    PrebidServerService pbsService = pbsServiceFactory.getService(OPENX_CONFIG)

    def "PBS should populate fledge config by default when bid response with fledge"() {
        given: "Default basic BidRequest with ae and openx bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.auctionEnvironment = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
        }

        and: "Default bid response with fledge config"
        def impId = bidRequest.imp[0].id
        def fledgeConfig = [(PBSUtils.randomString): PBSUtils.randomString]
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest, OPENX).tap {
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

        and: "PBS response shouldn't contain igb config"
        assert !response.ext?.interestGroupAuctionIntent?.interestGroupAuctionBuyer

        and: "PBS response shouldn't contain igs config"
        assert !response.ext?.interestGroupAuctionIntent?.interestGroupAuctionSeller
    }

    def "PBS should populate fledge config when bid response with fledge and ext.prebid.paaFormat = ORIGINAL"() {
        given: "Default basic BidRequest with ae and openx bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.auctionEnvironment = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            ext.prebid.paaFormat = ORIGINAL
        }

        and: "Default bid response with fledge config"
        def impId = bidRequest.imp[0].id
        def fledgeConfig = [(PBSUtils.randomString): PBSUtils.randomString]
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest, OPENX).tap {
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

        and: "PBS response shouldn't contain igb config"
        assert !response.ext?.interestGroupAuctionIntent?.interestGroupAuctionBuyer

        and: "PBS response shouldn't contain igs config"
        assert !response.ext?.interestGroupAuctionIntent?.interestGroupAuctionSeller
    }

    def "PBS shouldn't populate fledge config when bid response with fledge and ext.prebid.paaFormat = IAB"() {
        given: "Default basic BidRequest without ae"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.auctionEnvironment = NOT_SUPPORTED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            ext.prebid.paaFormat = IAB
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

        and: "PBS response shouldn't contain igb config"
        assert !response.ext?.interestGroupAuctionIntent?[0]?.interestGroupAuctionBuyer

        and: "PBS response should contain igs config"
        def interestGroupAuctionSeller = response.ext.interestGroupAuctionIntent[0].interestGroupAuctionSeller[0]
        assert interestGroupAuctionSeller.impId == impId
        assert interestGroupAuctionSeller.config
        assert interestGroupAuctionSeller.ext.bidder == bidResponse.seatbid[0].seat.value
        assert interestGroupAuctionSeller.ext.adapter == OPENX.value
    }

    def "PBS shouldn't populate fledge config when bid response didn't return fledge config"() {
        given: "Default basic BidRequest without ae"
        def bidRequest = BidRequest.defaultBidRequest.tap {
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

        and: "PBS response shouldn't contain igi config"
        assert !response?.ext?.interestGroupAuctionIntent
    }

    def "PBS should populate fledge and iab output config when bid response with fledge and paa formant IAB"() {
        given: "Default bid request with openx"
        def bidRequest = BidRequest.defaultBidRequest.tap {
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

        then: "PBS response shouldn't contain fledge config"
        assert !response.ext?.prebid?.fledge?.auctionConfigs

        and: "PBS response should contain igs config"
        def interestGroupAuctionSeller = response.ext.interestGroupAuctionIntent[0].interestGroupAuctionSeller[0]
        assert interestGroupAuctionSeller.impId == impId
        assert interestGroupAuctionSeller.config == fledgeConfig
        assert interestGroupAuctionSeller.ext.bidder == bidResponse.seatbid[0].seat.value
        assert interestGroupAuctionSeller.ext.adapter == bidResponse.seatbid[0].seat.value

        and: "PBS response shouldn't contain igb config"
        assert !response.ext?.interestGroupAuctionIntent?[0]?.interestGroupAuctionBuyer

        where:
        accountPaaFormat | requestPaaFormant
        IAB              | IAB
        null             | IAB
        IAB              | null
    }

    def "PBS should populate fledge config by default when bid response with fledge and requested aliases"() {
        given: "PBS config with alias config"
        def pbsService = pbsServiceFactory.getService(OPENX_CONFIG + OPENX_ALIAS_CONFIG)

        and: "Default basic BidRequest with ae and bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.auctionEnvironment = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.openxAlias = Openx.defaultOpenx
            ext.prebid.aliases = [(OPENX_ALIAS.value): OPENX]
        }

        and: "Default bid response with fledge config"
        def impId = bidRequest.imp[0].id
        def fledgeConfig = [(PBSUtils.randomString): PBSUtils.randomString]
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest, OPENX_ALIAS).tap {
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
        assert auctionConfigs[0].bidder == OPENX_ALIAS.value
        assert auctionConfigs[0].adapter == OPENX.value
        assert auctionConfigs[0].config == fledgeConfig

        and: "PBS response shouldn't contain igi config"
        assert !response.ext?.interestGroupAuctionIntent
    }

    def "PBS should populate iab config when bid response with fledge and requested aliases"() {
        given: "PBS config"
        def pbsService = pbsServiceFactory.getService(OPENX_CONFIG + OPENX_ALIAS_CONFIG)

        and: "Default basic BidRequest with ae and openx bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.auctionEnvironment = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.openxAlias = Openx.defaultOpenx
            ext.prebid.aliases = [(OPENX_ALIAS.value): OPENX]
            ext.prebid.paaFormat = IAB
        }

        and: "Default bid response with fledge config"
        def impId = bidRequest.imp[0].id
        def fledgeConfig = [(PBSUtils.randomString): PBSUtils.randomString]
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest, OPENX_ALIAS).tap {
            ext = new OpenxBidResponseExt().tap {
                fledgeAuctionConfigs = [(impId): fledgeConfig]
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain fledge config"
        assert !response.ext?.prebid?.fledge?.auctionConfigs

        and: "PBS response should contain igs config"
        def interestGroupAuctionSeller = response.ext.interestGroupAuctionIntent[0].interestGroupAuctionSeller[0]
        assert interestGroupAuctionSeller.impId == impId
        assert interestGroupAuctionSeller.config == fledgeConfig
        assert interestGroupAuctionSeller.ext.bidder == OPENX_ALIAS.value
        assert interestGroupAuctionSeller.ext.adapter == OPENX.value

        and: "PBS response shouldn't contain igi config"
        assert !response.ext?.interestGroupAuctionIntent?[0].interestGroupAuctionBuyer
    }

    def "PBS should populate fledge config by default when bid response with fledge and imp mismatched"() {
        given: "Default basic BidRequest with ae and openx bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.auctionEnvironment = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
        }

        and: "Default bid response with fledge config"
        def fledgeConfig = [(PBSUtils.randomString): PBSUtils.randomString]
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest, OPENX).tap {
            ext = new OpenxBidResponseExt().tap {
                fledgeAuctionConfigs = [(fledgeImpId): fledgeConfig]
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain fledge config"
        def auctionConfigs = response.ext?.prebid?.fledge?.auctionConfigs
        assert auctionConfigs?.size() == 1
        assert auctionConfigs[0].impId == fledgeImpId
        assert auctionConfigs[0].bidder == bidResponse.seatbid[0].seat.value
        assert auctionConfigs[0].adapter == bidResponse.seatbid[0].seat.value
        assert auctionConfigs[0].config == fledgeConfig

        and: "PBS response shouldn't contain igi config"
        assert !response.ext?.interestGroupAuctionIntent

        where:
        fledgeImpId << [PBSUtils.randomString, PBSUtils.randomNumber as String, WILDCARD.value]
    }

    def "PBS should log error and not populated fledge impId when bidder respond with not empty config, but an empty impid"() {
        given: "Start time"
        def startTime = Instant.now()

        and: "Default basic BidRequest with ae and openx bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.auctionEnvironment = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            ext.prebid.paaFormat = IAB
        }

        and: "Default bid response with fledge config without imp"
        def fledgeConfig = [(PBSUtils.randomString): PBSUtils.randomString]
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest, OPENX).tap {
            ext = new OpenxBidResponseExt().tap {
                fledgeAuctionConfigs = [(""): fledgeConfig] as Map<String, Map>
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Flush metrics"
        flushMetrics(pbsService)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain fledge config"
        assert !response.ext?.prebid?.fledge?.auctionConfigs

        and: "PBS response shouldn't contain igi config"
        assert !response.ext?.interestGroupAuctionIntent

        and: "PBS log should contain error"
        def logs = pbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "ExtIgiIgs with absent impId from bidder: ${OPENX.value}")

        and: "Bid response should contain warning"
        assert response.ext.warnings[PREBID]?.code == [999, 999]
        assert response.ext.warnings[PREBID]?.message ==
                ["ExtIgi with absent impId from bidder: ${OPENX.value}" as String,
                 "ExtIgiIgs with absent impId from bidder: ${OPENX.value}" as String]

        and: "Alert.general metric should be updated"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics["alerts.general" as String] == 1
    }

    def "PBS shouldn't populate fledge or igi config when bidder respond with igb"() {
        given: "Default basic BidRequest with ae and openx bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.auctionEnvironment = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
        }

        and: "Default bid response with igb config"
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest, OPENX).tap {
            ext = new OpenxBidResponseExt().tap {
                interestGroupAuctionIntent = [new InterestGroupAuctionIntent(
                        interestGroupAuctionBuyer: [new InterestGroupAuctionBuyer(
                                origin: PBSUtils.randomString,
                                maxBid: PBSUtils.randomDecimal,
                                cur: PBSUtils.getRandomEnum(Currency),
                                pbs: [(PBSUtils.randomString): PBSUtils.randomString],
                                ext: new InterestGroupAuctionBuyerExt(
                                        bidder: PBSUtils.randomString,
                                        adapter: PBSUtils.randomString
                                )
                        )])
                ]
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain fledge config"
        assert !response.ext?.prebid?.fledge?.auctionConfigs

        and: "PBS response shouldn't contain igi config"
        assert !response.ext?.interestGroupAuctionIntent
    }

    def "PBS should throw error when requested unknown paa format"() {
        given: "Default basic BidRequest with ae and openx bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.auctionEnvironment = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            ext.prebid.paaFormat = PaaFormat.INVALID
        }

        and: "Default bid response with fledge config"
        def impId = bidRequest.imp[0].id
        def fledgeConfig = [(PBSUtils.randomString): PBSUtils.randomString]
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest, OPENX).tap {
            ext = new OpenxBidResponseExt().tap {
                fledgeAuctionConfigs = [(impId): fledgeConfig]
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.responseBody.startsWith("Invalid request format: Error decoding bidRequest: " +
                "Cannot deserialize value of type `org.prebid.server.auction.model.PaaFormat` " +
                "from String \"invalid\": not one of the values accepted for Enum class: [original, iab]")
    }

    def "PBS shouldn't cause error when igs and igb empty array"() {
        given: "Default basic BidRequest with ae and openx bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.auctionEnvironment = DEVICE_ORCHESTRATED
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            ext.prebid.paaFormat = paaFormat
        }

        and: "Default bid response with igs config"
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest, OPENX).tap {
            ext = new OpenxBidResponseExt().tap {
                interestGroupAuctionIntent = [new InterestGroupAuctionIntent(
                        interestGroupAuctionSeller: interestGroupAuctionSeller,
                        interestGroupAuctionBuyer: interestGroupAuctionBuyer
                )]
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain fledge config"
        assert !response.ext?.prebid?.fledge?.auctionConfigs

        and: "PBS response shouldn't contain igi config"
        assert !response.ext?.interestGroupAuctionIntent

        where:
        paaFormat | interestGroupAuctionSeller         | interestGroupAuctionBuyer
        IAB       | [new InterestGroupAuctionSeller()] | [new InterestGroupAuctionBuyer()]
        ORIGINAL  | []                                 | []
    }
}
