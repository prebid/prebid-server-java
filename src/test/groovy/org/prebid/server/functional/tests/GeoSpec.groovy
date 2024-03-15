package org.prebid.server.functional.tests

import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.config.AccountSetting
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.util.PBSUtils

import java.time.Instant

import static org.prebid.server.functional.model.AccountStatus.ACTIVE
import static org.prebid.server.functional.model.pricefloors.Country.USA
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE
import static org.prebid.server.functional.util.privacy.model.State.ALABAMA

class GeoSpec extends BaseSpec {

    private static final Map<String, String> GEO_LOCATION = ["geolocation.type"                               : "configuration",
                                                             "geolocation.configurations.[0].address-pattern" : "209.",
                                                             "geolocation.configurations.[0].geo-info.country": "US",
                                                             "geolocation.configurations.[0].geo-info.region" : "AL"]

    def "PBS should populate geo with country and region when geo location enabled in host and account config"() {
        given: "Default basic generic BidRequest"
        def config = AccountConfig.defaultAccountConfig.tap {
            settings = new AccountSetting(geoLookup: defaultAccountGeoLookup)
        }
        def defaultPbsService = pbsServiceFactory.getService(
                ["settings.default-account-config": encode(config),
                 "geolocation.enabled"            : "true"] + GEO_LOCATION)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(
                    ip: "209.232.44.21",
                    ipv6: "af47:892b:3e98:b49a:a747:bda4:a6c8:aee2",
                    geo: new Geo(
                            country: null,
                            region: null,
                            lat: PBSUtils.getRandomDecimal(0, 90),
                            lon: PBSUtils.getRandomDecimal(0, 90)))
            ext.prebid.trace = VERBOSE
        }

        and: "Account in the DB"
        def accountConfig = new AccountConfig(
                auction: new AccountAuctionConfig(debugAllow: true),
                settings: new AccountSetting(geoLookup: accountGeoLookup))
        def account = new Account(status: ACTIVE, uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest, ["X-Forwarded-For": "209.232.44.21"])

        then: "Bidder request should contain country and region"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device.geo.country == USA
        assert bidderRequests.device.geo.region == ALABAMA.abbreviation

        and: "Metrics processed across activities should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics["geolocation_requests"] == 1
        assert metrics["geolocation_successful"] == 1

        where:
        defaultAccountGeoLookup | accountGeoLookup
        false                   | true
        true                    | true
        true                    | null
    }

    def "PBS shouldn't populate geo with country and region when geo location disable in host and account config enabled"() {
        given: "Default basic generic BidRequest"
        def config = AccountConfig.defaultAccountConfig.tap {
            settings = new AccountSetting(geoLookup: defaultAccountGeoLookupConfig)
        }
        def defaultPbsService = pbsServiceFactory.getService(GEO_LOCATION +
                ["settings.default-account-config": encode(config),
                 "geolocation.enabled"            : hostGeolocation])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(
                    ip: "209.232.44.21",
                    ipv6: "af47:892b:3e98:b49a:a747:bda4:a6c8:aee2",
                    geo: new Geo(
                            country: null,
                            region: null,
                            lat: PBSUtils.getRandomDecimal(0, 90),
                            lon: PBSUtils.getRandomDecimal(0, 90)))
            ext.prebid.trace = VERBOSE
        }

        and: "Account in the DB"
        def accountConfig = new AccountConfig(
                auction: new AccountAuctionConfig(debugAllow: true),
                settings: new AccountSetting(geoLookup: accountGeoLookup))
        def account = new Account(status: ACTIVE, uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest, ["X-Forwarded-For": "209.232.44.21"])

        then: "Bidder request shouldn't contain country and region"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequests.device.geo.country
        assert !bidderRequests.device.geo.region

        and: "Metrics processed across geo location shouldn't be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert !metrics["geolocation_requests"]
        assert !metrics["geolocation_successful"]

        where:
        defaultAccountGeoLookupConfig | hostGeolocation | accountGeoLookup
        true                          | "true"          | false
        true                          | "false"         | true
        false                         | "false"         | false
        false                         | "true"          | false
    }

    def "PBS shouldn't populate geo with country, region and emit error in log and metric when geo look up failed"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default basic generic BidRequest"
        def defaultPbsService = pbsServiceFactory.getService(GEO_LOCATION +
                ["geolocation.configurations.[0].address-pattern": "302.",
                 "geolocation.enabled"                           : "true"])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(
                    ip: "209.232.44.21",
                    ipv6: "af47:892b:3e98:b49a:a747:bda4:a6c8:aee2",
                    geo: new Geo(
                            country: null,
                            region: null,
                            lat: PBSUtils.getRandomDecimal(0, 90),
                            lon: PBSUtils.getRandomDecimal(0, 90)))
            ext.prebid.trace = VERBOSE
        }

        and: "Account in the DB"
        def accountConfig = new AccountConfig(
                auction: new AccountAuctionConfig(debugAllow: true),
                settings: new AccountSetting(geoLookup: true))
        def account = new Account(status: ACTIVE, uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest, ["X-Forwarded-For": "209.232.44.21"])

        then: "Bidder request shouldn't contain country and region"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequests.device.geo.country
        assert !bidderRequests.device.geo.region

        and: "Metrics processed across geo location should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics["geolocation_requests"] == 1
        assert metrics["geolocation_fail"] == 1
        assert !metrics["geolocation_successful"]

        and: "PBs should emit geo failed logs"
        def logs = defaultPbsService.getLogsByTime(startTime)
        def getLocation = getLogsByText(logs, "GeoLocationServiceWrapper")
        assert getLocation.size() == 1
        assert getLocation[0].contains("Geolocation lookup failed: " +
                "ConfigurationGeoLocationService: Geo location lookup failed.")
    }
}
