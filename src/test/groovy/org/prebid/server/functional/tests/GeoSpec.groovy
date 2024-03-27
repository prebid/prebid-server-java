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
import static org.prebid.server.functional.model.pricefloors.Country.CAN
import static org.prebid.server.functional.model.pricefloors.Country.USA
import static org.prebid.server.functional.model.request.auction.PublicCountryIp.CAN_IP
import static org.prebid.server.functional.model.request.auction.PublicCountryIp.USA_IP
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE
import static org.prebid.server.functional.util.privacy.model.State.ALABAMA
import static org.prebid.server.functional.util.privacy.model.State.ONTARIO
import static org.prebid.server.functional.util.privacy.model.State.QUEBEC

class GeoSpec extends BaseSpec {

    private static final String GEO_LOCATION_REQUESTS = "geolocation_requests"
    private static final String GEO_LOCATION_FAIL = "geolocation_fail"
    private static final String GEO_LOCATION_SUCCESSFUL = "geolocation_successful"
    private static final Map<String, String> GEO_LOCATION = ["geolocation.type"                               : "configuration",
                                                             "geolocation.configurations.[0].address-pattern" : USA_IP.v4,
                                                             "geolocation.configurations.[0].geo-info.country": USA.ISOAlpha2,
                                                             "geolocation.configurations.[0].geo-info.region" : ALABAMA.abbreviation,
                                                             "geolocation.configurations.[1].address-pattern" : CAN_IP.v4,
                                                             "geolocation.configurations.[1].geo-info.country": CAN.ISOAlpha2,
                                                             "geolocation.configurations.[1].geo-info.region" : QUEBEC.abbreviation]

    def "PBS should populate geo with country and region and take precedence from device.id when geo location enabled in host and account config and ip specified in both places"() {
        given: "PBS service with geolocation and default account configs"
        def config = AccountConfig.defaultAccountConfig.tap {
            settings = new AccountSetting(geoLookup: defaultAccountGeoLookup)
        }
        def defaultPbsService = pbsServiceFactory.getService(
                ["settings.default-account-config": encode(config),
                 "geolocation.enabled"            : "true"] + GEO_LOCATION)

        and: "Default bid request with device and geo data"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(
                    ip: USA_IP.v4,
                    ipv6: USA_IP.v6,
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
        def account = new Account(status: ACTIVE, uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest, ["X-Forwarded-For": CAN_IP.v4])

        then: "Bidder request should contain country and region"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device.geo.country == USA
        assert bidderRequests.device.geo.region == ALABAMA.abbreviation

        and: "Metrics processed across activities should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics[GEO_LOCATION_REQUESTS] == 1
        assert metrics[GEO_LOCATION_SUCCESSFUL] == 1
        assert !metrics[GEO_LOCATION_FAIL]

        where:
        defaultAccountGeoLookup | accountGeoLookup
        false                   | true
        true                    | true
        true                    | null
    }

    def "PBS should populate geo with country and region when geo location enabled in host and account config and ip present in device.id"() {
        given: "PBS service with geolocation and default account configs"
        def config = AccountConfig.defaultAccountConfig.tap {
            settings = new AccountSetting(geoLookup: defaultAccountGeoLookup)
        }
        def defaultPbsService = pbsServiceFactory.getService(
                ["settings.default-account-config": encode(config),
                 "geolocation.enabled"            : "true"] + GEO_LOCATION)

        and: "Default bid request with device and geo data"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(
                    ip: USA_IP.v4,
                    ipv6: USA_IP.v6,
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
        def account = new Account(status: ACTIVE, uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain country and region"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device.geo.country == USA
        assert bidderRequests.device.geo.region == ALABAMA.abbreviation

        and: "Metrics processed across activities should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics[GEO_LOCATION_REQUESTS] == 1
        assert metrics[GEO_LOCATION_SUCCESSFUL] == 1
        assert !metrics[GEO_LOCATION_FAIL]

        where:
        defaultAccountGeoLookup | accountGeoLookup
        false                   | true
        true                    | true
        true                    | null
    }

    def "PBS should populate geo with country and region when geo location enabled in host and account config and ip present in header"() {
        given: "PBS service with geolocation and default account configs"
        def config = AccountConfig.defaultAccountConfig.tap {
            settings = new AccountSetting(geoLookup: defaultAccountGeoLookup)
        }
        def defaultPbsService = pbsServiceFactory.getService(
                ["settings.default-account-config": encode(config),
                 "geolocation.enabled"            : "true"] + GEO_LOCATION)

        and: "Default bid request with device and geo data"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(
                    ip: null,
                    ipv6: null,
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
        def account = new Account(status: ACTIVE, uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest, ["X-Forwarded-For": USA_IP.v4])

        then: "Bidder request should contain country and region"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device.geo.country == USA
        assert bidderRequests.device.geo.region == ALABAMA.abbreviation

        and: "Metrics processed across activities should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics[GEO_LOCATION_REQUESTS] == 1
        assert metrics[GEO_LOCATION_SUCCESSFUL] == 1
        assert !metrics[GEO_LOCATION_FAIL]

        where:
        defaultAccountGeoLookup | accountGeoLookup
        false                   | true
        true                    | true
        true                    | null
    }

    def "PBS shouldn't populate geo with country and region when geo location disable in host and account config enabled and ip present in device.ip"() {
        given: "PBS service with geolocation and default account configs"
        def config = AccountConfig.defaultAccountConfig.tap {
            settings = new AccountSetting(geoLookup: defaultAccountGeoLookupConfig)
        }
        def defaultPbsService = pbsServiceFactory.getService(GEO_LOCATION +
                ["settings.default-account-config": encode(config),
                 "geolocation.enabled"            : hostGeolocation])

        and: "Default bid request with device and geo data"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(
                    ip: USA_IP.v4,
                    ipv6: USA_IP.v6,
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
        def account = new Account(status: ACTIVE, uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain country and region"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequests.device.geo.country
        assert !bidderRequests.device.geo.region

        and: "Metrics processed across geo location shouldn't be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert !metrics[GEO_LOCATION_REQUESTS]
        assert !metrics[GEO_LOCATION_SUCCESSFUL]
        assert !metrics[GEO_LOCATION_FAIL]

        where:
        defaultAccountGeoLookupConfig | hostGeolocation | accountGeoLookup
        true                          | "true"          | false
        true                          | "false"         | true
        false                         | "false"         | false
        false                         | "true"          | false
    }

    def "PBS shouldn't populate geo with country and region when geo location disable in host and account config enabled and ip present in header"() {
        given: "PBS service with geolocation and default account configs"
        def config = AccountConfig.defaultAccountConfig.tap {
            settings = new AccountSetting(geoLookup: defaultAccountGeoLookupConfig)
        }
        def defaultPbsService = pbsServiceFactory.getService(GEO_LOCATION +
                ["settings.default-account-config": encode(config),
                 "geolocation.enabled"            : hostGeolocation])

        and: "Default bid request with device and geo data"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(
                    ip: null,
                    ipv6: null,
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
        def account = new Account(status: ACTIVE, uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest, ["X-Forwarded-For": USA_IP.v4])

        then: "Bidder request shouldn't contain country and region"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequests.device.geo.country
        assert !bidderRequests.device.geo.region

        and: "Metrics processed across geo location shouldn't be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert !metrics[GEO_LOCATION_REQUESTS]
        assert !metrics[GEO_LOCATION_SUCCESSFUL]
        assert !metrics[GEO_LOCATION_FAIL]

        where:
        defaultAccountGeoLookupConfig | hostGeolocation | accountGeoLookup
        true                          | "true"          | false
        true                          | "false"         | true
        false                         | "false"         | false
        false                         | "true"          | false
    }

    def "PBS shouldn't populate geo with country, region and emit error in log and metric when geo look up failed and ip present in device.id"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "PBS service with geolocation"
        def defaultPbsService = pbsServiceFactory.getService(GEO_LOCATION +
                ["geolocation.configurations.[0].address-pattern": PBSUtils.randomNumber as String,
                 "geolocation.enabled"                           : "true"])

        and: "Default bid request with device and geo data"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(
                    ip: USA_IP.v4,
                    ipv6: USA_IP.v6,
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
        def account = new Account(status: ACTIVE, uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain country and region"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequests.device.geo.country
        assert !bidderRequests.device.geo.region

        and: "Metrics processed across geo location should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics[GEO_LOCATION_REQUESTS] == 1
        assert metrics[GEO_LOCATION_FAIL] == 1
        assert !metrics[GEO_LOCATION_SUCCESSFUL]

        and: "PBs should emit geo failed logs"
        def logs = defaultPbsService.getLogsByTime(startTime)
        def getLocation = getLogsByText(logs, "GeoLocationServiceWrapper")
        assert getLocation.size() == 1
        assert getLocation[0].contains("Geolocation lookup failed: " +
                "ConfigurationGeoLocationService: Geo location lookup failed.")
    }

    def "PBS shouldn't populate geo with country, region and emit error in log and metric when geo look up failed and ip present in header"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "PBS service with geolocation"
        def defaultPbsService = pbsServiceFactory.getService(GEO_LOCATION +
                ["geolocation.configurations.[0].address-pattern": PBSUtils.randomNumber as String,
                 "geolocation.enabled"                           : "true"])

        and: "Default bid request with device and geo data"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(
                    ip: null,
                    ipv6: null,
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
        def account = new Account(status: ACTIVE, uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest, ["X-Forwarded-For": USA_IP.v4])

        then: "Bidder request shouldn't contain country and region"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequests.device.geo.country
        assert !bidderRequests.device.geo.region

        and: "Metrics processed across geo location should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics[GEO_LOCATION_REQUESTS] == 1
        assert metrics[GEO_LOCATION_FAIL] == 1
        assert !metrics[GEO_LOCATION_SUCCESSFUL]

        and: "PBs should emit geo failed logs"
        def logs = defaultPbsService.getLogsByTime(startTime)
        def getLocation = getLogsByText(logs, "GeoLocationServiceWrapper")
        assert getLocation.size() == 1
        assert getLocation[0].contains("Geolocation lookup failed: " +
                "ConfigurationGeoLocationService: Geo location lookup failed.")
    }

    def "PBS shouldn't populate country and region via geo when geo enabled in account and country and region specified in request and ip present in device.id"() {
        given: "PBS service with geolocation"
        def defaultPbsService = pbsServiceFactory.getService(
                ["geolocation.enabled": "true"] + GEO_LOCATION)

        and: "Default bid request with device and geo data"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(
                    ip: USA_IP.v4,
                    ipv6: USA_IP.v6,
                    geo: new Geo(
                            country: CAN,
                            region: ONTARIO.abbreviation,
                            lat: PBSUtils.getRandomDecimal(0, 90),
                            lon: PBSUtils.getRandomDecimal(0, 90)))
            ext.prebid.trace = VERBOSE
        }

        and: "Account in the DB"
        def accountConfig = new AccountConfig(
                auction: new AccountAuctionConfig(debugAllow: true),
                settings: new AccountSetting(geoLookup: true))
        def account = new Account(status: ACTIVE, uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain country and region"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device.geo.country == CAN
        assert bidderRequests.device.geo.region == ONTARIO.abbreviation

        and: "Metrics processed across activities shouldn't be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert !metrics[GEO_LOCATION_REQUESTS]
        assert !metrics[GEO_LOCATION_SUCCESSFUL]
        assert !metrics[GEO_LOCATION_FAIL]
    }

    def "PBS shouldn't populate country and region via geo when geo enabled in account and country and region specified in request and ip present in header"() {
        given: "PBS service with geolocation"
        def defaultPbsService = pbsServiceFactory.getService(
                ["geolocation.enabled": "true"] + GEO_LOCATION)

        and: "Default bid request with device and geo data"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(
                    ip: null,
                    ipv6: null,
                    geo: new Geo(
                            country: CAN,
                            region: ONTARIO.abbreviation,
                            lat: PBSUtils.getRandomDecimal(0, 90),
                            lon: PBSUtils.getRandomDecimal(0, 90)))
            ext.prebid.trace = VERBOSE
        }

        and: "Account in the DB"
        def accountConfig = new AccountConfig(
                auction: new AccountAuctionConfig(debugAllow: true),
                settings: new AccountSetting(geoLookup: true))
        def account = new Account(status: ACTIVE, uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest, ["X-Forwarded-For": USA_IP.v4])

        then: "Bidder request should contain country and region"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device.geo.country == CAN
        assert bidderRequests.device.geo.region == ONTARIO.abbreviation

        and: "Metrics processed across activities shouldn't be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert !metrics[GEO_LOCATION_REQUESTS]
        assert !metrics[GEO_LOCATION_SUCCESSFUL]
        assert !metrics[GEO_LOCATION_FAIL]
    }
}
