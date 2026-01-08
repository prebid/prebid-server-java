package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.config.AlternateBidderCodes
import org.prebid.server.functional.model.request.Channel
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.AdServerTargeting
import org.prebid.server.functional.model.request.auction.AdjustmentRule
import org.prebid.server.functional.model.request.auction.Amp
import org.prebid.server.functional.model.request.auction.AppExt
import org.prebid.server.functional.model.request.auction.AppExtData
import org.prebid.server.functional.model.request.auction.AppPrebid
import org.prebid.server.functional.model.request.auction.BidAdjustment
import org.prebid.server.functional.model.request.auction.BidAdjustmentFactors
import org.prebid.server.functional.model.request.auction.BidAdjustmentRule
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.BidderConfig
import org.prebid.server.functional.model.request.auction.BidderConfigOrtb
import org.prebid.server.functional.model.request.auction.ConsentedProvidersSettings
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.DeviceExt
import org.prebid.server.functional.model.request.auction.Dooh
import org.prebid.server.functional.model.request.auction.DoohExt
import org.prebid.server.functional.model.request.auction.EidPermission
import org.prebid.server.functional.model.request.auction.ExtPrebidBidderConfig
import org.prebid.server.functional.model.request.auction.ExtRequestPrebidData
import org.prebid.server.functional.model.request.auction.Interstitial
import org.prebid.server.functional.model.request.auction.MultiBid
import org.prebid.server.functional.model.request.auction.PaaFormat
import org.prebid.server.functional.model.request.auction.PrebidCache
import org.prebid.server.functional.model.request.auction.DevicePrebid
import org.prebid.server.functional.model.request.auction.PrebidCurrency
import org.prebid.server.functional.model.request.auction.Renderer
import org.prebid.server.functional.model.request.auction.RendererData
import org.prebid.server.functional.model.request.auction.Sdk
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.request.auction.SiteExt
import org.prebid.server.functional.model.request.auction.SiteExtData
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.auction.UserExtPrebid
import org.prebid.server.functional.model.request.auction.UserTime
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.ChannelType.WEB
import static org.prebid.server.functional.model.Currency.USD
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
import static org.prebid.server.functional.model.mock.services.currencyconversion.CurrencyConversionRatesResponse.defaultConversionRates
import static org.prebid.server.functional.model.request.auction.AdjustmentType.MULTIPLIER
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.BANNER
import static org.prebid.server.functional.model.request.auction.DebugCondition.ENABLED
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.DOOH
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.request.auction.TraceLevel.BASIC
import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC
import static org.prebid.server.functional.util.PBSUtils.getRandomDecimal
import static org.prebid.server.functional.util.PBSUtils.roundDecimal

class BidderFieldDisplayBehaviorSpec extends BaseSpec {

    def "PBS should pass ext.prebid.createTids to bidder request"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.createTids = PBSUtils.randomBoolean
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain createTids"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.createTids == bidRequest.ext.prebid.createTids
    }

    def "PBS shouldn't pass ext.prebid.returnAllBidStatus to bidder request"() {
        given: "Default basic bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain returnAllBidStatus"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.returnAllBidStatus
    }

    def "PBS shouldn't pass aliasGvlIds to bidder request when aliasGvlIds or aliases specified"() {
        given: "Default basic bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.generic = null
            ext.prebid.aliasgvlids = [(BidderName.ALIAS.value): PBSUtils.randomNumber]
            ext.prebid.aliases = [(BidderName.ALIAS.value): BidderName.GENERIC]
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain aliasgvlids and aliases"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.aliasgvlids
        assert !bidderRequest.ext.prebid.aliases
    }

    def "PBS shouldn't pass adServerTargeting to bidder request when adServerTargeting specified"() {
        given: "Default bid request"
        def customBidRequest = "custom_bid_request"
        def sourceOfBidRequest = "bidrequest"
        def impId = "imp.id"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.tap {
                adServerTargeting = [
                        new AdServerTargeting().tap {
                            key = customBidRequest
                            source = sourceOfBidRequest
                            value = impId
                        }]
            }
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain adServerTargeting"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.adServerTargeting
    }

    def "PBS should pass supportDeals to bidder request when supportDeals specified"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.supportDeals = PBSUtils.randomBoolean
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain requested supportDeals"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.supportDeals == bidRequest.ext.prebid.supportDeals
    }

    def "PBS shouldn't pass ext.prebid.cache to bidder request when cache specified"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.cache = new PrebidCache(winningOnly: PBSUtils.randomBoolean)
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain cache"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.cache
    }

    def "PBS should pass ext.prebid.channel to bidder request when channel specified"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.channel = new Channel(name: WEB)
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain ext.prebid.channel"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.channel == bidRequest.ext.prebid.channel
    }

    def "PBS should pass ext.prebid.currency.{usePbsRates/rates} to bidder request when usePbsRates or rates specified"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.currency = new PrebidCurrency(
                    usePbsRates: PBSUtils.randomBoolean,
                    rates: getDefaultConversionRates())
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain ext.prebid.currency"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.currency == bidRequest.ext.prebid.currency
    }

    def "PBS shouldn't pass ext.prebid.data.{bidders,eidpermissions} to bidder request"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.data = new ExtRequestPrebidData(bidders: [GENERIC.value],
                    eidpermissions: [new EidPermission(source: PBSUtils.randomString, bidders: [BidderName.GENERIC])])
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain ext.prebid.data.{bidders,eidpermissions}"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest?.ext?.prebid?.data?.bidders
        assert !bidderRequest?.ext?.prebid?.data?.eidpermissions
    }

    def "PBS should pass ext.prebid.{trace,debug,integration} to bidder request"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = BASIC
            ext.prebid.debug = ENABLED
            ext.prebid.integration = PBSUtils.randomString
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain ext.prebid.data.{debug,trace,integration}"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            bidderRequest.ext.prebid.trace == bidRequest.ext.prebid.trace
            bidderRequest.ext.prebid.debug == bidRequest.ext.prebid.debug
            bidderRequest.ext.prebid.integration == bidRequest.ext.prebid.integration
        }
    }

    def "PBS shouldn't pass ext.prebid.events to bidder request when events specified"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            enableEvents()
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain events"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.events
    }

    def "PBS shouldn't pass ext.prebid.noSale to bidder request when noSale specified"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.noSale = [PBSUtils.randomString]
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain noSale"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.noSale
    }

    def "PBS should pass ext.prebid.amp to bidder request when amp specified"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.amp = new Amp(data: AmpRequest.getDefaultAmpRequest())
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain requested amp"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.amp == bidRequest.ext.prebid.amp
    }

    def "PBS should copy imp level passThrough to bidresponse.seatbid[].bid[].ext.prebid.passThrough when the passThrough is present"() {
        given: "Default bid request with passThrough"
        def randomString = PBSUtils.randomString
        def passThrough = [(randomString): randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.passThrough = passThrough
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain the same passThrough as on request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.passThrough

        and: "Response should contain the same passThrough as on request"
        assert response.seatbid.first().bid.first().ext.prebid.passThrough == passThrough

        and: "Response shouldn't contain in ext.prebid.passThrough"
        assert !response.ext.prebid.passThrough
    }

    def "PBS should copy global level passThrough object to bidresponse.ext.prebid.passThrough when passThrough is present"() {
        given: "Default bid request with passThrough"
        def randomString = PBSUtils.randomString
        def passThrough = [(randomString): randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.passThrough = passThrough
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain the same passThrough as on request"
        assert !response.seatbid.first().bid.first().ext.prebid.passThrough

        and: "Response should contain in ext.prebid.passThrough"
        assert response.ext.prebid.passThrough == passThrough

        and: "Bidder request shouldn't contain the same passThrough as on request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.passThrough
    }

    def "PBS auction should pass ext.prebid.sdk requested to bidder request when sdk specified"() {
        given: "Default bid request with ext.prebid.sdk"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.sdk = new Sdk(renderers: [new Renderer(
                    name: PBSUtils.randomString,
                    version: PBSUtils.randomString,
                    data: new RendererData(any: PBSUtils.randomString))])
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain sdk value same in request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll {
            bidderRequest.ext.prebid.sdk.renderers.name == bidRequest.ext.prebid.sdk.renderers.name
            bidderRequest.ext.prebid.sdk.renderers.version == bidRequest.ext.prebid.sdk.renderers.version
            bidderRequest.ext.prebid.sdk.renderers.data.any == bidRequest.ext.prebid.sdk.renderers.data.any
        }
    }

    def "PBS auction should pass ext.prebid.paaformat to bidder request when paaformat specified"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.paaFormat = PaaFormat.ORIGINAL
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain paaFormat value same in request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.paaFormat == bidRequest.ext.prebid.paaFormat
    }

    def "PBS auction shouldn't pass ext.prebid.kvps to bidder request when kvps specified"() {
        given: "Default bid request with kvps"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.keyValuePairs = [(PBSUtils.randomString): PBSUtils.randomString]
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain keyValuePairs"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.keyValuePairs
    }

    def "PBS auction should pass ext.prebid.alternatebiddercodes to bidder request when alternate bidder codes specified"() {
        given: "Default bid request with alternateBidderCodes"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.alternateBidderCodes = new AlternateBidderCodes().tap {
                it.enabled = true
                it.bidders = [(BidderName.GENERIC): new org.prebid.server.functional.model.config.BidderConfig(enabled: true, allowedBidderCodes: [BidderName.GENERIC]),
                              (RUBICON)           : new org.prebid.server.functional.model.config.BidderConfig(enabled: true, allowedBidderCodes: [RUBICON])]
            }
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain ext.prebid.alternateBidderCodes"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.alternateBidderCodes.enabled == bidRequest.ext.prebid.alternateBidderCodes.enabled
        assert bidderRequest.ext.prebid.alternateBidderCodes.bidders == [(BidderName.GENERIC): new org.prebid.server.functional.model.config.BidderConfig(enabled: true)]
    }

    def "PBS should pass user.ext to bidder request when user.ext specified"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(ext: new UserExt().tap {
                fcapids = [PBSUtils.randomString]
                time = new UserTime(userdow: PBSUtils.randomNumber, userhour: PBSUtils.randomNumber)
                prebid = new UserExtPrebid(buyeruids: [(BidderName.GENERIC): PBSUtils.randomString])
                consentedProvidersSettings = new ConsentedProvidersSettings(consentedProviders: PBSUtils.randomString)
            })
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain user.ext.prebid.buyeruids"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest?.user?.ext?.prebid?.buyeruids

        and: "Bidder request should contain user.ext.{fcapid,time,consentedProvidersSettings}"
        verifyAll(bidderRequest) {
            bidderRequest.user.ext.fcapids == bidRequest.user.ext.fcapids
            bidderRequest.user.ext.time == bidRequest.user.ext.time
            bidderRequest.user.ext.consentedProvidersSettings == bidRequest.user.ext.consentedProvidersSettings
        }
    }

    def "PBS should pass site.ext to bidder request when site.ext specified"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.ext = new SiteExt(amp: 0, data: new SiteExtData(id: PBSUtils.randomString))
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain site.ext.{amp,data}"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.site.ext.data == bidRequest.site.ext.data
        assert bidderRequest.site.ext.amp == bidRequest.site.ext.amp
    }

    def "PBS should pass device.ext to bidder request when device.ext specified"() {
        given: "Default basic bid request with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(
                    ext: new DeviceExt(
                            atts: DeviceExt.Atts.UNKNOWN,
                            cdep: PBSUtils.randomString,
                            prebid: new DevicePrebid(interstitial: new Interstitial(
                                    minHeightPercentage: PBSUtils.getRandomNumber(0, 100),
                                    minWidthPercentage: PBSUtils.getRandomNumber(0, 100)))))
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain requested device.ext"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.ext == bidRequest.device.ext
    }

    def "PBS should pass ext.prebid.auctionTimestamp to bidder request when auctionTimestamp specified"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.auctionTimestamp = PBSUtils.randomNumber
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain auctionTimestamp"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.auctionTimestamp
    }

    def "PBS shouldn't pass ext.prebid.bidderConfig to bidder request when bidderConfig specified"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidderConfig = [new ExtPrebidBidderConfig(bidders: [BidderName.GENERIC], config:
                    new BidderConfig(ortb2: new BidderConfigOrtb(site: Site.configFPDSite, user: User.configFPDUser)))]
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain bidderConfig"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.bidderConfig
    }

    def "PBS shouldn't pass bidder param to the bidder when bidder param bidder not requested"() {
        given: "Default bid request with populated ext.prebid.bidderParams"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidderParams = [(RUBICON.value): PBSUtils.randomString]
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain error"
        assert !response.ext?.errors

        and: "Generic bidder request shouldn't contain bidder param"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.bidderParams
    }

    def "PBS should pass bidder app.ext to the bidder request when app ext specified"() {
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.ext = new AppExt(data: new AppExtData(language: PBSUtils.randomString),
                    prebid: new AppPrebid(source: PBSUtils.randomString, version: PBSUtils.randomString))
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain app.ext"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.app.ext == bidRequest.app.ext
    }

    def "PBS should pass dooh.ext to bidder request when dooh.ext specified"() {
        given: "Default bid request with bidRequest.dooh"
        def bidRequest = BidRequest.getDefaultBidRequest(DOOH).tap {
            dooh = new Dooh(id: PBSUtils.randomString, ext: DoohExt.defaultDoohExt)
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain dooh.ext"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.dooh.ext == bidRequest.dooh.ext
    }

    def "PBS should pass ext.prebid.multiBid only bidder related entity for each bidder"() {
        given: "Default basic BidRequest with generic bidder with includeBidderKeys = false"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Set maxbids = 2 for generic and rubicon bidder"
        def maxBids = 2
        def genericMultiBid = new MultiBid(bidder: BidderName.GENERIC, maxBids: maxBids, targetBidderCodePrefix: PBSUtils.randomString)
        def rubiconMultiBid = new MultiBid(bidder: RUBICON, maxBids: maxBids, targetBidderCodePrefix: PBSUtils.randomString)
        bidRequest.ext.prebid.multibid = [genericMultiBid, rubiconMultiBid]

        and: "Default basic bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid request should contain requested ext.prebid.multiBid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.multibid == [genericMultiBid]
    }

    def "PBS should pass ext.prebid.bidAdjustment only bidder related entry for each bidder"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def rule = new BidAdjustmentRule().tap {
            generic = ["*": [new AdjustmentRule(adjustmentType: MULTIPLIER, value: PBSUtils.randomPrice, currency: currency)]]
            openx = ["*": [new AdjustmentRule(adjustmentType: MULTIPLIER, value: PBSUtils.randomPrice, currency: currency)]]
        }
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, rule)
            cur = [currency]
            imp.first.bidFloor = PBSUtils.randomPrice
            imp.first.bidFloorCur = currency
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain generic bid adjustments"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.bidAdjustments.version == bidRequest.ext.prebid.bidAdjustments.version
        assert bidderRequest.ext.prebid.bidAdjustments.mediaType[BANNER].generic == bidRequest.ext.prebid.bidAdjustments.mediaType[BANNER].generic

        and: "Bidder request shouldn't contain openx bid adjustments"
        assert !bidderRequest.ext.prebid.bidAdjustments.mediaType[BANNER].openx
    }

    def "PBS should pass ext.prebid.bidAdjustmentFactors only bidder related entry for each bidder"() {
        given: "Default bid request with bid adjustment"
        def bidAdjustment = roundDecimal(getRandomDecimal(), 0)
        def mediaTypeBidAdjustment = bidAdjustmentFactor
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors().tap {
                adjustments = [(BidderName.GENERIC): bidAdjustment, (OPENX): bidAdjustment]
                mediaTypes = [(BANNER): [(BidderName.GENERIC): mediaTypeBidAdjustment],
                              (BANNER): [(OPENX): mediaTypeBidAdjustment]]
            }
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain generic bid adjustment factors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.bidAdjustmentFactors.adjustments[BidderName.GENERIC] == bidRequest.ext.prebid.bidAdjustmentFactors.adjustments[BidderName.GENERIC]
        assert bidderRequest.ext.prebid.bidAdjustmentFactors.mediaTypes[BANNER][GENERIC] == bidRequest.ext.prebid.bidAdjustmentFactors.mediaTypes[BANNER][GENERIC]

        and: "Bidder request shouldn't contain opneX bid adjustment factors for generic call"
        assert bidderRequest.ext.prebid.bidAdjustmentFactors.adjustments[OPENX]
        assert bidderRequest.ext.prebid.bidAdjustmentFactors.mediaTypes[BANNER][OPENX]

        where:
        bidAdjustmentFactor << [0.9, 1.1]
    }
}
