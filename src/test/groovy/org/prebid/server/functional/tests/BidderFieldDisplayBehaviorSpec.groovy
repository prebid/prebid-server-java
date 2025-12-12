package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.mock.services.currencyconversion.CurrencyConversionRatesResponse
import org.prebid.server.functional.model.request.Channel
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.AdServerTargeting
import org.prebid.server.functional.model.request.auction.Amp
import org.prebid.server.functional.model.request.auction.BidAdjustmentFactors
import org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.BidderConfig
import org.prebid.server.functional.model.request.auction.BidderConfigOrtb
import org.prebid.server.functional.model.request.auction.ConsentedProvidersSettings
import org.prebid.server.functional.model.request.auction.DebugCondition
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.DeviceExt
import org.prebid.server.functional.model.request.auction.EidPermission
import org.prebid.server.functional.model.request.auction.Events
import org.prebid.server.functional.model.request.auction.ExtPrebidBidderConfig
import org.prebid.server.functional.model.request.auction.ExtRequestPrebidData
import org.prebid.server.functional.model.request.auction.Interstitial
import org.prebid.server.functional.model.request.auction.PrebidAnalytics
import org.prebid.server.functional.model.request.auction.PrebidCache
import org.prebid.server.functional.model.request.auction.DevicePrebid
import org.prebid.server.functional.model.request.auction.PrebidCurrency
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.model.request.auction.Renderer
import org.prebid.server.functional.model.request.auction.RendererData
import org.prebid.server.functional.model.request.auction.Sdk
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.request.auction.SiteExt
import org.prebid.server.functional.model.request.auction.SiteExtData
import org.prebid.server.functional.model.request.auction.TraceLevel
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.auction.UserExtPrebid
import org.prebid.server.functional.model.request.auction.UserTime
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.PBSUtils
import spock.lang.IgnoreRest

import static org.prebid.server.functional.model.ChannelType.WEB
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.UNKNOWN
import static org.prebid.server.functional.model.mock.services.currencyconversion.CurrencyConversionRatesResponse.defaultConversionRates
import static org.prebid.server.functional.model.request.auction.DebugCondition.ENABLED
import static org.prebid.server.functional.model.request.auction.DeviceExt.Atts.*
import static org.prebid.server.functional.model.request.auction.PaaFormat.ORIGINAL
import static org.prebid.server.functional.model.request.auction.TraceLevel.BASIC
import static org.prebid.server.functional.model.response.auction.ErrorType.ALIAS

class BidderFieldDisplayBehaviorSpec extends BaseSpec {

    //todo:ext.prebid.returnallbidstatus (boolean)
    //  Never needed inside the adapter code
    //  Exposes nothing because it’s a boolean

    def "PBS shouldn't send returnallbidstatus to bidder request"() {
        given: "Default basic bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain returnAll"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.returnAllBidStatus
    }

    //todo: ext.prebid.aliasgvlids (map)
    //  Never needed inside the adapter code

    @IgnoreRest
    def "PBS shouldn't pass aliasgvlids and aliases to bidder request"() {
        given: "Default basic bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.generic = null
            ext.prebid.aliasgvlids = ["alias": 123]
            ext.prebid.aliases = [(ALIAS.value): BidderName.GENERIC]
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

    //todo: ext.prebid.bidadjustmentfactors and ext.prebid.bidadjustments (map)
    //  Never needed inside the adapter code (for now) but according to the requirements adapters they might be needed to reverse price floors according to the https://magnite.atlassian.net/browse/HB-20538
    //  bidadjustments are missed in the table
    //  The value is taken from request/account
    //  The rules of other bidders if any are exposed.

    def "PBS shouldn't send bid adjustment media type to bidder request"() {
        given: "Default basic bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.tap {
                bidAdjustmentFactors = new BidAdjustmentFactors().tap {
                    it.adjustments = [(BidderName.GENERIC): BigDecimal.ONE]
                    it.mediaTypes = [(BidAdjustmentMediaType.BANNER): [(BidderName.GENERIC): BigDecimal.ONE]]
                }
            }
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain bid adjustment factors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.bidAdjustmentFactors
    }

    //todo: ext.prebid.adservertargeting (object)
    //    Never needed inside the adapter code

    def "PBS shouldn't send bid adServerTargeting to bidder request"() {
        given: "Default basic bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.tap {
                adServerTargeting = [
                        new AdServerTargeting().tap {
                            key = "custom_bid_request"
                            source = "bidrequest"
                            value = "imp.id"
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

    //todo: ext.prebid.supportdeals (boolean)
    //  Never needed inside the adapter code
    //  Exposes nothing because it’s a boolean


    def "PBS shouldn't send supportDeals to bidder request"() {
        given: "Default basic bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.supportDeals = PBSUtils.randomBoolean
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain supportDeals"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.supportDeals
    }

    //todo: ext.prebid.cache (object)
    //  Never needed inside the adapter code
    //  Not sure it exposes something because it’s only about ttlseconds integers and some booleans

    @IgnoreRest
    def "PBS shouldn't pass ext.prebid.cache to bidder request"() {
        given: "Default basic bid request"
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

    //todo: ext.prebid.channel (object)
    //  Never needed inside the adapter code
    //  Not sure it exposes something because it’s only about ttlseconds integers and some booleans

    def "PBS shouldn't pass ext.prebid.channel to bidder request"() {
        given: "Default basic bid request"
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


    //todo: ext.prebid.currency.rates (object)

    @IgnoreRest
    def "PBS should pass ext.prebid.currency.rates to bidder request"() {
        given: "Default basic bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.currency = new PrebidCurrency(
                    usePbsRates: PBSUtils.getRandomBoolean(),
                    rates: getDefaultConversionRates())
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain ext.prebid.currency.rates"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.currency == bidRequest.ext.prebid.currency
    }

    //todo: ext.prebid.data.bidder (object)

    @IgnoreRest
    def "PBS shouldn't pass ext.prebid.data.{bidders,eidpermissions} to bidder request"() {
        given: "Default basic bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.data = new ExtRequestPrebidData(bidders: [GENERIC.value],
                    eidpermissions: [new EidPermission(source: PBSUtils.randomString, bidders: [GENERIC])])
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain ext.prebid.data.{bidders,eidpermissions}"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest?.ext?.prebid?.data?.bidders
        assert !bidderRequest?.ext?.prebid?.data?.eidpermissions
    }

    @IgnoreRest
    def "PBS shouldn't pass ext.prebid.data.{trace,debug} to bidder request"() {
        given: "Default basic bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = BASIC
            ext.prebid.debug = ENABLED
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain ext.prebid.data.{debug,trace}"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.trace
        assert bidderRequest.ext.prebid.debug
    }

    //todo: ext.prebid.events (object)
    //  Never needed inside the adapter code

    def "PBS shouldn't send bid events to bidder request"() {
        given: "Default basic bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.events = new Events()
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain events"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.events
    }

    //todo: ext.prebid.nosale (object)
    //  Never needed inside the adapter code

    def "PBS shouldn't send noSale to bidder request"() {
        given: "Default basic bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.noSale = [PBSUtils.randomString]
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain noSale"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.noSale
    }

    //todo: ext.prebid.multibid (array of objects)
    //      Used in RubiconBidder only


    //todo: ext.prebid.amp (object)
    //      Mistakenly used in the InvibesBidder
    //      Never used in other bidder
    //      Not mentioned in the table
    //      ADD AMP REQUEST!!!

    def "PBS shouldn't send amp to bidder request"() {
        given: "Default basic bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.amp = new Amp(data: AmpRequest.getDefaultAmpRequest())
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain noSale"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.amp
    }

    //todo: ext.prebid.analytics (object)
    //      Never needed inside the adapter code

    def "PBS shouldn't send analytics to bidder request"() {
        given: "Default basic bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.analytics = new PrebidAnalytics()
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain analytics"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.analytics
    }

    //todo: need ask
    //  ext.prebid.bidderparams and ext.prebid.bidders (object) !!! PAY ATTENTION on ext.prebid.bidderparams
    //  According to the table ext.prebid.bidderparams should not be exposed as-is, instead it should replaced with the ext.prebid.bidderparams.bidder from ext.prebid.bidders, the ext.prebid.bidders presumably shouldn’t be exposed at all - BUT in my test the ext.prebid.bidderparams and ext.prebid.bidders are kept as-is
    //  ext.prebid.bidders is used in RubiconBidder
    //  ext.prebid.bidderparams is used in MediaGoBidder and PubmaticBidder
    //  ext.prebid.floors (object)
    //
    //  Needed only in RubiconBidder
    //  The value is taken from the floors provider

    def "PBS should copy imp level passThrough to bidresponse.seatbid[].bid[].ext.prebid.passThrough when the passThrough is present"() {
        given: "Default bid request with passThrough"
        def randomString = PBSUtils.randomString
        def passThrough = [(randomString): randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.passThrough = passThrough
        }

        when: "Requesting PBS auction"
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

        when: "Requesting PBS auction"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain the same passThrough as on request"
        assert !response.seatbid.first().bid.first().ext.prebid.passThrough

        and: "Response should contain in ext.prebid.passThrough"
        assert response.ext.prebid.passThrough == passThrough

        and: "Bidder request shouldn't contain the same passThrough as on request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.passThrough
    }

    //todo : ext.prebid.sdk (object)
    //          Never used at all - maybe it’s deprecated
    //          Not mentioned in the table

    def "PBS auction should pass ext.prebid.sdk requested to bidder request when sdk specified"() {
        given: "Default bid request with aliases"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.sdk = new Sdk(renderers: [new Renderer(
                    name: PBSUtils.randomString,
                    version: PBSUtils.randomString,
                    data: new RendererData(any: PBSUtils.randomString))])
        }

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain sdk value same in request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.sdk.renderers.name == bidRequest.ext.prebid.sdk.renderers.name
        assert bidderRequest.ext.prebid.sdk.renderers.version == bidRequest.ext.prebid.sdk.renderers.version
        assert bidderRequest.ext.prebid.sdk.renderers.data.any == bidRequest.ext.prebid.sdk.renderers.data.any
    }

    def "PBS auction shouldn't pass ext.prebid.paaformat to bidder request when paaformat specified"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.paaFormat = ORIGINAL
        }

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain paaForamt value same in request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.paaFormat
    }

    def "PBS should pass srid to the bidder request when srid present"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomNumber)
        }

        and: "Default stored request"
        def storedRequestModel = BidRequest.defaultStoredRequest

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(bidRequest.ext.prebid.storedRequest.id, storedRequestModel)
        storedRequestDao.save(storedRequest)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request sho ext.prebid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.storedRequest.id
    }

    //todo: user.ext
    //  The whole object is exposed except for the prebid field
    //  consent, eids, data fields are needed for some bidders

    def "PBS should pass user.ext"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(ext: new UserExt().tap {
                fcapids = [PBSUtils.randomString]
                time = new UserTime(userdow: PBSUtils.randomNumber, userhour: PBSUtils.randomNumber)
                prebid = new UserExtPrebid(buyeruids: [(GENERIC): PBSUtils.randomString])
                consentedProvidersSettings = new ConsentedProvidersSettings(consentedProviders: PBSUtils.randomString)
            })
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain user.ext.prebid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user.ext.prebid

        and: "Bidder request should contain fcapid,time,consentedProvidedSettings"
        assert bidderRequest.user.ext.fcapids == bidRequest.user.ext.fcapids
        assert bidderRequest.user.ext.time == bidRequest.user.ext.time
        assert bidderRequest.user.ext.consentedProvidersSettings == bidRequest.user.ext.consentedProvidersSettings
    }

    //todo :site.ext
    //  The whole object is exposed
    //  data and amp fields are needed for some bidders

    def "PBS should pass site.ext"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.ext = new SiteExt(amp: 0, data: new SiteExtData(id: PBSUtils.randomString))
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain user.ext.{amp,data}"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.site.ext.data == bidRequest.site.ext.data
        assert bidderRequest.site.ext.amp == bidRequest.site.ext.amp
    }

    //todo :device.ext.prebid
    //  The whole object is exposed

    @IgnoreRest
    def "PBS should pass device.ext.prebid"() {
        given: "Default basic bid request with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(
                    ext: new DeviceExt(
                            atts: UNKNOWN,
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

    //todo: ext.prebid.adservertargeting (object)
    //    Never needed inside the adapter code

    @IgnoreRest
    def "PBS should pass ext.prebid.auctiontimestamp to bidder request"() {
        given: "Default basic bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.auctionTimestamp = PBSUtils.randomNumber
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain auctionTimestamp"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.auctionTimestamp
    }

    //todo: ext.prebid.adservertargeting (object)
    //    Never needed inside the adapter code

    @IgnoreRest
    def "PBS shouldn't pass ext.prebid.bidderConfig to bidder request"() {
        given: "Default basic bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidderConfig = [new ExtPrebidBidderConfig(bidders: [GENERIC], config:
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
}
