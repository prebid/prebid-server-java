package org.prebid.server.functional.tests.pricefloors

import org.prebid.server.functional.model.mock.services.floorsprovider.PriceFloorRules
import org.prebid.server.functional.model.pricefloors.Country
import org.prebid.server.functional.model.pricefloors.DeviceType
import org.prebid.server.functional.model.pricefloors.MediaType
import org.prebid.server.functional.model.pricefloors.ModelGroup
import org.prebid.server.functional.model.pricefloors.PriceFloorSchema
import org.prebid.server.functional.model.pricefloors.Rule
import org.prebid.server.functional.model.request.auction.AdServer
import org.prebid.server.functional.model.request.auction.App
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Channel
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Format
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.ImpExtData
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

import static org.prebid.server.functional.model.pricefloors.Country.USA
import static org.prebid.server.functional.model.pricefloors.DeviceType.DESKTOP
import static org.prebid.server.functional.model.pricefloors.DeviceType.PHONE
import static org.prebid.server.functional.model.pricefloors.DeviceType.TABLET
import static org.prebid.server.functional.model.pricefloors.MediaType.BANNER
import static org.prebid.server.functional.model.pricefloors.MediaType.VIDEO
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.BUNDLE
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.CHANNEL
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.COUNTRY
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.DEVICE_TYPE
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.DOMAIN
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.GPT_SLOT
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.MEDIA_TYPE
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.PB_AD_SLOT
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.PUB_DOMAIN
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.SITE_DOMAIN
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.SIZE

class PriceFloorsRulesSpec extends PriceFloorsBaseSpec {

    @PendingFeature
    def "PBS should ignore rule and log warning when total number of split entries in a given rule doesn't match the number of fields"() {
        given: "Default BidRequest with price"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsProviderFloorValue = 0.8
        def invalidRule = new Rule(mediaType: BANNER, country: Country.MULTIPLE,
                siteDomain: PBSUtils.randomString).rule
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].schema = new PriceFloorSchema(fields: [MEDIA_TYPE, COUNTRY])
            data.modelGroups[0].values = [(rule)       : floorsProviderFloorValue,
                                          (invalidRule): floorsProviderFloorValue + 0.1]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorsProviderFloorValue
        assert bidderRequest.ext.prebid.floors.data.modelGroups == floorsResponse.data.modelGroups[0].values[0]
    }

    @PendingFeature
    def "PBS should choose correct rule when media type is defined in rules"() {
        given: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].schema = new PriceFloorSchema(fields: [MEDIA_TYPE])
            data.modelGroups[0].values =
                    [(new Rule(mediaType: MediaType.MULTIPLE).rule): bothFloorValue,
                     (new Rule(mediaType: BANNER).rule)            : bannerFloorValue,
                     (new Rule(mediaType: VIDEO).rule)             : videoFloorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == 0.6

        where:
        bidRequest                       | bothFloorValue   | bannerFloorValue | videoFloorValue
        bidRequestWithMultipleMediaTypes | 0.6              | randomFloorValue | randomFloorValue
        BidRequest.defaultBidRequest     | randomFloorValue | 0.6              | randomFloorValue
        BidRequest.defaultVideoRequest   | randomFloorValue | randomFloorValue | 0.6
    }

    @PendingFeature
    def "PBS should choose rule with '*' when imp[0].banner.format contains multiple sizes"() {
        given: "Default BidRequest with price"
        def lowerWidth = 300
        def lowerHigh = 250
        def higherWidth = lowerWidth + 1
        def higherHigh = lowerHigh + 1
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.format = [new Format(w: lowerWidth, h: lowerHigh),
                                    new Format(w: higherWidth, h: higherHigh)]
        }

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def requestFloorValue = 0.8
        def floorsProviderFloorValue = requestFloorValue + 0.1
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].schema = new PriceFloorSchema(fields: [SIZE])
            data.modelGroups[0].values =
                    [(new Rule(size: "*").rule)                           : floorsProviderFloorValue,
                     (new Rule(size: "${lowerWidth}x${lowerHigh}").rule)  : floorsProviderFloorValue + 0.1,
                     (new Rule(size: "${higherWidth}x${higherHigh}").rule): floorsProviderFloorValue + 0.2]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorsProviderFloorValue
    }

    @PendingFeature
    def "PBS should choose correct rule when size is defined in rules"() {
        given: "Default BidRequest with price"
        def width = 300
        def height = 250
        def bidRequest = bidRequestClosure(width, height) as BidRequest

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def requestFloorValue = 0.8
        def floorsProviderFloorValue = requestFloorValue + 0.1
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].schema = new PriceFloorSchema(fields: [SIZE])
            data.modelGroups[0].values =
                    [(new Rule(size: "*").rule)                 : floorsProviderFloorValue + 0.1,
                     (new Rule(size: "${width}x${height}").rule): floorsProviderFloorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorsProviderFloorValue

        where:
        bidRequestClosure <<
                [{ int widthVal, int heightVal -> BidRequest.defaultBidRequest.tap {
                    imp[0].banner.format = [new Format(w: widthVal, h: heightVal)] } },
                 { int widthVal, int heightVal -> BidRequest.defaultBidRequest.tap {
                    imp[0].banner.format = null
                    imp[0].banner.w = widthVal
                    imp[0].banner.h = heightVal } },
                 { int widthVal, int heightVal -> BidRequest.defaultVideoRequest.tap {
                    imp[0].video.w = widthVal
                    imp[0].video.h = heightVal } }]
    }

    @PendingFeature
    def "PBS should choose correct rule when domain is defined in rules"() {
        given: "BidRequest with domain"
        def domain = PBSUtils.randomString
        def bidRequest = bidRequestClosure(domain) as BidRequest

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = 0.8
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].schema = new PriceFloorSchema(fields: [DOMAIN])
            data.modelGroups[0].values =
                    [(new Rule(domain: domain).rule)               : floorValue,
                     (new Rule(domain: PBSUtils.randomString).rule): floorValue + 0.1]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorValue

        where:
        bidRequestClosure << [{ String domainVal -> BidRequest.defaultBidRequest.tap { site.domain = domainVal } },
                       { String domainVal -> BidRequest.defaultBidRequest.tap { site.publisher.domain = domainVal } }]
    }

    def "PBS should choose correct rule when siteDomain is defined in rules"() {
        given: "BidRequest with domain"
        def domain = PBSUtils.randomString
        def accountId = PBSUtils.randomString
        def bidRequest = bidRequestClosure(domain, accountId) as BidRequest


        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(accountId)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = 0.8
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].schema = new PriceFloorSchema(fields: [SITE_DOMAIN])
            data.modelGroups[0].values =
                    [(new Rule(siteDomain: domain).rule)               : floorValue,
                     (new Rule(siteDomain: PBSUtils.randomString).rule): floorValue + 0.1]
        }
        floorsProvider.setResponse(accountId, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorValue

        where:
        bidRequestClosure << [{ String domainVal, String accountIdVal -> BidRequest.defaultBidRequest.tap {
                                    site.domain = domainVal
                                    site.publisher.id = accountIdVal} },
                              { String domainVal, String accountIdVal -> BidRequest.defaultBidRequest.tap {
                                    site = null
                                    app =  App.defaultApp
                                    app.domain = domainVal
                                    app.publisher.id = accountIdVal} }]
    }

     def "PBS should choose correct rule when pubDomain is defined in rules"() {
        given: "BidRequest with domain"
        def domain = PBSUtils.randomString
        def accountId = PBSUtils.randomString
        def bidRequest = bidRequestClosure(domain, accountId) as BidRequest

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(accountId)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = 0.8
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].schema = new PriceFloorSchema(fields: [PUB_DOMAIN])
            data.modelGroups[0].values =
                    [(new Rule(pubDomain: domain).rule)               : floorValue,
                     (new Rule(pubDomain: PBSUtils.randomString).rule): floorValue + 0.1]
        }
        floorsProvider.setResponse(accountId, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorValue

        where:
        bidRequestClosure << [{ String domainVal, String accountIdVal -> BidRequest.defaultBidRequest.tap {
                                    site.publisher.domain = domainVal
                                    site.publisher.id = accountIdVal} },
                              { String domainVal, String accountIdVal -> BidRequest.defaultBidRequest.tap {
                                    site = null
                                    app =  App.defaultApp
                                    app.publisher.domain = domainVal
                                    app.publisher.id = accountIdVal} }]
    }

    @PendingFeature
    def "PBS should choose correct rule when bundle is defined in rules"() {
        given: "BidRequest with domain"
        def bundle = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            app = App.defaultApp
            app.bundle = bundle
        }
        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.app.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = 0.8
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].schema = new PriceFloorSchema(fields: [BUNDLE])
            data.modelGroups[0].values =
                    [(new Rule(bundle: bundle).rule)               : floorValue,
                     (new Rule(bundle: PBSUtils.randomString).rule): floorValue + 0.1]
        }
        floorsProvider.setResponse(bidRequest.app.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorValue
    }

    @PendingFeature
    def "PBS should choose correct rule when channel is defined in rules"() {
        given: "BidRequest with domain"
        def channel = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.channel = new Channel(name: channel)
        }
        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = 0.8
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].schema = new PriceFloorSchema(fields: [CHANNEL])
            data.modelGroups[0].values =
                    [(new Rule(channel: channel).rule)              : floorValue,
                     (new Rule(channel: PBSUtils.randomString).rule): floorValue + 0.1]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorValue
    }

    @PendingFeature
    def "PBS should choose correct rule when gptSlot is defined in rules"() {
        given: "BidRequest with domain"
        def gptSlot = PBSUtils.randomString
        def bidRequest = bidRequestClosure(gptSlot) as BidRequest

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = 0.8
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].schema = new PriceFloorSchema(fields: [GPT_SLOT])
            data.modelGroups[0].values =
                    [(new Rule(gptSlot: gptSlot).rule)               : floorValue,
                     (new Rule(gptSlot: PBSUtils.randomString).rule): floorValue + 0.1]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorValue

        where:
        bidRequestClosure << [{ String gptSlotVal -> BidRequest.defaultBidRequest.tap {
                                    imp[0].ext.data = new ImpExtData(adServer: new AdServer(name: "gam", adSlot: gptSlotVal))} },
                              { String gptSlotVal -> BidRequest.defaultBidRequest.tap {
                                    imp[0].ext.data = new ImpExtData(adServer: new AdServer(name: PBSUtils.randomString, adSlot: gptSlotVal))} },
                              { String gptSlotVal -> BidRequest.defaultBidRequest.tap {
                                    imp[0].ext.data = new ImpExtData(adServer: new AdServer(name: PBSUtils.randomString), pbAdSlot: gptSlotVal)} }]
    }

    @PendingFeature
    def "PBS should choose correct rule when pbAdSlot is defined in rules"() {
        given: "BidRequest with domain"
        def pbAdSlot = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.data = new ImpExtData(pbAdSlot: pbAdSlot)
        }
        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = 0.8
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].schema = new PriceFloorSchema(fields: [PB_AD_SLOT])
            data.modelGroups[0].values =
                    [(new Rule(pbAdSlot: pbAdSlot).rule)             : floorValue,
                     (new Rule(pbAdSlot: PBSUtils.randomString).rule): floorValue + 0.1]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorValue
    }

    @PendingFeature
    def "PBS should choose correct rule when country is defined in rules"() {
        given: "BidRequest with domain"
        def country = USA
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(geo: new Geo(country: country))
        }
        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = 0.8
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].schema = new PriceFloorSchema(fields: [COUNTRY])
            data.modelGroups[0].values =
                    [(new Rule(country: country).rule)         : floorValue,
                     (new Rule(country: Country.MULTIPLE).rule): floorValue + 0.1]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorValue
    }

    @PendingFeature
    def "PBS should choose correct rule when devicetype is defined in rules"() {
        given: "BidRequest with device.ua"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(ua: deviceType)
        }

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].schema = new PriceFloorSchema(fields: [DEVICE_TYPE])
            data.modelGroups[0].values =
                    [(new Rule(deviceType: PHONE).rule)              : phoneFloorValue,
                     (new Rule(deviceType: TABLET).rule)             : tabletFloorValue,
                     (new Rule(deviceType: DESKTOP).rule)            : desktopFloorValue,
                     (new Rule(deviceType: DeviceType.MULTIPLE).rule): floorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == 0.8

        where:
        deviceType            | phoneFloorValue  | tabletFloorValue | desktopFloorValue
        "Phone"               | 0.8              | randomFloorValue | randomFloorValue
        "iPhone"              | 0.8              | randomFloorValue | randomFloorValue
        "Android.*Mobile"     | 0.8              | randomFloorValue | randomFloorValue
        "Mobile.*Android"     | 0.8              | randomFloorValue | randomFloorValue
        "tablet"              | randomFloorValue | 0.8              | randomFloorValue
        "iPad"                | randomFloorValue | 0.8              | randomFloorValue
        "Windows NT.*touch"   | randomFloorValue | 0.8              | randomFloorValue
        "touch.*Windows NT"   | randomFloorValue | 0.8              | randomFloorValue
        "Android"             | randomFloorValue | 0.8              | randomFloorValue
        PBSUtils.randomString | randomFloorValue | randomFloorValue | 0.8
    }

    @PendingFeature
    def "PBS should choose no rule specifying deviceType when device is not present"() {
        given: "BidRequest with device.ua"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = null
        }

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = randomFloorValue
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups << ModelGroup.modelGroup
            data.modelGroups[0].schema = new PriceFloorSchema(fields: [MEDIA_TYPE])
            data.modelGroups[0].values =
                    [(new Rule(mediaType: BANNER).rule): floorValue]
            data.modelGroups[1].schema = new PriceFloorSchema(fields: [DEVICE_TYPE])
            data.modelGroups[1].values =
                    [(new Rule(deviceType: PHONE).rule)              : floorValue + 0.1,
                     (new Rule(deviceType: TABLET).rule)             : floorValue + 0.2,
                     (new Rule(deviceType: DESKTOP).rule)            : floorValue + 0.3,
                     (new Rule(deviceType: DeviceType.MULTIPLE).rule): floorValue + 0.4]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorValue
    }

    @PendingFeature
    def "PBS should use data.modelGroups[].default when no matching rules are found"() {
        given: "BidRequest with device.ua"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = randomFloorValue
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].schema = new PriceFloorSchema(fields: [MEDIA_TYPE])
            data.modelGroups[0].values = [(new Rule(mediaType: VIDEO).rule): floorValue + 0.1]
            data.modelGroups[0].defaultFloor = floorValue
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == floorValue
    }
}
