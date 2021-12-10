package org.prebid.server.functional.tests.pricefloors

import org.prebid.server.functional.model.config.PriceFloorsFetch
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.mock.services.floorsprovider.PriceFloorRules
import org.prebid.server.functional.model.pricefloors.Rule
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.ExtPrebidFloors
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

import java.time.Instant

import static org.mockserver.model.HttpStatusCode.BAD_REQUEST_400
import static org.prebid.server.functional.model.pricefloors.Country.MULTIPLE
import static org.prebid.server.functional.model.pricefloors.MediaType.BANNER

class PriceFloorsFetchingSpec extends PriceFloorsBaseSpec {

    private static final int maxEnforceFloorsRate = 100

    @PendingFeature
    def "PBS should activate floors feature when price-floors.enabled = true in PBS config"() {
        given: "Pbs with PF configuration"
        def pbsService = pbsServiceFactory.getService(["price-floors.enabled": "true"])

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch and fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should fetch data"
        assert floorsProvider.getRequestCount(bidRequest.site.publisher.id) == 1

        and: "PBS should signal bids"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor
    }

    @PendingFeature
    def "PBS should not activate floors feature when price-floors.enabled = false in PBS config"() {
        given: "Pbs with PF configuration"
        def pbsService = pbsServiceFactory.getService(["price-floors.enabled": "false"])

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch and fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should no fetching, no signaling, no enforcing"
        assert floorsProvider.getRequestCount(bidRequest.site.publisher.id) == 0
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.imp[0].bidFloor
    }

    @PendingFeature
    def "PBS should validate fetch.url from account config"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Flush metrics"
        def metric = "placeholder"
        flushMetrics()

        and: "Default BidRequest"
        def bidRequest = bidRequestWithFloors

        and: "Account with enabled fetch and fetch.url = null in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.fetch.url = null
        }
        accountDao.save(account)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS log should error"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, bidRequest.site.publisher.id)
        assert floorsLogs.size() == 1
        assert floorsLogs == ["placeholder"]

        and: "#metric should be update"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert metrics[metric] == 1

        and: "PBS should fall back to the startup configuration"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == bidRequest.imp[0].bidFloor
    }

    @PendingFeature
    def "PBS should validate fetch.max-age-sec from account config"() {
        given: "PBS with adapter configuration"
        def minMaxAgeSec = PBSUtils.randomNumber
        def defaultAccountConfigSettings = defaultAccountConfigSettings.tap {
            auction.priceFloors.fetch.minMaxAgeSec = minMaxAgeSec
        }
        def pbsService = pbsServiceFactory.getService(
                ["price-floors.enabled"           : "true",
                 "settings.default-account-config": mapper.encode(defaultAccountConfigSettings)])

        and: "Test start time"
        def startTime = Instant.now()

        and: "Flush metrics"
        def metric = "placeholder"
        flushMetrics()

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch, fetch.url, maxAgeSec in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.fetch.maxAgeSec = minMaxAgeSec - 1
        }
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS log should error"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, bidRequest.site.publisher.id)
        assert floorsLogs.size() == 1
        assert floorsLogs == ["placeholder"]

        and: "#metric should be update"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert metrics[metric] == 1
    }

    @PendingFeature
    def "PBS should validate fetch.period-sec from account config"() {
        given: "PBS with adapter configuration"
        def minMaxAgeSec = PBSUtils.randomNumber
        def maxAgeSec = PBSUtils.randomNumber + 10
        def defaultAccountConfigSettings = defaultAccountConfigSettings.tap {
            auction.priceFloors.fetch.minMaxAgeSec = minMaxAgeSec
            auction.priceFloors.fetch.maxAgeSec = maxAgeSec
        }
        def pbsService = pbsServiceFactory.getService(
                ["price-floors.enabled"           : "true",
                 "settings.default-account-config": mapper.encode(defaultAccountConfigSettings)])

        and: "Test start time"
        def startTime = Instant.now()

        and: "Flush metrics"
        def metric = "placeholder"
        flushMetrics()

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch, fetch.url, periodSec in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.fetch = fetchConfig(minMaxAgeSec, maxAgeSec)
        }
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS log should error"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, bidRequest.site.publisher.id)
        assert floorsLogs.size() == 1
        assert floorsLogs == ["placeholder"]

        and: "#metric should be update"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert metrics[metric] == 1

        where:
        fetchConfig << [{ int min, int max -> new PriceFloorsFetch(periodSec: minMaxAgeSec - 1) },
                        { int min, int max -> new PriceFloorsFetch(periodSec: maxAgeSec, maxAgeSec: maxAgeSec - 1) }]
    }

    @PendingFeature
    def "PBS should validate fetch.max-file-size-kb from account config"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Flush metrics"
        def metric = "placeholder"
        flushMetrics()

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch, fetch.url, maxFileSizeKb in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.fetch.maxFileSizeKb = PBSUtils.randomNegativeNumber
        }
        accountDao.save(account)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS log should error"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, bidRequest.site.publisher.id)
        assert floorsLogs.size() == 1
        assert floorsLogs == ["placeholder"]

        and: "#metric should be update"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert metrics[metric] == 1
    }

    @PendingFeature
    def "PBS should validate fetch.max-rules from account config"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Flush metrics"
        def metric = "placeholder"
        flushMetrics()

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch, fetch.url, maxRules in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.fetch.maxRules = PBSUtils.randomNegativeNumber
        }
        accountDao.save(account)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS log should error"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, bidRequest.site.publisher.id)
        assert floorsLogs.size() == 1
        assert floorsLogs == ["placeholder"]

        and: "#metric should be update"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert metrics[metric] == 1
    }

    @PendingFeature
    def "PBS should validate fetch.timeout-ms from account config"() {
        given: "PBS with adapter configuration"
        def minTimeoutMs = PBSUtils.randomNumber
        def maxTimeoutMs = PBSUtils.randomNumber + 10
        def defaultAccountConfigSettings = defaultAccountConfigSettings.tap {
            auction.priceFloors.fetch.timeoutMs = PBSUtils.getRandomNumber(minTimeoutMs, maxTimeoutMs)
            auction.priceFloors.fetch.minTimeoutMs = minTimeoutMs
            auction.priceFloors.fetch.maxTimeoutMs = maxTimeoutMs
        }
        def pbsService = pbsServiceFactory.getService(
                ["price-floors.enabled"           : "true",
                 "settings.default-account-config": mapper.encode(defaultAccountConfigSettings)])

        and: "Test start time"
        def startTime = Instant.now()

        and: "Flush metrics"
        def metric = "placeholder"
        flushMetrics()

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch, fetch.url, timeoutMs in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.fetch = fetchConfig(minTimeoutMs, maxTimeoutMs)
        }
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS log should error"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, bidRequest.site.publisher.id)
        assert floorsLogs.size() == 1
        assert floorsLogs == ["placeholder"]

        and: "#metric should be update"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert metrics[metric] == 1

        where:
        fetchConfig << [{ int min, int max -> new PriceFloorsFetch(timeoutMs: minTimeoutMs - 1) },
                        { int min, int max -> new PriceFloorsFetch(timeoutMs: maxTimeoutMs + 1) }]
    }

    @PendingFeature
    def "PBS should validate fetch.enforce-floors-rate from account config"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Flush metrics"
        def metric = "placeholder"
        flushMetrics()

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch, fetch.url, enforceFloorsRate in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.enforceFloorsRate = enforceFloorsRate
        }
        accountDao.save(account)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS log should error"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, bidRequest.site.publisher.id)
        assert floorsLogs.size() == 1
        assert floorsLogs == ["placeholder"]

        and: "#metric should be update"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert metrics[metric] == 1

        where:
        enforceFloorsRate << [PBSUtils.randomNegativeNumber, maxEnforceFloorsRate + 1]
    }

    @PendingFeature
    def "PBS should continue to hold onto previously fetched rules util they expire when price-floors.fetch.enabled = false in account config"() {
        given: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        when: "PBS processes auction request and cache rules"
        floorsPbsService.sendAuctionRequest(bidRequest)

        and: "Account with disabled fetch in the DB"
        def accountForDisabledFetch = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.fetch.enabled = false
        }
        accountDao.save(accountForDisabledFetch)

        and: "PBS processes auction request and cache rules"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "должен применить старые рулы"
    }

    @PendingFeature
    def "PBS should fetch data from provider when price-floors.fetch.enabled = true in account config"() {
        given: "Default BidRequest with price"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.floors = ExtPrebidFloors.extPrebidFloors
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should fetch data from data provider"
        assert floorsProvider.getRequestCount(bidRequest.site.publisher.id) == 1
    }

    @PendingFeature
    def "PBS should process floors from request when price-floors.fetch.enabled = false in account config"() {
        given: "Default BidRequest"
        def bidRequest = bidRequestWithFloors

        and: "Account with fetch.enabled, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.fetch = fetch
        }
        accountDao.save(account)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should not fetch data from data provider"
        assert floorsProvider.getRequestCount(bidRequest.site.publisher.id) == 0

        and: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].bidFloor == bidRequest.imp[0].bidFloor as Long

        where:
        fetch << [new PriceFloorsFetch(enabled: false, url: fetchUrl), new PriceFloorsFetch(url: fetchUrl)]
    }


    @PendingFeature
    def "PBS should validate rules from request"() {
        given: "Default BidRequest with price"

        when: "PBS processes auction request"

        then: "Response should contain basic fields"
    }

    @PendingFeature
    def "PBS floors validation failure should not reject the entire auction"() {
        given: "Default basic BidRequest with generic bidder"

        when: "PBS processes auction request"

        then: "Response should contain basic fields"
    }

    @PendingFeature
    def "PBS should not replace a previously good schema when new retrieved schema is invalid !!!!"() {
    }

    @PendingFeature
    def "PBS should log error and increase #metric when Floors Provider return status code != 200"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Flush metrics"
        def metric = "price-floors.fetch.failure"
        flushMetrics()

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response  with status code != 200"
        floorsProvider.setResponse(bidRequest.site.publisher.id, BAD_REQUEST_400)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        and: "PBS processes collected metrics request"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()

        then: "#metric should be update"
        assert metrics[metric] == 1

        and: "PBS log should contain request to allowed adapter"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, fetchUrl)
        assert floorsLogs.size() == 1
        assert floorsLogs == ["Failed to fetch price floor from provider for account = $fetchUrl with a reason :" +
                                      " Failed to request for account $fetchUrl, provider respond with status %s" as String]

        and: "Floors validation failure cannot reject the entire auction"
        assert !response.seatbid.isEmpty()
    }

    @PendingFeature
    def "PBS should log error and increase #metric when Floors Provider return invalid json"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Flush metrics"
        def metric = "price-floors.fetch.failure"
        flushMetrics()

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response with invalid json"
        def invalidJson = "{{}}"
        floorsProvider.setResponse(bidRequest.site.publisher.id, invalidJson)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        and: "PBS processes collected metrics request"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()

        then: "#metric should be update"
        assert metrics[metric] == 1

        and: "PBS log should contain request to allowed adapter"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, fetchUrl)
        assert floorsLogs.size() == 1
        assert floorsLogs ==
                ["Failed to fetch price floor from provider for account = $fetchUrl with a reason : placeholder" as String]

        and: "Floors validation failure cannot reject the entire auction"
        assert !response.seatbid.isEmpty()
    }

    @PendingFeature
    def "PBS should log error and increase #metric when Floors Provider return empty response body"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Flush metrics"
        def metric = "price-floors.fetch.failure"
        flushMetrics()

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch, fetch.url in the DB"
        def accountId = bidRequest.site.publisher.id
        def account = getAccountWithEnabledFetch(accountId)
        accountDao.save(account)

        and: "Set Floors Provider response with invalid json"
        floorsProvider.setResponse(accountId, "")

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        and: "PBS processes collected metrics request"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()

        then: "#metric should be update"
        assert metrics[metric] == 1

        and: "PBS log should contain request to allowed adapter"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, fetchUrl)
        assert floorsLogs.size() == 1
        assert floorsLogs == ["Failed to fetch price floor from provider for account = $accountId with a reason : " +
                                      "Failed to parse price floor response for account $accountId, response body " +
                                      "can not be empty" as String]

        and: "Floors validation failure cannot reject the entire auction"
        assert !response.seatbid.isEmpty()
    }

    @PendingFeature
    def "PBS should log error and increase #metric when Floors Provider response doesn't contain model"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Flush metrics"
        def metric = "price-floors.fetch.failure"
        flushMetrics()

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountId = bidRequest.site.publisher.id
        def account = getAccountWithEnabledFetch(accountId)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups = null
        }
        floorsProvider.setResponse(accountId, floorsResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        and: "PBS processes collected metrics request"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()

        then: "#metric should be update"
        assert metrics[metric] == 1

        and: "PBS log should contain request to allowed adapter"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, fetchUrl)
        assert floorsLogs.size() == 1
        assert floorsLogs ==
                ["Failed to fetch price floor from provider for account = $accountId with a reason : placeholer" as String]

        and: "Floors validation failure cannot reject the entire auction"
        assert !response.seatbid.isEmpty()
    }

    @PendingFeature
    def "PBS should log error and increase #metric when Floors Provider response doesn't contain rule"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Flush metrics"
        def metric = "price-floors.fetch.failure"
        flushMetrics()

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountId = bidRequest.site.publisher.id
        def account = getAccountWithEnabledFetch(accountId)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = null
        }
        floorsProvider.setResponse(accountId, floorsResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        and: "PBS processes collected metrics request"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()

        then: "#metric should be update"
        assert metrics[metric] == 1

        and: "PBS log should contain request to allowed adapter"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, fetchUrl)
        assert floorsLogs.size() == 1
        assert floorsLogs ==
                ["Failed to fetch price floor from provider for account = $accountId with a reason : placeholer" as String]

        and: "Floors validation failure cannot reject the entire auction"
        assert !response.seatbid.isEmpty()
    }

    @PendingFeature
    def "PBS should log error and increase #metric when Floors Provider response has more than fetch.max-rules"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Flush metrics"
        def metric = "price-floors.fetch.failure"
        flushMetrics()

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountId = bidRequest.site.publisher.id
        def account = getAccountWithEnabledFetch(accountId).tap {
            config.auction.priceFloors.fetch.maxRules = 1
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values.put(new Rule(mediaType: BANNER, country: MULTIPLE).rule, 0.7)
        }
        floorsProvider.setResponse(accountId, floorsResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        and: "PBS processes collected metrics request"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()

        then: "#metric should be update"
        assert metrics[metric] == 1

        and: "PBS log should contain request to allowed adapter"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, fetchUrl)
        assert floorsLogs.size() == 1
        assert floorsLogs ==
                ["Failed to fetch price floor from provider for account = $accountId with a reason : placeholer" as String]

        and: "Floors validation failure cannot reject the entire auction"
        assert !response.seatbid.isEmpty()
    }

    @PendingFeature
    def "PBS should log error and increase #metric when fetch request exceeds fetch.timeout-ms"() {
        given: "PBS with adapter configuration"
        def defaultAccountConfigSettings = defaultAccountConfigSettings.tap {
            auction.priceFloors.fetch.minTimeoutMs = 1
            auction.priceFloors.fetch.timeoutMs = 1
        }
        def pbsService = pbsServiceFactory.getService(
                ["price-floors.enabled"           : "true",
                 "settings.default-account-config": mapper.encode(defaultAccountConfigSettings)])

        and: "Test start time"
        def startTime = Instant.now()

        and: "Flush metrics"
        def metric = "price-floors.fetch.failure"
        flushMetrics(pbsService)

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountId = bidRequest.site.publisher.id
        def account = getAccountWithEnabledFetch(accountId)
        accountDao.save(account)

        and: "Set Floors Provider response"
        floorsProvider.setResponseWithTimeout(accountId)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        and: "PBS processes collected metrics request"
        def metrics = pbsService.sendCollectedMetricsRequest()

        then: "#metric should be update"
        assert metrics[metric] == 1

        and: "PBS log should contain request to allowed adapter"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, fetchUrl)
        assert floorsLogs.size() == 1
        assert floorsLogs ==
                ["Failed to fetch price floor from provider for account = $accountId with a reason : placeholer" as String]

        and: "Floors validation failure cannot reject the entire auction"
        assert !response.seatbid.isEmpty()
    }

    @PendingFeature
    def "PBS should log error and increase #metric when Floors Provider's response size is more than fetch.max-file-size-kb"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Flush metrics"
        def metric = "price-floors.fetch.failure"
        flushMetrics()

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountId = bidRequest.site.publisher.id
        def maxSize = PBSUtils.randomNumber
        def account = getAccountWithEnabledFetch(accountId).tap {
            config.auction.priceFloors.fetch.maxFileSizeKb = maxSize
        }
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules
        def responseSize = maxSize + 1
        floorsProvider.setResponse(accountId, floorsResponse, ["Content-Length": responseSize as String])

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        and: "PBS processes collected metrics request"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()

        then: "#metric should be update"
        assert metrics[metric] == 1

        and: "PBS log should contain request to allowed adapter"
        def logs = floorsPbsService.getLogsByTime(startTime)
        def floorsLogs = getLogsByText(logs, fetchUrl)
        assert floorsLogs.size() == 1
        assert floorsLogs ==
                ["Failed to fetch price floor from provider for account = $accountId with a reason : " +
                         "Response size $responseSize exceeded $maxSize bytes limit" as String]

        and: "Floors validation failure cannot reject the entire auction"
        assert !response.seatbid.isEmpty()
    }

    @PendingFeature
    def "PBS should prefer data from stored request when request doesn't contain floors data"() {
        given: "Default BidRequest with price"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomNumber)
        }

        and: "Default stored request with timeout"
        def storedRequestModel = bidRequestWithFloors

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(bidRequest, storedRequestModel)
        storedRequestDao.save(storedRequest)

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.fetch.enabled = false
        }
        accountDao.save(account)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            imp[0].bidFloor == storedRequestModel.ext.prebid.floors.data.modelGroups[0].values[rule]
            imp[0].bidFloorCur == storedRequestModel.ext.prebid.floors.data.modelGroups[0].currency

            imp[0].ext.prebid.floors.floorRule ==
                    storedRequestModel.ext.prebid.floors.data.modelGroups[0].values.keySet()[0]
            imp[0].ext.prebid.floors.floorRuleValue ==
                    storedRequestModel.ext.prebid.floors.data.modelGroups[0].values[rule]
            imp[0].ext.prebid.floors.floorValue == storedRequestModel.ext.prebid.floors.data.modelGroups[0].values[rule]

            ext.prebid.floors == storedRequestModel.ext.prebid.floors
        }
    }

    @PendingFeature
    def "PBS should prefer data from request when fetch is disabled in account config"() {
        given: "Default BidRequest with price"
        def bidRequest = bidRequestWithFloors

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.fetch.enabled = false
        }
        accountDao.save(account)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            imp[0].bidFloor == bidRequest.ext.prebid.floors.data.modelGroups[0].values[rule]
            imp[0].bidFloorCur == bidRequest.ext.prebid.floors.data.modelGroups[0].currency

            imp[0].ext.prebid.floors.floorRule == bidRequest.ext.prebid.floors.data.modelGroups[0].values.keySet()[0]
            imp[0].ext.prebid.floors.floorRuleValue == bidRequest.ext.prebid.floors.data.modelGroups[0].values[rule]
            imp[0].ext.prebid.floors.floorValue == bidRequest.ext.prebid.floors.data.modelGroups[0].values[rule]

            ext.prebid.floors == bidRequest.ext.prebid.floors
        }
    }

    @PendingFeature
    def "PBS should prefer data from floors provider when floors data is defined in both request and stored request"() {
        given: "Default BidRequest with price"
        def bidRequest = bidRequestWithFloors.tap {
            ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomNumber)
        }

        and: "Default stored request with timeout"
        def storedRequestModel = bidRequestWithFloors

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(bidRequest, storedRequestModel)
        storedRequestDao.save(storedRequest)

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): randomFloorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            imp[0].bidFloor == bidRequest.ext.prebid.floors.data.modelGroups[0].values[rule]
            imp[0].bidFloorCur == bidRequest.ext.prebid.floors.data.modelGroups[0].currency

            imp[0].ext.prebid.floors.floorRule == bidRequest.ext.prebid.floors.data.modelGroups[0].values.keySet()[0]
            imp[0].ext.prebid.floors.floorRuleValue == bidRequest.ext.prebid.floors.data.modelGroups[0].values[rule]
            imp[0].ext.prebid.floors.floorValue == bidRequest.ext.prebid.floors.data.modelGroups[0].values[rule]

            ext.prebid.floors == bidRequest.ext.prebid.floors
        }
    }

    @PendingFeature
    def "PBS should periodically fetch floor rules when previous response from floors provider is #description"() {
        given: "PBS with adapter configuration"
        def defaultAccountConfigSettings = defaultAccountConfigSettings.tap {
            auction.priceFloors.fetch.minMaxAgeSec = 5
            auction.priceFloors.fetch.maxAgeSec = 5
        }
        def pbsService = pbsServiceFactory.getService(
                ["price-floors.enabled"           : "true",
                 "settings.default-account-config": mapper.encode(defaultAccountConfigSettings)])

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider #description response"
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should cache data from data provider"
        assert floorsProvider.getRequestCount(bidRequest.site.publisher.id) == 1

        and: "PBS should periodically fetch data from data provider"
        assert floorsProvider.checkRequestCount(bidRequest.site.publisher.id, 2, 5000, 1000)

        where:
        description | floorsResponse
        "valid"     | PriceFloorRules.priceFloorRules
        "invalid"   | PriceFloorRules.priceFloorRules.tap { data.modelGroups = null }
    }

    @PendingFeature
    def "PBS should continue to hold onto previously fetched rules when fetch.enabled = false in account config"() {
        given: "PBS with adapter configuration"
        def defaultAccountConfigSettings = defaultAccountConfigSettings.tap {
            auction.priceFloors.fetch.maxAgeSec = 86400
        }
        def pbsService = pbsServiceFactory.getService(
                ["price-floors.enabled"           : "true",
                 "settings.default-account-config": mapper.encode(defaultAccountConfigSettings)])

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account with enabled fetch in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider #description response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): randomFloorValue]
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        and: "Account with disabled fetch in the DB"
        account.tap {
            config.auction.priceFloors.fetch.enabled = false
        }
        accountDao.save(account)

        and: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should cache data from data provider"
        assert bidder.getRequestCount(bidRequest.site.publisher.id) == 2
        def bidderRequest = bidder.getBidderRequests(bidRequest.site.publisher.id).last()

        verifyAll(bidderRequest) {
            imp[0].bidFloor == floorsResponse.data.modelGroups[0].values[rule]
            imp[0].bidFloorCur == floorsResponse.data.modelGroups[0].currency
            imp[0].ext.prebid.floors.floorRule == floorsResponse.data.modelGroups[0].values.keySet()[0]
            imp[0].ext.prebid.floors.floorRuleValue == floorsResponse.data.modelGroups[0].values[rule]
            imp[0].ext.prebid.floors.floorValue == floorsResponse.data.modelGroups[0].values[rule]
            ext.prebid.floors.enabled
            ext.prebid.floors.floorMin == floorsResponse.floorMin
            ext.prebid.floors.floorMin == floorsResponse.floorMin
            ext.prebid.floors.floorProvider == floorsResponse.floorProvider
            ext.prebid.floors.enforcement.enforcePbs == floorsResponse.enforcement.enforcePbs
            ext.prebid.floors.enforcement.floorDeals == floorsResponse.enforcement.floorDeals
            ext.prebid.floors.enforcement.bidAdjustment == floorsResponse.enforcement.bidAdjustment
            ext.prebid.floors.enforcement.enforceRate == bidRequest.ext.prebid.floors.enforcement.enforceRate
            ext.prebid.floors.skipRate == floorsResponse.skipRate
            ext.prebid.floors.data == floorsResponse.data
        }
    }


}
