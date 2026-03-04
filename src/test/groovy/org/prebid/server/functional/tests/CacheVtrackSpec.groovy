package org.prebid.server.functional.tests

import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountCacheConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountEventsConfig
import org.prebid.server.functional.model.config.AccountVtrackConfig
import org.prebid.server.functional.model.config.Endpoint
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.vtrack.VtrackRequest
import org.prebid.server.functional.model.request.vtrack.xml.Vast
import org.prebid.server.functional.model.response.vtrack.TransferValue
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class CacheVtrackSpec extends BaseSpec {

    private static final String ACCOUNT_VTRACK_XML_CREATIVE_SIZE_METRIC = "account.%s.prebid_cache.vtrack.creative_size.xml"
    private static final String ACCOUNT_VTRACK_CREATIVE_TTL_XML_METRIC = "account.%s.prebid_cache.vtrack.creative_ttl.xml"
    private static final String ACCOUNT_VTRACK_WRITE_ERR_METRIC = "account.%s.prebid_cache.vtrack.write.err"
    private static final String ACCOUNT_VTRACK_WRITE_OK_METRIC = "account.%s.prebid_cache.vtrack.write.ok"

    private static final String VTRACK_XML_CREATIVE_SIZE_METRIC = "prebid_cache.vtrack.creative_size.xml"
    private static final String VTRACK_XML_CREATIVE_TTL_METRIC = "prebid_cache.vtrack.creative_ttl.xml"
    private static final String VTRACK_WRITE_OK_METRIC = "prebid_cache.vtrack.write.ok"
    private static final String VTRACK_WRITE_ERROR_METRIC = "prebid_cache.vtrack.write.err"
    private static final String VTRACK_READ_OK_METRIC = "prebid_cache.vtrack.read.ok"
    private static final String VTRACK_READ_ERROR_METRIC = "prebid_cache.vtrack.read.err"

    private static final String CACHE_ENDPOINT = "/cache"
    private static final String CACHE_PATH = "/${PBSUtils.randomString}".toString()
    private static final String CACHE_HOST = "${PBSUtils.randomString}:${PBSUtils.getRandomNumber(0, 65535)}".toString()
    private static final String HTTP_SCHEME = 'http'

    private static final Map<String, String> INVALID_PREBID_CACHE_CONFIG = ["cache.path"  : CACHE_PATH,
                                                                            "cache.scheme": HTTP_SCHEME,
                                                                            "cache.host"  : CACHE_HOST]
    private static final Map<String, String> VALID_INTERNAL_CACHE = ["cache.internal.scheme": HTTP_SCHEME,
                                                                     "cache.internal.host"  : "$networkServiceContainer.hostAndPort".toString(),
                                                                     "cache.internal.path"  : CACHE_ENDPOINT]
    private static PrebidServerService pbsServiceWithInternalCache

    def setupSpec() {
        pbsServiceWithInternalCache = pbsServiceFactory.getService(VALID_INTERNAL_CACHE + INVALID_PREBID_CACHE_CONFIG)
    }

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(VALID_INTERNAL_CACHE + INVALID_PREBID_CACHE_CONFIG)
    }

    void cleanup() {
        prebidCache.reset()
    }

    def "PBS should update prebid_cache.creative_size.xml metric and adding tracking xml when xml creative contain #wrapper and impression are valid xml value"() {
        given: "Current value of metric prebid_cache.vtrack.write.ok"
        def initialOkVTrackValue = getCurrentMetricValue(defaultPbsService, VTRACK_WRITE_OK_METRIC)

        and: "Create and save enabled events config in account"
        def accountId = PBSUtils.randomNumber.toString()
        def account = new Account().tap {
            uuid = accountId
            config = new AccountConfig().tap {
                auction = new AccountAuctionConfig(events: new AccountEventsConfig(enabled: true))
            }
        }
        accountDao.save(account)

        and: "Set up prebid cache"
        prebidCache.setResponse()

        and: "Vtrack request with custom tags"
        def payload = PBSUtils.randomString
        def creative = "<VAST version=\"3.0\"><Ad><${wrapper}><AdSystem>prebid.org wrapper</AdSystem>" +
                "<VASTAdTagURI>&lt;![CDATA[//${payload}]]&gt;</VASTAdTagURI>" +
                "<${impression}> &lt;![CDATA[ ]]&gt; </${impression}><Creatives></Creatives></${wrapper}></Ad></VAST>"
        def request = VtrackRequest.getDefaultVtrackRequest(creative)

        and: "Flush metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes vtrack request"
        defaultPbsService.sendPostVtrackRequest(request, accountId)

        then: "Vast xml is modified"
        def prebidCacheRequest = prebidCache.getXmlRecordedRequestsBody(payload)
        assert prebidCacheRequest.size() == 1
        assert prebidCacheRequest[0].contains("${Endpoint.EVENT}?t=imp&b=${request.puts[0].bidid}&a=$accountId&bidder=${request.puts[0].bidder}")

        and: "prebid_cache.creative_size.xml metric should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        def ttlSeconds = request.puts[0].ttlseconds
        assert metrics[VTRACK_WRITE_OK_METRIC] == initialOkVTrackValue + 1
        assert metrics[VTRACK_XML_CREATIVE_TTL_METRIC] == ttlSeconds

        and: "account.<account-id>.prebid_cache.vtrack.creative_size.xml should be updated"
        assert metrics[ACCOUNT_VTRACK_WRITE_OK_METRIC.formatted(accountId) as String] == 1
        assert metrics[ACCOUNT_VTRACK_CREATIVE_TTL_XML_METRIC.formatted(accountId) as String] == ttlSeconds

        where:
        wrapper                                     | impression
        " wrapper "                                 | " impression "
        PBSUtils.getRandomCase(" wrapper ")         | PBSUtils.getRandomCase(" impression ")
        "  wraPPer ${PBSUtils.getRandomString()}  " | "  imPreSSion ${PBSUtils.getRandomString()}"
        "    inLine    "                            | " ImpreSSion $PBSUtils.randomNumber"
        PBSUtils.getRandomCase(" inline ")          | " ${PBSUtils.getRandomCase(" impression ")} $PBSUtils.randomNumber "
        "  inline ${PBSUtils.getRandomString()}  "  | "   ImpreSSion    "
    }

    def "PBS should update prebid_cache.creative_size.xml metric when xml creative is received"() {
        given: "Current value of metric prebid_cache.requests.ok"
        def initialValue = getCurrentMetricValue(defaultPbsService, VTRACK_WRITE_OK_METRIC)

        and: "Cache set up response"
        prebidCache.setResponse()

        and: "Default VtrackRequest"
        def accountId = PBSUtils.randomNumber.toString()
        def creative = encodeXml(Vast.getDefaultVastModel(PBSUtils.randomString))
        def request = VtrackRequest.getDefaultVtrackRequest(creative)

        and: "Flush metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes vtrack request"
        defaultPbsService.sendPostVtrackRequest(request, accountId)

        then: "prebid_cache.vtrack.creative_size.xml metric should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        def creativeSize = creative.bytes.length
        assert metrics[VTRACK_WRITE_OK_METRIC] == initialValue + 1

        and: "account.<account-id>.prebid_cache.creative_size.xml should be updated"
        assert metrics[ACCOUNT_VTRACK_WRITE_OK_METRIC.formatted(accountId)] == 1
        assert metrics[ACCOUNT_VTRACK_XML_CREATIVE_SIZE_METRIC.formatted(accountId)] == creativeSize
    }

    def "PBS should failed VTrack request when sending request without account"() {
        given: "Default VtrackRequest"
        def creative = encodeXml(Vast.getDefaultVastModel(PBSUtils.randomString))
        def request = VtrackRequest.getDefaultVtrackRequest(creative)

        and: "Flush metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes vtrack request"
        defaultPbsService.sendPostVtrackRequest(request, null)

        then: "Request should fail with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == BAD_REQUEST.code()
        assert exception.responseBody == "Account 'a' is required query parameter and can't be empty"
    }

    def "PBS shouldn't use negative value in tllSecond when account vtrack ttl is #accountTtl and request ttl second is #requestedTtl"() {
        given: "Default VtrackRequest"
        def creative = encodeXml(Vast.getDefaultVastModel(PBSUtils.randomString))
        def request = VtrackRequest.getDefaultVtrackRequest(creative).tap {
            puts[0].ttlseconds = requestedTtl
        }

        and: "Cache set up response"
        prebidCache.setResponse()

        and: "Create and save vtrack in account"
        def accountId = PBSUtils.randomNumber.toString()
        def account = new Account().tap {
            it.uuid = accountId
            it.config = new AccountConfig().tap {
                it.vtrack = new AccountVtrackConfig(ttl: accountTtl)
            }
        }
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes vtrack request"
        defaultPbsService.sendPostVtrackRequest(request, accountId)

        then: "Pbs should emit creative_ttl.xml with lowest value"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics[ACCOUNT_VTRACK_CREATIVE_TTL_XML_METRIC.formatted(accountId)]
                == [requestedTtl, accountTtl].findAll { it -> it > 0 }.min()
        where:
        requestedTtl                                            | accountTtl
        PBSUtils.getRandomNumber(300, 1500) as Integer          | PBSUtils.getRandomNegativeNumber(-1500, 300) as Integer
        PBSUtils.getRandomNegativeNumber(-1500, 300) as Integer | PBSUtils.getRandomNumber(300, 1500) as Integer
        PBSUtils.getRandomNegativeNumber(-1500, 300) as Integer | PBSUtils.getRandomNegativeNumber(-1500, 300) as Integer
    }

    def "PBS should use lowest tllSecond when account vtrack ttl is #accountTtl and request ttl second is #requestedTtl"() {
        given: "Default VtrackRequest"
        def creative = encodeXml(Vast.getDefaultVastModel(PBSUtils.randomString))
        def request = VtrackRequest.getDefaultVtrackRequest(creative).tap {
            puts[0].ttlseconds = requestedTtl
        }

        and: "Cache set up response"
        prebidCache.setResponse()

        and: "Create and save vtrack in account"
        def accountId = PBSUtils.randomNumber.toString()
        def account = new Account().tap {
            it.uuid = accountId
            it.config = new AccountConfig().tap {
                it.vtrack = new AccountVtrackConfig(ttl: accountTtl)
            }
        }
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes vtrack request"
        defaultPbsService.sendPostVtrackRequest(request, accountId)

        then: "Pbs should emit creative_ttl.xml with lowest value"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics[ACCOUNT_VTRACK_CREATIVE_TTL_XML_METRIC.formatted(accountId)] == [requestedTtl, accountTtl].min()

        where:
        requestedTtl                                   | accountTtl
        null                                           | null
        null                                           | PBSUtils.getRandomNumber(300, 1500) as Integer
        PBSUtils.getRandomNumber(300, 1500) as Integer | null
        PBSUtils.getRandomNumber(300, 1500) as Integer | PBSUtils.getRandomNumber(300, 1500) as Integer
    }

    def "PBS should proceed request when account ttl and request ttl second are empty"() {
        given: "Default VtrackRequest"
        def creative = encodeXml(Vast.getDefaultVastModel(PBSUtils.randomString))
        def request = VtrackRequest.getDefaultVtrackRequest(creative).tap {
            puts[0].ttlseconds = null
        }

        and: "Cache set up response"
        prebidCache.setResponse()

        and: "Create and save vtrack in account"
        def accountId = PBSUtils.randomNumber.toString()
        def account = new Account().tap {
            it.uuid = accountId
            it.config = new AccountConfig().tap {
                it.vtrack = new AccountVtrackConfig(ttl: null)
            }
        }
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes vtrack request"
        defaultPbsService.sendPostVtrackRequest(request, accountId)

        then: "Pbs shouldn't emit creative_ttl.xml"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert !metrics[ACCOUNT_VTRACK_CREATIVE_TTL_XML_METRIC.formatted(accountId)]
    }

    def "PBS should return 400 status code when get vtrack request without uuid"() {
        when: "PBS processes get vtrack request"
        defaultPbsService.sendGetVtrackRequest(["uuid": null])

        then: "Request should fail with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == BAD_REQUEST.code()
        assert exception.responseBody == "'uuid' is a required query parameter and can't be empty"
    }

    def "PBS should return 200 status code when get vtrack request contain uuid"() {
        given: "Clean up and set up successful response"
        def responseBody = TransferValue.getTransferValue()
        prebidCache.setGetResponse(responseBody)

        when: "PBS processes get vtrack request"
        def response = defaultPbsService.sendGetVtrackRequest(["uuid": UUID.randomUUID().toString()])

        then: "Response should contain response from pbc"
        assert response == responseBody

        then: "Metrics should contain ok metric"
        def metricsRequest = defaultPbsService.sendCollectedMetricsRequest()
        assert metricsRequest[VTRACK_READ_OK_METRIC] == 1
    }

    def "PBS should return status code that came from pbc when get vtrack request and response from pbc invalid"() {
        given: "Random uuid"
        def uuid = UUID.randomUUID().toString()

        and: "Cache set up invalid response"
        def randomErrorMessage = PBSUtils.randomString
        prebidCache.setInvalidGetResponse(uuid, randomErrorMessage)

        when: "PBS processes get vtrack request"
        defaultPbsService.sendGetVtrackRequest(["uuid": uuid])

        then: "Request should fail with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INTERNAL_SERVER_ERROR.code()
        assert exception.responseBody == "Error occurred while sending request to cache: Cannot parse response: $randomErrorMessage"

        and: "Metrics should contain error metric"
        def metricsRequest = defaultPbsService.sendCollectedMetricsRequest()
        assert metricsRequest[VTRACK_READ_ERROR_METRIC] == 1
    }

    def "PBS should return 200 status code and body when get vtrack request with uuid and ch"() {
        given: "Current value of metric prebid_cache.vtrack.read.ok"
        def initialValue = getCurrentMetricValue(defaultPbsService, VTRACK_READ_OK_METRIC)

        and: "Random uuid and cache host"
        def uuid = UUID.randomUUID().toString()
        def cacheHost = PBSUtils.randomString

        and: "Set up response body"
        def responseBody = TransferValue.getTransferValue()
        prebidCache.setGetResponse(responseBody)

        when: "PBS processes get vtrack request"
        def response = defaultPbsService.sendGetVtrackRequest(["uuid": uuid, "ch": cacheHost])

        then: "Response should contain response from pbc"
        assert response == responseBody

        and: "Metrics should contain ok metrics"
        def metricsRequest = defaultPbsService.sendCollectedMetricsRequest()
        assert metricsRequest[VTRACK_READ_OK_METRIC] == initialValue + 1
    }

    def "PBS should return 200 status code and body when internal cache configured and get vtrack request with uuid and ch"() {
        given: "Current value of metric prebid_cache.vtrack.read.ok"
        def initialValue = getCurrentMetricValue(pbsServiceWithInternalCache, VTRACK_READ_OK_METRIC)

        and: "Flush metric"
        flushMetrics(pbsServiceWithInternalCache)

        and: "Random uuid and cache host"
        def uuid = UUID.randomUUID().toString()
        def cacheHost = PBSUtils.randomString

        and: "Mock set up successful response"
        def responseBody = TransferValue.getTransferValue()
        prebidCache.setGetResponse(responseBody)

        when: "PBS processes get vtrack request"
        def response = pbsServiceWithInternalCache.sendGetVtrackRequest(["uuid": uuid, "ch": cacheHost])

        then: "Response should contain response from pbc"
        assert response == responseBody

        and: "Metrics should contain ok metrics"
        def metricsRequest = pbsServiceWithInternalCache.sendCollectedMetricsRequest()
        assert metricsRequest[VTRACK_READ_OK_METRIC] == initialValue + 1

        and: "Verify parameters that came to external cache services"
        def requestParams = prebidCache.getVTracGetRequestParams()
        assert requestParams == "[{ch=[$cacheHost], uuid=[$uuid]}]"
    }

    def "PBS should return 200 status code when internal cache and get vtrack request contain uuid"() {
        given: "Current value of metric prebid_cache.vtrack.read.ok"
        def initialValue = getCurrentMetricValue(pbsServiceWithInternalCache, VTRACK_READ_OK_METRIC)

        and: "Random uuid"
        def uuid = UUID.randomUUID().toString()

        and: "Set up response body"
        def responseBody = TransferValue.getTransferValue()
        prebidCache.setGetResponse(responseBody)

        and: "Flush metric"
        flushMetrics(pbsServiceWithInternalCache)

        when: "PBS processes get vtrack request"
        def response = pbsServiceWithInternalCache.sendGetVtrackRequest(["uuid": uuid])

        then: "Response should contain response from pbc"
        assert response == responseBody

        and: "Metrics should contain ok metrics"
        def metricsRequest = pbsServiceWithInternalCache.sendCollectedMetricsRequest()
        assert metricsRequest[VTRACK_READ_OK_METRIC] == initialValue + 1

        and: "Verify parameters that came to external cache services"
        def requestParams = prebidCache.getVTracGetRequestParams()
        assert requestParams == "[{uuid=[$uuid]}]"
    }

    def "PBS should return status code that came from pbc when internal cache and get vtrack request and response from pbc invalid"() {
        given: "Random uuid"
        def uuid = UUID.randomUUID().toString()

        and: "Cache set up invalid response"
        def randomErrorMessage = PBSUtils.randomString
        prebidCache.setInvalidGetResponse(uuid, randomErrorMessage)

        and: "Flush metric"
        flushMetrics(pbsServiceWithInternalCache)

        when: "PBS processes get vtrack request"
        pbsServiceWithInternalCache.sendGetVtrackRequest(["uuid": uuid])

        then: "Request should fail with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INTERNAL_SERVER_ERROR.code()
        assert exception.responseBody == "Error occurred while sending request to cache: Cannot parse response: $randomErrorMessage"

        and: "Metrics should contain error metric"
        def metricsRequest = pbsServiceWithInternalCache.sendCollectedMetricsRequest()
        assert metricsRequest[VTRACK_READ_ERROR_METRIC] == 1

        and: "Verify parameters that came to external cache services"
        def requestParams = prebidCache.getVTracGetRequestParams()
        assert requestParams == "[{uuid=[$uuid]}]"
    }

    def "PBS should return 400 status code when internal cache and get vtrack request without uuid"() {
        when: "PBS processes get vtrack request"
        pbsServiceWithInternalCache.sendGetVtrackRequest(["uuid": null])

        then: "Request should fail with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == BAD_REQUEST.code()
        assert exception.responseBody == "'uuid' is a required query parameter and can't be empty"
    }

    def "PBS should update prebid_cache.creative_size.xml metric when account cache config #enabledCacheConcfig"() {
        given: "Current value of metric prebid_cache.requests.ok"
        def okInitialValue = getCurrentMetricValue(defaultPbsService, VTRACK_WRITE_OK_METRIC)

        and: "Default VtrackRequest"
        def accountId = PBSUtils.randomNumber.toString()
        def creative = encodeXml(Vast.getDefaultVastModel(PBSUtils.randomString))
        def request = VtrackRequest.getDefaultVtrackRequest(creative)

        and: "Create and save enabled events config in account"
        def account = new Account().tap {
            it.uuid = accountId
            it.config = new AccountConfig().tap {
                it.auction = new AccountAuctionConfig(cache: new AccountCacheConfig(enabled: enabledCacheConcfig))
            }
        }
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(defaultPbsService)

        and: "Set up prebid cache"
        prebidCache.setResponse()

        when: "PBS processes vtrack request"
        defaultPbsService.sendPostVtrackRequest(request, accountId)

        then: "prebid_cache.creative_size.xml metric should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        def creativeSize = creative.bytes.length
        assert metrics[VTRACK_WRITE_OK_METRIC] == okInitialValue + 1

        and: "account.<account-id>.prebid_cache.creative_size.xml should be updated"
        assert metrics[ACCOUNT_VTRACK_WRITE_OK_METRIC.formatted(accountId)] == 1
        assert metrics[ACCOUNT_VTRACK_XML_CREATIVE_SIZE_METRIC.formatted(accountId)] == creativeSize

        where:
        enabledCacheConcfig << [null, false, true]
    }

    def "PBS should failed cache and update prebid_cache.vtrack.write.err metric when cache service respond with invalid status code"() {
        given: "Current value of metric prebid_cache.requests.ok"
        def okInitialValue = getCurrentMetricValue(defaultPbsService, VTRACK_WRITE_ERROR_METRIC)

        and: "Default VtrackRequest"
        def accountId = PBSUtils.randomNumber.toString()
        def creative = encodeXml(Vast.getDefaultVastModel(PBSUtils.randomString))
        def request = VtrackRequest.getDefaultVtrackRequest(creative)

        and: "Create and save enabled events config in account"
        def account = new Account().tap {
            it.uuid = accountId
            it.config = new AccountConfig().tap {
                it.auction = new AccountAuctionConfig(cache: new AccountCacheConfig(enabled: true))
            }
        }
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(defaultPbsService)

        and: "Reset cache and set up invalid response"
        prebidCache.setInvalidPostResponse()

        when: "PBS processes vtrack request"
        defaultPbsService.sendPostVtrackRequest(request, accountId)

        then: "PBS throws an exception"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 500
        assert exception.responseBody.contains("Error occurred while sending request to cache: HTTP status code 500")

        then: "prebid_cache.vtrack.write.err metric should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics[VTRACK_WRITE_ERROR_METRIC] == okInitialValue + 1

        and: "account.<account-id>.prebid_cache.vtrack.write.err should be updated"
        assert metrics[ACCOUNT_VTRACK_WRITE_ERR_METRIC.formatted(accountId)] == 1
    }
}
