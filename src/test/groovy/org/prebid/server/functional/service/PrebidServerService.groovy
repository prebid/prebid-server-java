package org.prebid.server.functional.service

import com.fasterxml.jackson.core.type.TypeReference
import io.restassured.authentication.AuthenticationScheme
import io.restassured.authentication.BasicAuthScheme
import io.restassured.builder.RequestSpecBuilder
import io.restassured.response.Response
import io.restassured.specification.RequestSpecification
import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.mock.services.prebidcache.response.PrebidCacheResponse
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.event.EventRequest
import org.prebid.server.functional.model.request.logging.httpinteraction.HttpInteractionRequest
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.model.request.vtrack.VtrackRequest
import org.prebid.server.functional.model.response.amp.AmpResponse
import org.prebid.server.functional.model.response.amp.RawAmpResponse
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.RawAuctionResponse
import org.prebid.server.functional.model.response.biddersparams.BiddersParamsResponse
import org.prebid.server.functional.model.response.cookiesync.CookieSyncResponse
import org.prebid.server.functional.model.response.cookiesync.RawCookieSyncResponse
import org.prebid.server.functional.model.response.currencyrates.CurrencyRatesResponse
import org.prebid.server.functional.model.response.getuids.GetuidResponse
import org.prebid.server.functional.model.response.infobidders.BidderInfoResponse
import org.prebid.server.functional.model.response.setuid.SetuidResponse
import org.prebid.server.functional.model.response.status.StatusResponse
import org.prebid.server.functional.testcontainers.container.PrebidServerContainer
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import static io.restassured.RestAssured.given
import static java.time.ZoneOffset.UTC

class PrebidServerService implements ObjectMapperWrapper {

    static final String AUCTION_ENDPOINT = "/openrtb2/auction"
    static final String AMP_ENDPOINT = "/openrtb2/amp"
    static final String COOKIE_SYNC_ENDPOINT = "/cookie_sync"
    static final String SET_UID_ENDPOINT = "/setuid"
    static final String GET_UIDS_ENDPOINT = "/getuids"
    static final String EVENT_ENDPOINT = "/event"
    static final String VTRACK_ENDPOINT = "/vtrack"
    static final String STATUS_ENDPOINT = "/status"
    static final String INFO_BIDDERS_ENDPOINT = "/info/bidders"
    static final String BIDDERS_PARAMS_ENDPOINT = "/bidders/params"
    static final String CURRENCY_RATES_ENDPOINT = "/currency/rates"
    static final String HTTP_INTERACTION_ENDPOINT = "/logging/httpinteraction"
    static final String COLLECTED_METRICS_ENDPOINT = "/collected-metrics"
    static final String PROMETHEUS_METRICS_ENDPOINT = "/metrics"
    static final String UIDS_COOKIE_NAME = "uids"

    private final PrebidServerContainer pbsContainer
    private final RequestSpecification requestSpecification
    private final RequestSpecification adminRequestSpecification
    private final RequestSpecification prometheusRequestSpecification

    private final Logger log = LoggerFactory.getLogger(PrebidServerService)

    PrebidServerService(PrebidServerContainer pbsContainer) {
        def authenticationScheme = new BasicAuthScheme()
        authenticationScheme.userName = pbsContainer.ADMIN_ENDPOINT_USERNAME
        authenticationScheme.password = pbsContainer.ADMIN_ENDPOINT_PASSWORD
        this.pbsContainer = pbsContainer
        requestSpecification = new RequestSpecBuilder().setBaseUri(pbsContainer.rootUri)
                                                       .build()
        adminRequestSpecification = buildAndGetRequestSpecification(pbsContainer.adminRootUri, authenticationScheme)
        prometheusRequestSpecification = buildAndGetRequestSpecification(pbsContainer.prometheusRootUri, authenticationScheme)
    }

    BidResponse  sendAuctionRequest(BidRequest bidRequest, Map<String, ?> headers = [:]) {
        def response = postAuction(bidRequest, headers)

        checkResponseStatusCode(response)
        decode(response.body.asString(), BidResponse)
    }

    RawAuctionResponse sendAuctionRequestRaw(BidRequest bidRequest, Map<String, String> headers = [:]) {
        def response = postAuction(bidRequest, headers)

        new RawAuctionResponse().tap {
            it.headers = getHeaders(response)
            it.responseBody = response.body.asString()
        }
    }

    AmpResponse sendAmpRequestWithAdditionalQueries(AmpRequest ampRequest, Map<String, Object> queries = [:]) {
        def response = getAmp(ampRequest, [:], queries)

        checkResponseStatusCode(response)
        decode(response.body.asString(), AmpResponse)
    }

    AmpResponse sendAmpRequest(AmpRequest ampRequest, Map<String, String> headers = [:]) {
        def response = getAmp(ampRequest, headers)

        checkResponseStatusCode(response)
        decode(response.body.asString(), AmpResponse)
    }

    RawAmpResponse sendAmpRequestRaw(AmpRequest ampRequest, Map<String, String> headers = [:]) {
        def response = getAmp(ampRequest, headers)

        new RawAmpResponse().tap {
            it.headers = getHeaders(response)
            it.responseBody = response.body.asString()
        }
    }

    CookieSyncResponse sendCookieSyncRequest(CookieSyncRequest request) {
        def response = postCookieSync(request)

        checkResponseStatusCode(response)
        decode(response.body.asString(), CookieSyncResponse)
    }

    CookieSyncResponse sendCookieSyncRequest(CookieSyncRequest request, Map<String, String> headers) {
        def response = postCookieSync(request, null, headers)

        checkResponseStatusCode(response)
        decode(response.body.asString(), CookieSyncResponse)
    }

    CookieSyncResponse sendCookieSyncRequest(CookieSyncRequest request, UidsCookie uidsCookie) {
        def response = postCookieSync(request, uidsCookie)

        checkResponseStatusCode(response)
        decode(response.body.asString(), CookieSyncResponse)
    }

    CookieSyncResponse sendCookieSyncRequest(CookieSyncRequest request,
                                             UidsCookie uidsCookie,
                                             Map<String, String> additionalCookies) {
        def response = postCookieSync(request, uidsCookie, additionalCookies)

        checkResponseStatusCode(response)
        decode(response.body.asString(), CookieSyncResponse)
    }

    RawCookieSyncResponse sendCookieSyncRequestRaw(CookieSyncRequest request, UidsCookie uidsCookie) {
        def response = postCookieSync(request, uidsCookie)

        new RawCookieSyncResponse().tap {
            it.headers = getHeaders(response)
            it.responseBody = response.body.asString()
        }
    }

    RawCookieSyncResponse sendCookieSyncRequestRaw(CookieSyncRequest request,
                                                   UidsCookie uidsCookie,
                                                   Map<String, String> additionalCookies) {
        def response = postCookieSync(request, uidsCookie, additionalCookies)

        new RawCookieSyncResponse().tap {
            it.headers = getHeaders(response)
            it.responseBody = response.body.asString()
        }
    }

    SetuidResponse sendSetUidRequest(SetuidRequest request, UidsCookie uidsCookie, Map header = [:]) {
        sendSetUidRequest(request, [uidsCookie], header)
    }

    SetuidResponse sendSetUidRequest(SetuidRequest request, List<UidsCookie> uidsCookies, Map header = [:]) {
        def cookies = uidsCookies.withIndex().collectEntries { group, index ->
            def uidsCookieAsJson = encode(group)
            def uidsCookieAsEncodedJson = Base64.urlEncoder.encodeToString(uidsCookieAsJson.bytes)
            ["${UIDS_COOKIE_NAME}${index > 0 ? index + 1 : ''}": uidsCookieAsEncodedJson]
        }

        def response = given(requestSpecification)
                .cookies(cookies)
                .queryParams(toMap(request))
                .headers(header)
                .get(SET_UID_ENDPOINT)

        checkResponseStatusCode(response)

        def setuidResponse = new SetuidResponse()
        setuidResponse.uidsCookie = getDecodedUidsCookie(response)
        setuidResponse.responseBody = response.asByteArray()
        setuidResponse.headers = getHeaders(response)
        setuidResponse
    }

    GetuidResponse sendGetUidRequest(UidsCookie uidsCookie) {
        def uidsCookieAsJson = encode(uidsCookie)
        def uidsCookieAsEncodedJson = Base64.urlEncoder.encodeToString(uidsCookieAsJson.bytes)

        def response = given(requestSpecification).cookie(UIDS_COOKIE_NAME, uidsCookieAsEncodedJson)
                                                  .get(GET_UIDS_ENDPOINT)

        checkResponseStatusCode(response)
        decode(response.body.asString(), GetuidResponse)
    }

    byte[] sendEventRequest(EventRequest eventRequest, Map<String, String> headers = [:]) {
        def response = given(requestSpecification).headers(headers)
                                                  .queryParams(toMap(eventRequest))
                                                  .get(EVENT_ENDPOINT)

        checkResponseStatusCode(response)
        response.body.asByteArray()
    }

    PrebidCacheResponse sendVtrackRequest(VtrackRequest request, String account) {
        def response = given(requestSpecification).queryParam("a", account)
                                                  .body(request)
                                                  .post(VTRACK_ENDPOINT)

        checkResponseStatusCode(response)
        decode(response.body.asString(), PrebidCacheResponse)
    }

    StatusResponse sendStatusRequest() {
        def response = given(requestSpecification).get(STATUS_ENDPOINT)

        checkResponseStatusCode(response)
        decode(response.body.asString(), StatusResponse)
    }

    String sendInfoBiddersRequest() {
        def response = given(requestSpecification).get(INFO_BIDDERS_ENDPOINT)

        checkResponseStatusCode(response)
        response.body().asString()
    }

    List<String> sendInfoBiddersRequest(Map<String, String> queryParam) {
        def response = given(requestSpecification).queryParams(queryParam).get(INFO_BIDDERS_ENDPOINT)

        checkResponseStatusCode(response)
        decode(response.asString(), new TypeReference<List<String>>() {})
    }

    BidderInfoResponse sendBidderInfoRequest(BidderName bidderName) {

        def response = given(requestSpecification).get("$INFO_BIDDERS_ENDPOINT/$bidderName.value")

        checkResponseStatusCode(response)
        decode(response.body.asString(), BidderInfoResponse)
    }

    BiddersParamsResponse sendBiddersParamsRequest() {
        def response = given(requestSpecification).get(BIDDERS_PARAMS_ENDPOINT)

        checkResponseStatusCode(response)
        decode(response.body.asString(), BiddersParamsResponse)
    }

    CurrencyRatesResponse sendCurrencyRatesRequest() {
        def response = given(adminRequestSpecification).get(CURRENCY_RATES_ENDPOINT)

        checkResponseStatusCode(response)
        decode(response.body.asString(), CurrencyRatesResponse)
    }

    String sendLoggingHttpInteractionRequest(HttpInteractionRequest httpInteractionRequest) {
        def response = given(adminRequestSpecification).queryParams(toMap(httpInteractionRequest))
                                                       .get(HTTP_INTERACTION_ENDPOINT)

        checkResponseStatusCode(response)
        response.body().asString()
    }

    Map<String, Number> sendCollectedMetricsRequest() {
        def response = given(adminRequestSpecification).get(COLLECTED_METRICS_ENDPOINT)

        checkResponseStatusCode(response)
        decode(response.asString(), new TypeReference<Map<String, Number>>() {})
    }

    String sendPrometheusMetricsRequest() {
        def response = given(prometheusRequestSpecification).get(PROMETHEUS_METRICS_ENDPOINT)

        checkResponseStatusCode(response)
        response.body().asString()
    }

    PrebidServerService withWarmup() {
        sendAuctionRequest(BidRequest.defaultBidRequest)
        this
    }

    private Response postAuction(BidRequest bidRequest, Map<String, ?> headers = [:]) {
        def payload = encode(bidRequest)

        given(requestSpecification).headers(headers)
                                   .body(payload)
                                   .post(AUCTION_ENDPOINT)
    }

    private Response postCookieSync(CookieSyncRequest cookieSyncRequest,
                                    UidsCookie uidsCookie = null,
                                    Map<String, ?> additionalCookies = null,
                                    Map<String, String> header = null) {

        def cookies = additionalCookies ?: [:]

        if (uidsCookie) {
            cookies.put(UIDS_COOKIE_NAME, Base64.urlEncoder.encodeToString(encode(uidsCookie).bytes))
        }

        postCookieSync(cookieSyncRequest, cookies, header)
    }

    private Response postCookieSync(CookieSyncRequest cookieSyncRequest,
                                    Map<String, ?> cookies,
                                    Map<String, ?> headers) {
        def requestSpecification = given(requestSpecification)
                .body(encode(cookieSyncRequest))

        if (cookies) {
            requestSpecification.cookies(cookies)
        }

        if (headers) {
            requestSpecification.headers(headers)
        }

        requestSpecification.post(COOKIE_SYNC_ENDPOINT)
    }

    private Response getAmp(AmpRequest ampRequest,
                            Map<String, String> headers = [:],
                            Map<String, Object> queries = [:]) {
        def map = toMap(ampRequest)

        if (!queries.isEmpty()) {
            map.putAll(queries)
        }

        given(requestSpecification).headers(headers)
                .queryParams(map)
                .get(AMP_ENDPOINT)
    }

    private void checkResponseStatusCode(Response response, int statusCode = 200) {
        def responseStatusCode = response.statusCode
        if (responseStatusCode != statusCode) {
            def responseBody = response.body.asString()
            log.error(responseBody)
            throw new PrebidServerException(responseStatusCode, responseBody, getHeaders(response))
        }
    }

    private static Map<String, List<String>> getHeaders(Response response) {
        response.headers().groupBy { it.name }.collectEntries { [(it.key): it.value*.value] }
    }

    private static UidsCookie getDecodedUidsCookie(Response response) {
        def sortedCookies = response.detailedCookies()
                .findAll { cookie -> !(cookie =~ /\buids\d*=\s*;/) }
                .sort { a, b ->
                    def aMatch = (a.name =~ /uids(\d*)/)[0]
                    def bMatch = (b.name =~ /uids(\d*)/)[0]

                    def aNumber = (aMatch?.getAt(1) ? aMatch[1].toInteger() : 0)
                    def bNumber = (bMatch?.getAt(1) ? bMatch[1].toInteger() : 0)

                    aNumber <=> bNumber
                }

        def decodedCookiesList = sortedCookies.collect { cookie ->
            def uid = (cookie =~ /uids\d*=(\S+?);/)[0][1]
            decodeWithBase64(uid as String, UidsCookie)
        }

        decodedCookiesList.inject(new UidsCookie()) { uidsCookie, decodedCookie ->
            uidsCookie.uids = (uidsCookie.uids ?: new LinkedHashMap()) + (decodedCookie.uids ?: new LinkedHashMap())
            uidsCookie.tempUIDs = (uidsCookie.tempUIDs ?: new LinkedHashMap()) + (decodedCookie.tempUIDs ?: new LinkedHashMap())
            uidsCookie
        }
    }

    List<String> getLogsByTime(Instant testStart, Instant testEnd = Instant.now()) {
        if (testEnd.isBefore(testStart)) {
            throw new IllegalArgumentException("The end time of the test is less than the start time")
        }
        def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                                         .withZone(ZoneId.from(UTC))
        def logs = Arrays.asList(pbsContainer.logs.split("\n"))
        def filteredLogs = []

        def deltaTime = Duration.between(testStart, testEnd).plusSeconds(1).seconds

        for (int i = 0; i <= deltaTime; i++) {
            def time = testStart.plusSeconds(i)
            def element = logs.find { it.contains(formatter.format(time)) }
            if (element) {
                filteredLogs.addAll(logs.subList(logs.indexOf(element), logs.size()))
                break
            }
        }
        filteredLogs
    }

    String getLogsByValue(String value) {
        if (!value) {
            throw new IllegalArgumentException("Value is null or empty")
        }
        getPbsLogsByValue(value)
    }

    Boolean isContainLogsByValue(String value) {
        getPbsLogsByValue(value) != null
    }

    private String getPbsLogsByValue(String value) {
        pbsContainer.logs.split("\n").find { it.contains(value) }
    }

    <T> T getValueFromContainer(String path, Class<T> clazz) {
        pbsContainer.copyFileFromContainer(path, { inputStream ->
            return decode(inputStream, clazz)
        })
    }

    Boolean isFileExist(String path) {
        pbsContainer.execInContainer("test", "-f", path).getExitCode() == 0
    }

    void deleteFilesInDirectory(String directoryPath) {
        pbsContainer.execInContainer("find", directoryPath, "-maxdepth", "${Integer.MAX_VALUE}", "-type", "f", "-exec", "rm", "-f", "{}", "+")
    }

    private static RequestSpecification buildAndGetRequestSpecification(String uri, AuthenticationScheme authScheme) {
        new RequestSpecBuilder().setBaseUri(uri)
                                .setAuth(authScheme)
                                .build()
    }
}
