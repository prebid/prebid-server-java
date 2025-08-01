package org.prebid.server.functional.tests.pricefloors

import org.prebid.server.functional.model.ChannelType
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.bidderspecific.BidderRequest
import org.prebid.server.functional.model.config.AlternateBidderCodes
import org.prebid.server.functional.model.config.BidderConfig
import org.prebid.server.functional.model.db.StoredImp
import org.prebid.server.functional.model.pricefloors.Country
import org.prebid.server.functional.model.pricefloors.FloorModelGroup
import org.prebid.server.functional.model.pricefloors.MediaType
import org.prebid.server.functional.model.pricefloors.PriceFloorData
import org.prebid.server.functional.model.pricefloors.PriceFloorSchema
import org.prebid.server.functional.model.pricefloors.Rule
import org.prebid.server.functional.model.request.auction.Amx
import org.prebid.server.functional.model.request.auction.Banner
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.ExtPrebidFloors
import org.prebid.server.functional.model.request.auction.ExtPrebidPriceFloorEnforcement
import org.prebid.server.functional.model.request.auction.Format
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.ImpExtContextData
import org.prebid.server.functional.model.request.auction.ImpExtContextDataAdServer
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.model.response.auction.BidExt
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.PBSUtils

import java.time.Instant

import static org.prebid.server.functional.model.ChannelType.WEB
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.AMX
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.pricefloors.Country.USA
import static org.prebid.server.functional.model.pricefloors.DeviceType.DESKTOP
import static org.prebid.server.functional.model.pricefloors.DeviceType.MULTIPLE
import static org.prebid.server.functional.model.pricefloors.DeviceType.PHONE
import static org.prebid.server.functional.model.pricefloors.DeviceType.TABLET
import static org.prebid.server.functional.model.pricefloors.MediaType.BANNER
import static org.prebid.server.functional.model.pricefloors.MediaType.VIDEO
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.AD_UNIT_CODE
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.BIDDER
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.BOGUS
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.BUNDLE
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.CHANNEL
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.COUNTRY
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.DEVICE_TYPE
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.DOMAIN
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.GPT_SLOT
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.MEDIA_TYPE
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.PUB_DOMAIN
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.SITE_DOMAIN
import static org.prebid.server.functional.model.pricefloors.PriceFloorField.SIZE
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.DOOH
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.request.auction.FetchStatus.ERROR
import static org.prebid.server.functional.model.request.auction.Location.NO_DATA
import static org.prebid.server.functional.model.request.auction.Prebid.Channel
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.RESPONSE_REJECTED_DUE_TO_PRICE_FLOOR
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class PriceFloorsRulesSpec extends PriceFloorsBaseSpec {

    def "PBS should ignore rule when total number of split entries in a given rule doesn't match the number of fields"() {
        given: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = 0.8
        def invalidRule = new Rule(mediaType: BANNER, country: Country.MULTIPLE,
                siteDomain: PBSUtils.randomString).rule
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [MEDIA_TYPE, COUNTRY])
            modelGroups[0].values = [(rule)       : floorValue,
                                     (invalidRule): floorValue + 0.1]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorValue

        and: "Bidder request shouldn't include imp.ext.prebid.floors"
        assert !bidderRequest.imp[0].ext.prebid.floors
    }

    def "PBS should support different delimiters for floor rules"() {
        given: "Default bidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [MEDIA_TYPE, COUNTRY], delimiter: delimiter)
            modelGroups[0].values = [(new Rule(delimiter: delimiter, mediaType: MediaType.MULTIPLE, country: Country.MULTIPLE).rule): PBSUtils.randomFloorValue,
                                     (new Rule(delimiter: delimiter, mediaType: BANNER, country: Country.MULTIPLE).rule)            : floorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid.first().price = floorValue
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should not contain errors, warnings"
        assert !response.ext?.warnings
        assert !response.ext?.errors

        and: "PBS should not reject the entire auction"
        assert !response.seatbid.isEmpty()

        and: "Bidder request bidFloor should correspond to appropriate media type"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorValue

        where:
        delimiter << [".", "/"]
    }

    def "PBS should treat rule values case-insensitive"() {
        given: "BidRequest with domain"
        def domain = PBSUtils.randomString
        def accountId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.domain = domain
            site.publisher.id = accountId
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(accountId)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [DOMAIN])
            modelGroups[0].values = [(new Rule(domain: domain).rule.toUpperCase())               : floorValue,
                                     (new Rule(domain: PBSUtils.randomString).rule.toUpperCase()): floorValue + 0.1]
        }
        floorsProvider.setResponse(accountId, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorValue
    }

    def "PBS should consider rules file invalid when rules file contains an unrecognized dimension in the schema"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "BidRequest with domain"
        def domain = PBSUtils.randomString
        def accountId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.domain = domain
            site.publisher.id = accountId
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(accountId)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups << FloorModelGroup.modelGroup
            modelGroups[0].schema = new PriceFloorSchema(fields: [BOGUS])
            modelGroups[0].values = [(new Rule(domain: domain).rule): floorValue + 0.1]
            modelGroups[1].schema = new PriceFloorSchema(fields: [DOMAIN])
            modelGroups[1].values = [(new Rule(domain: domain).rule): floorValue]
        }
        floorsProvider.setResponse(accountId, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid.first().price = floorValue
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should pass bidFloor"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert !bidderRequest.imp[0].bidFloor
        assert bidderRequest.ext?.prebid?.floors?.location == NO_DATA
        assert bidderRequest.ext?.prebid?.floors?.fetchStatus == ERROR

        and: "PBS should not contain errors or warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "PBS should log a warning"
        def logs = floorsPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Cannot deserialize value of type " +
                "`org.prebid.server.floors.model.PriceFloorField` " +
                "from String \"bogus\": not one of the values accepted for Enum class").size() == 1

        and: "PBS should not reject the entire auction"
        assert !response.seatbid.isEmpty()
    }

    def "PBS should round floor value to 4-digits of precision"() {
        given: "BidRequest with domain"
        def domain = PBSUtils.randomString
        def accountId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.domain = domain
            site.publisher.id = accountId
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(accountId)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.roundDecimal(PBSUtils.getRandomDecimal(FLOOR_MIN, 2), 6)
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [DOMAIN])
            modelGroups[0].values = [(new Rule(domain: domain).rule): floorValue]
        }
        floorsProvider.setResponse(accountId, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should be rounded"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == getRoundedFloorValue(floorValue)
    }

    def "PBS should choose correct rule when media type is defined in rules"() {
        given: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [MEDIA_TYPE])
            modelGroups[0].values = [(new Rule(mediaType: MediaType.MULTIPLE).rule): bothFloorValue,
                                     (new Rule(mediaType: BANNER).rule)            : bannerFloorValue,
                                     (new Rule(mediaType: VIDEO).rule)             : videoFloorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate media type"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == 0.6

        where:
        bidRequest                       | bothFloorValue            | bannerFloorValue          | videoFloorValue
        bidRequestWithMultipleMediaTypes | 0.6                       | PBSUtils.randomFloorValue | PBSUtils.randomFloorValue
        BidRequest.defaultBidRequest     | PBSUtils.randomFloorValue | 0.6                       | PBSUtils.randomFloorValue
        BidRequest.defaultVideoRequest   | PBSUtils.randomFloorValue | PBSUtils.randomFloorValue | 0.6
    }

    def "PBS should choose rule with '*' when imp[0].banner.format contains multiple sizes"() {
        given: "Default BidRequest with format"
        def lowerWidth = 300
        def lowerHigh = 250
        def higherWidth = lowerWidth + 1
        def higherHigh = lowerHigh + 1
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.format = [new Format(weight: lowerWidth, height: lowerHigh),
                                    new Format(weight: higherWidth, height: higherHigh)]
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def requestFloorValue = 0.8
        def floorsProviderFloorValue = requestFloorValue + 0.1
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [SIZE])
            modelGroups[0].values = [(new Rule(size: "*").rule)                           : floorsProviderFloorValue,
                                     (new Rule(size: "${lowerWidth}x${lowerHigh}").rule)  : floorsProviderFloorValue +
                                             0.1,
                                     (new Rule(size: "${higherWidth}x${higherHigh}").rule): floorsProviderFloorValue +
                                             0.2]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorsProviderFloorValue
    }

    def "PBS should choose correct rule when size is defined in rules"() {
        given: "Default BidRequest with size"
        def width = 300
        def height = 250
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0] = impClosure(width, height)
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def requestFloorValue = 0.8
        def floorsProviderFloorValue = requestFloorValue + 0.1
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [SIZE])
            modelGroups[0].values = [(new Rule(size: "*").rule)                 : floorsProviderFloorValue + 0.1,
                                     (new Rule(size: "${width}x${height}").rule): floorsProviderFloorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorsProviderFloorValue

        where:
        mediaType                                                            | impClosure
        org.prebid.server.functional.model.response.auction.MediaType.BANNER | { int widthVal, int heightVal ->
            Imp.getDefaultImpression(mediaType).tap {
                banner.format = [new Format(weight: widthVal, height: heightVal)]
            }
        }
        org.prebid.server.functional.model.response.auction.MediaType.BANNER | { int widthVal, int heightVal ->
            Imp.getDefaultImpression(mediaType).tap {
                banner.format = null
                banner.weight = widthVal
                banner.height = heightVal
            }
        }
        org.prebid.server.functional.model.response.auction.MediaType.VIDEO  | { int widthVal, int heightVal ->
            Imp.getDefaultImpression(mediaType).tap {
                video.weight = widthVal
                video.height = heightVal
            }
        }
    }

    def "PBS should choose correct rule when domain is defined in rules for #distributionChannel channel"() {
        given: "BidRequest with domain"
        def domain = PBSUtils.randomString
        def accountId = PBSUtils.randomString
        def bidRequest = bidRequestClosure(domain, accountId) as BidRequest

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(accountId)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [DOMAIN])
            modelGroups[0].values = [(new Rule(domain: domain).rule)               : floorValue,
                                     (new Rule(domain: PBSUtils.randomString).rule): floorValue + 0.1]
        }
        floorsProvider.setResponse(accountId, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorValue

        where:
        distributionChannel | bidRequestClosure
        SITE                | { String publisherDomain, String publisherAccountId ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                site.domain = publisherDomain
                site.publisher.id = publisherAccountId
            }
        }
        SITE                | { String publisherDomain, String publisherAccountId ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                site.publisher.domain = publisherDomain
                site.publisher.id = publisherAccountId
            }
        }
        APP                 | { String publisherDomain, String publisherAccountId ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                app.domain = publisherDomain
                app.publisher.id = publisherAccountId
            }
        }
        APP                 | { String publisherDomain, String publisherAccountId ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                app.publisher.domain = publisherDomain
                app.publisher.id = publisherAccountId
            }
        }
        DOOH                | { String publisherDomain, String publisherAccountId ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                dooh.domain = publisherDomain
                dooh.publisher.id = publisherAccountId
            }
        }
        DOOH                | { String publisherDomain, String publisherAccountId ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                dooh.publisher.domain = publisherDomain
                dooh.publisher.id = publisherAccountId
            }
        }
    }

    def "PBS should choose correct rule when siteDomain is defined in rules for #distributionChannel channel"() {
        given: "BidRequest with domain"
        def domain = PBSUtils.randomString
        def accountId = PBSUtils.randomString
        def bidRequest = bidRequestClosure(domain, accountId) as BidRequest

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(accountId)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [SITE_DOMAIN])
            modelGroups[0].values = [(new Rule(siteDomain: domain).rule)               : floorValue,
                                     (new Rule(siteDomain: PBSUtils.randomString).rule): floorValue + 0.1]
        }
        floorsProvider.setResponse(accountId, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorValue

        where:
        distributionChannel | bidRequestClosure
        SITE                | { String publisherDomain, String publisherAccountId ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                site.domain = publisherDomain
                site.publisher.id = publisherAccountId
            }
        }
        APP                 | { String publisherDomain, String publisherAccountId ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                app.domain = publisherDomain
                app.publisher.id = publisherAccountId
            }
        }
        DOOH                | { String publisherDomain, String publisherAccountId ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                dooh.domain = publisherDomain
                dooh.publisher.id = publisherAccountId
            }
        }
    }

    def "PBS should choose correct rule when pubDomain is defined in rules for #distributionChannel channel"() {
        given: "BidRequest with domain"
        def domain = PBSUtils.randomString
        def accountId = PBSUtils.randomString
        def bidRequest = bidRequestClosure(domain, accountId)

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(accountId)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [PUB_DOMAIN])
            modelGroups[0].values = [(new Rule(pubDomain: domain).rule)               : floorValue,
                                     (new Rule(pubDomain: PBSUtils.randomString).rule): floorValue + 0.1]
        }
        floorsProvider.setResponse(accountId, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorValue

        where:
        distributionChannel | bidRequestClosure
        SITE                | { String publisherDomain, String publisherAccountId ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                site.publisher.domain = publisherDomain
                site.publisher.id = publisherAccountId
            }
        }
        APP                 | { String publisherDomain, String publisherAccountId ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                app.publisher.domain = publisherDomain
                app.publisher.id = publisherAccountId
            }
        }
        DOOH                | { String publisherDomain, String publisherAccountId ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                dooh.publisher.domain = publisherDomain
                dooh.publisher.id = publisherAccountId
            }
        }
    }

    def "PBS should choose correct rule when bundle is defined in rules"() {
        given: "BidRequest with domain"
        def bundle = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.bundle = bundle
        }
        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.app.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [BUNDLE])
            modelGroups[0].values = [(new Rule(bundle: bundle).rule)               : floorValue,
                                     (new Rule(bundle: PBSUtils.randomString).rule): floorValue + 0.1]
        }
        floorsProvider.setResponse(bidRequest.app.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorValue
    }

    def "PBS should choose correct rule when channel is defined in rules"() {
        given: "BidRequest with domain"
        def channel = WEB
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.channel = new Channel().tap {
                name = channel
            }
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [CHANNEL])
            modelGroups[0].values = [(new Rule(channel: channel).rule)        : floorValue,
                                     (new Rule(channel: ChannelType.APP).rule): floorValue + 0.1]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorValue
    }

    def "PBS should choose correct rule when gptSlot is defined in rules"() {
        given: "BidRequest with domain"
        def gptSlot = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.data = contextDataClosure(gptSlot)
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [GPT_SLOT])
            modelGroups[0].values = [(new Rule(gptSlot: gptSlot).rule)              : floorValue,
                                     (new Rule(gptSlot: PBSUtils.randomString).rule): floorValue + 0.1]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorValue

        where:
        contextDataClosure <<
                [{ String gptSlotVal -> new ImpExtContextData(adServer: new ImpExtContextDataAdServer(name: "gam", adSlot: gptSlotVal)) },
                 { String gptSlotVal -> new ImpExtContextData(adServer: new ImpExtContextDataAdServer(name: PBSUtils.randomString), pbAdSlot: gptSlotVal) }]
    }

    def "PBS should choose correct rule when adUnitCode is defined in rules"() {
        given: "BidRequest with domain"
        def randomStringValue = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            updateImpClosure(randomStringValue, it.imp[0])
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [AD_UNIT_CODE])
            modelGroups[0].values = [(new Rule(adUnitCode: randomStringValue).rule)    : floorValue,
                                     (new Rule(adUnitCode: PBSUtils.randomString).rule): floorValue + 0.1]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorValue

        where:
        updateImpClosure << [{ anyStr, imp -> imp.ext.gpid = anyStr },
                             { anyStr, imp -> imp.tagId = anyStr },
                             { anySrt, imp -> imp.ext.data = new ImpExtContextData(pbAdSlot: anySrt) }]
    }

    def "PBS should choose correct rule when adUnitCode is defined in rules with stored request"() {
        given: "BidRequest with stored request"
        def randomString = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.storedRequest = new PrebidStoredRequest(id: randomString)
        }

        and: "Save storedImp into DB"
        def storedImp = StoredImp.getStoredImp(bidRequest).tap {
            impData = Imp.defaultImpression
        }
        storedImpDao.save(storedImp)

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [AD_UNIT_CODE])
            modelGroups[0].values =
                    [(new Rule(adUnitCode: randomString).rule)         : floorValue,
                     (new Rule(adUnitCode: PBSUtils.randomString).rule): floorValue + 0.1]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorValue
    }

    def "PBS should choose correct rule when country is defined in rules"() {
        given: "BidRequest with domain"
        def country = USA
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(geo: new Geo(country: country))
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [COUNTRY])
            modelGroups[0].values = [(new Rule(country: country).rule)         : floorValue,
                                     (new Rule(country: Country.MULTIPLE).rule): floorValue + 0.1]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorValue
    }

    def "PBS should choose correct rule when devicetype is defined in rules"() {
        given: "BidRequest with device.ua"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(ua: deviceType)
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [DEVICE_TYPE])
            modelGroups[0].values = [(new Rule(deviceType: PHONE).rule)   : phoneFloorValue,
                                     (new Rule(deviceType: TABLET).rule)  : tabletFloorValue,
                                     (new Rule(deviceType: DESKTOP).rule) : desktopFloorValue,
                                     (new Rule(deviceType: MULTIPLE).rule): PBSUtils.randomFloorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == 0.8

        where:
        deviceType            | phoneFloorValue           | tabletFloorValue          | desktopFloorValue
        "Phone"               | 0.8                       | PBSUtils.randomFloorValue | PBSUtils.randomFloorValue
        "iPhone"              | 0.8                       | PBSUtils.randomFloorValue | PBSUtils.randomFloorValue
        "Android.*Mobile"     | 0.8                       | PBSUtils.randomFloorValue | PBSUtils.randomFloorValue
        "Mobile.*Android"     | 0.8                       | PBSUtils.randomFloorValue | PBSUtils.randomFloorValue
        "tablet"              | PBSUtils.randomFloorValue | 0.8                       | PBSUtils.randomFloorValue
        "iPad"                | PBSUtils.randomFloorValue | 0.8                       | PBSUtils.randomFloorValue
        "Windows NT.*touch"   | PBSUtils.randomFloorValue | 0.8                       | PBSUtils.randomFloorValue
        "touch.*Windows NT"   | PBSUtils.randomFloorValue | 0.8                       | PBSUtils.randomFloorValue
        "Android"             | PBSUtils.randomFloorValue | 0.8                       | PBSUtils.randomFloorValue
        PBSUtils.randomString | PBSUtils.randomFloorValue | PBSUtils.randomFloorValue | 0.8
    }

    def "PBS should choose wildcard device type when device is not present"() {
        given: "BidRequest without device.ua"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = null
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response with wildcard deviceType rule"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [DEVICE_TYPE])
            modelGroups[0].values = [(new Rule(deviceType: PHONE).rule)   : floorValue + 0.1,
                                     (new Rule(deviceType: TABLET).rule)  : floorValue + 0.2,
                                     (new Rule(deviceType: DESKTOP).rule) : floorValue + 0.3,
                                     (new Rule(deviceType: MULTIPLE).rule): floorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request with empty user agent header"
        floorsPbsService.sendAuctionRequest(bidRequest, ["User-Agent": ""])

        then: "Bidder request bidFloor should correspond to a wildcard rule"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorValue
    }

    def "PBS should use data.modelGroups[].default when no matching rules are found"() {
        given: "BidRequest with device.ua"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [MEDIA_TYPE])
            modelGroups[0].values = [(new Rule(mediaType: VIDEO).rule): floorValue + 0.1]
            modelGroups[0].defaultFloor = floorValue
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to defaultFloor"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == floorValue
    }

    def "PBS should choose correct rule based on full match when domain, mediaType, gptSlot are defined in rules"() {
        given: "Default bidRequest with domain, mediaType, adSlot"
        def domain = "example.com"
        def adSlot = "/111/k/categorytop/footer_right/300x250"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.domain = domain
            imp[0].banner = Banner.defaultBanner
            imp[0].ext.data = new ImpExtContextData(adServer: new ImpExtContextDataAdServer(name: "gam", adSlot: adSlot))
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = new File("src/test/resources/org/prebid/server/functional/floor-rules.json").text
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule value"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == 0.06
    }

    def "PBS should choose correct rule based on incomplete match when domain, mediaType, gptSlot are defined in rules"() {
        given: "Default bidRequest with domain, mediaType, adSlot"
        def domain = "example.com"
        def adSlot = "test"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.domain = domain
            imp[0].banner = Banner.defaultBanner
            imp[0].ext.data = new ImpExtContextData(adServer: new ImpExtContextDataAdServer(name: "gam", adSlot: adSlot))
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = new File("src/test/resources/org/prebid/server/functional/floor-rules.json").text
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule value"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        assert bidderRequest.imp[0].bidFloor == 0.04
    }

    def "PBS should populate seatNonBid when bid rejected due to floor"() {
        given: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(enforcePbs: enforcePbs))
            ext.prebid.returnAllBidStatus = true
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS cache rules"
        cacheFloorsProviderRules(bidRequest, floorValue, floorsPbsService)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid[0].price = floorValue - 0.1
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid and contain errors"
        def seatNonBids = response.ext.seatnonbid
        assert seatNonBids.size() == 1

        def seatNonBid = seatNonBids[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_DUE_TO_PRICE_FLOOR
        assert seatNonBid.nonBid.size() == bidResponse.seatbid[0].bid.size()

        where:
        enforcePbs << [true, null]
    }

    def "PBS shouldn't populate seatNonBid when rejected due to floor and returnAllBidStatus is false"() {
        given: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(enforcePbs: enforcePbs))
            ext.prebid.returnAllBidStatus = false
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS cache rules"
        cacheFloorsProviderRules(bidRequest, floorValue, floorsPbsService)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid[0].price = floorValue - 0.1
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain seatNonBid and contain errors"
        assert !response.ext.seatnonbid
        assert !response.seatbid

        where:
        enforcePbs << [true, null]
    }

    def "PBS should use correct rule for each bidder when a bidder is defined with valid bid floor value"() {
        given: "PBS config with openX bidder"
        def floorsPbsService = pbsServiceFactory.getService(
                FLOORS_CONFIG + GENERIC_ALIAS_CONFIG +
                        ["adapters.openx.enabled" : "true",
                         "adapters.openx.endpoint": "$networkServiceContainer.rootUri/auction".toString()])

        and: "Default bid Request with generic and openx bidder within separate imps"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            addImp(Imp.defaultImpression.tap {
                ext.prebid.bidder.generic = null
                ext.prebid.bidder.openx = Openx.defaultOpenx
            })
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.accountId)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [BIDDER])
            modelGroups[0].values = [(new Rule(bidder: GENERIC).rule): genericBidFloorRuleValue,
                                     (new Rule(bidder: OPENX).rule)  : openxBidFloorRuleValue]
        }
        floorsProvider.setResponse(bidRequest.accountId, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest, floorsPbsService)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule"
        def bidderRequest = getRequests(response)
        assert bidderRequest.size() == 2
        assert bidderRequest[GENERIC.value].first.imp[0].bidFloor == genericBidFloorRuleValue
        assert bidderRequest[OPENX.value].first.imp[0].bidFloor == openxBidFloorRuleValue

        and: "Bidder request should contain proper bid floor value"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        def impIdToBidderCallImp = impIdToBidderCallImp(bidderRequests)
        assert impIdToBidderCallImp[bidRequest.imp[0].id].bidFloor == genericBidFloorRuleValue
        assert impIdToBidderCallImp[bidRequest.imp[1].id].bidFloor == openxBidFloorRuleValue

        where:
        genericBidFloorRuleValue  | openxBidFloorRuleValue
        null                      | PBSUtils.randomFloorValue
        PBSUtils.randomFloorValue | null
        PBSUtils.randomFloorValue | PBSUtils.randomFloorValue
    }

    def "PBS shouldn't apply the floor rule for the main bidder to alias bidder"() {
        given: "Default bid request with generic and alias bidder within separate imps"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            addImp(Imp.defaultImpression.tap {
                ext.prebid.bidder.generic = null
                ext.prebid.bidder.alias = new Generic()
            })
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.accountId)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [BIDDER])
            modelGroups[0].values = [(new Rule(bidder: GENERIC).rule): genericBidFloorRuleValue]
        }
        floorsProvider.setResponse(bidRequest.accountId, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest, floorsPbsService)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule"
        def bidderRequest = getRequests(response)
        assert bidderRequest.size() == 2
        assert bidderRequest[GENERIC.value].first.imp[0].bidFloor == genericBidFloorRuleValue
        assert !bidderRequest[ALIAS.value].first.imp[0].bidFloor

        and: "Bidder request should contain proper bid floor value"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        def impIdToBidderCallImp = impIdToBidderCallImp(bidderRequests)
        assert impIdToBidderCallImp[bidRequest.imp[0].id].bidFloor == genericBidFloorRuleValue
        assert !impIdToBidderCallImp[bidRequest.imp[1].id].bidFloor

        where:
        genericBidFloorRuleValue  | aliasBidFloorRuleValue
        null                      | PBSUtils.randomFloorValue
        PBSUtils.randomFloorValue | null
        PBSUtils.randomFloorValue | PBSUtils.randomFloorValue
    }

    def "PBS shouldn't apply the floor rule for the alias bidder to main bidder"() {
        given: "Default bid request with generic and alias bidder within separate imp"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            addImp(Imp.defaultImpression.tap {
                ext.prebid.bidder.generic = null
                ext.prebid.bidder.alias = new Generic()
            })
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.accountId)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].schema = new PriceFloorSchema(fields: [BIDDER])
            modelGroups[0].values = [(new Rule(bidder: ALIAS).rule): aliasBidFloorRuleValue]
        }
        floorsProvider.setResponse(bidRequest.accountId, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest, floorsPbsService)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond to appropriate rule"
        def bidderRequest = getRequests(response)
        assert bidderRequest.size() == 2
        assert bidderRequest[ALIAS.value].first.imp[0].bidFloor == aliasBidFloorRuleValue
        assert !bidderRequest[GENERIC.value].first.imp[0].bidFloor

        and: "Bidder request should contain proper bid floor value"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        def impIdToBidderCallImp = impIdToBidderCallImp(bidderRequests)
        assert impIdToBidderCallImp[bidRequest.imp[1].id].bidFloor == aliasBidFloorRuleValue
        assert !impIdToBidderCallImp[bidRequest.imp[0].id].bidFloor

        where:
        genericBidFloorRuleValue  | aliasBidFloorRuleValue
        null                      | PBSUtils.randomFloorValue
        PBSUtils.randomFloorValue | null
        PBSUtils.randomFloorValue | PBSUtils.randomFloorValue
    }

    def "PBS should populate seatNonBid when bid rejected due to floor and alternate bidder code specified"() {
        given: "PBS config with floors config"
        def config = FLOORS_CONFIG +
                ["adapters.amx.enabled" : "true",
                 "adapters.amx.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
        def pbsService = pbsServiceFactory.getService(config)

        and: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.tap {
                generic = null
                amx = new Amx()
            }
            ext.prebid.tap {
                floors = new ExtPrebidFloors(enforcement: new ExtPrebidPriceFloorEnforcement(enforcePbs: true))
                returnAllBidStatus = true
                alternateBidderCodes = new AlternateBidderCodes(
                        enabled: true,
                        bidders: [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])])
            }
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorData.priceFloorData.tap {
            modelGroups[0].values = [(rule): floorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS cache rules"
        cacheFloorsProviderRules(bidRequest, floorValue, pbsService, AMX)

        and: "Set bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid.first().tap {
                price = floorValue - 0.1
                ext = new BidExt(bidderCode: GENERIC)
            }
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid"
        def seatNonBids = response.ext.seatnonbid
        assert seatNonBids.size() == 1

        def seatNonBid = seatNonBids[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_DUE_TO_PRICE_FLOOR
        assert seatNonBid.nonBid.size() == bidResponse.seatbid[0].bid.size()
    }

    private static Map<String, Imp> impIdToBidderCallImp(List<BidderRequest> bidderRequests) {
        bidderRequests.imp.flatten().collectEntries { [it.id, it] } as Map<String, Imp>
    }
}
