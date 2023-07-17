package org.prebid.server.functional.service

import com.fasterxml.jackson.core.type.TypeReference
import io.qameta.allure.Step
import io.restassured.authentication.AuthenticationScheme
import io.restassured.authentication.BasicAuthScheme
import io.restassured.builder.RequestSpecBuilder
import io.restassured.response.Response
import io.restassured.specification.RequestSpecification
import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.deals.report.LineItemStatusReport
import org.prebid.server.functional.model.mock.services.prebidcache.response.PrebidCacheResponse
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
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
    static final String FORCE_DEALS_UPDATE_ENDPOINT = "/pbs-admin/force-deals-update"
    static final String LINE_ITEM_STATUS_ENDPOINT = "/pbs-admin/lineitem-status"
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

    @Step("[POST] /openrtb2/auction")
    BidResponse sendAuctionRequest(BidRequest bidRequest, Map<String, ?> headers = [:]) {
        def response = postAuction(bidRequest, headers)

        checkResponseStatusCode(response)
        response.as(BidResponse)
    }

    @Step("[POST RAW] /openrtb2/auction")
    RawAuctionResponse sendAuctionRequestRaw(BidRequest bidRequest, Map<String, String> headers = [:]) {
        def response = postAuction(bidRequest, headers)

        new RawAuctionResponse().tap {
            it.headers = getHeaders(response)
            it.responseBody = response.body.asString()
        }
    }

    @Step("[GET] /openrtb2/amp")
    AmpResponse sendAmpRequest(AmpRequest ampRequest, Map<String, String> headers = [:]) {
        def response = getAmp(ampRequest, headers)

        checkResponseStatusCode(response)
        response.as(AmpResponse)
    }

    @Step("[GET RAW] /openrtb2/amp")
    RawAmpResponse sendAmpRequestRaw(AmpRequest ampRequest, Map<String, String> headers = [:]) {
        def response = getAmp(ampRequest, headers)

        new RawAmpResponse().tap {
            it.headers = getHeaders(response)
            it.responseBody = response.body.asString()
        }
    }

    @Step("[POST] /cookie_sync without cookie")
    CookieSyncResponse sendCookieSyncRequest(CookieSyncRequest request) {
        def response = postCookieSync(request)

        checkResponseStatusCode(response)
        response.as(CookieSyncResponse)
    }

    @Step("[POST] /cookie_sync with headers")
    CookieSyncResponse sendCookieSyncRequest(CookieSyncRequest request, Map<String, String> headers) {
        def response = postCookieSync(request, headers)

        checkResponseStatusCode(response)
        response.as(CookieSyncResponse)
    }

    @Step("[POST] /cookie_sync with uids cookie")
    CookieSyncResponse sendCookieSyncRequest(CookieSyncRequest request, UidsCookie uidsCookie) {
        def response = postCookieSync(request, uidsCookie)

        checkResponseStatusCode(response)
        response.as(CookieSyncResponse)
    }

    @Step("[POST] /cookie_sync with uids and additional cookies")
    CookieSyncResponse sendCookieSyncRequest(CookieSyncRequest request,
                                             UidsCookie uidsCookie,
                                             Map<String, String> additionalCookies) {
        def response = postCookieSync(request, uidsCookie, additionalCookies)

        checkResponseStatusCode(response)
        response.as(CookieSyncResponse)
    }

    @Step("[GET] /setuid")
    SetuidResponse sendSetUidRequest(SetuidRequest request, UidsCookie uidsCookie, Map header = [:]) {
        def uidsCookieAsJson = encode(uidsCookie)
        def uidsCookieAsEncodedJson = Base64.urlEncoder.encodeToString(uidsCookieAsJson.bytes)
        def response = given(requestSpecification).cookie(UIDS_COOKIE_NAME, uidsCookieAsEncodedJson)
                                                  .queryParams(toMap(request))
                                                  .headers(header)
                                                  .get(SET_UID_ENDPOINT)

        checkResponseStatusCode(response)

        def setuidResponse = new SetuidResponse()
        setuidResponse.uidsCookie = getDecodedUidsCookie(response)
        setuidResponse.responseBody = response.asByteArray()
        setuidResponse.headers = response.headers()
        setuidResponse
    }

    @Step("[GET] /getuids")
    GetuidResponse sendGetUidRequest(UidsCookie uidsCookie) {
        def uidsCookieAsJson = encode(uidsCookie)
        def uidsCookieAsEncodedJson = Base64.urlEncoder.encodeToString(uidsCookieAsJson.bytes)

        def response = given(requestSpecification).cookie(UIDS_COOKIE_NAME, uidsCookieAsEncodedJson)
                                                  .get(GET_UIDS_ENDPOINT)

        checkResponseStatusCode(response)
        response.as(GetuidResponse)
    }

    @Step("[GET] /event")
    byte[] sendEventRequest(EventRequest eventRequest, Map<String, String> headers = [:]) {
        def response = given(requestSpecification).headers(headers)
                                                  .queryParams(toMap(eventRequest))
                                                  .get(EVENT_ENDPOINT)

        checkResponseStatusCode(response)
        response.body.asByteArray()
    }

    @Step("[POST] /vtrack")
    PrebidCacheResponse sendVtrackRequest(VtrackRequest request, String account) {
        def response = given(requestSpecification).queryParam("a", account)
                                                  .body(request)
                                                  .post(VTRACK_ENDPOINT)

        checkResponseStatusCode(response)
        response.as(PrebidCacheResponse)
    }

    @Step("[GET] /status")
    StatusResponse sendStatusRequest() {
        def response = given(requestSpecification).get(STATUS_ENDPOINT)

        checkResponseStatusCode(response)
        response.as(StatusResponse)
    }

    @Step("[GET] /info/bidders")
    List<String> sendInfoBiddersRequest(String enabledOnly) {
        def response = given(requestSpecification).queryParam("enabledonly", enabledOnly)
                                                  .get(INFO_BIDDERS_ENDPOINT)

        checkResponseStatusCode(response)
        decode(response.asString(), new TypeReference<List<String>>() {})
    }

    @Step("[GET] /info/bidders")
    String sendInfoBiddersRequest() {
        def response = given(requestSpecification).get(INFO_BIDDERS_ENDPOINT)

        checkResponseStatusCode(response)
        response.body().asString()
    }

    @Step("[GET] /info/bidders/{bidderName}")
    BidderInfoResponse sendBidderInfoRequest(BidderName bidderName) {

        def response = given(requestSpecification).get("$INFO_BIDDERS_ENDPOINT/$bidderName.value")

        checkResponseStatusCode(response)
        response.as(BidderInfoResponse)
    }

    @Step("[GET] /bidders/params")
    BiddersParamsResponse sendBiddersParamsRequest() {
        def response = given(requestSpecification).get(BIDDERS_PARAMS_ENDPOINT)

        checkResponseStatusCode(response)
        response.as(BiddersParamsResponse)
    }

    @Step("[GET] /currency/rates")
    CurrencyRatesResponse sendCurrencyRatesRequest() {
        def response = given(adminRequestSpecification).get(CURRENCY_RATES_ENDPOINT)

        checkResponseStatusCode(response)
        response.as(CurrencyRatesResponse)
    }

    @Step("[GET] /logging/httpinteraction")
    String sendLoggingHttpInteractionRequest(HttpInteractionRequest httpInteractionRequest) {
        def response = given(adminRequestSpecification).queryParams(toMap(httpInteractionRequest))
                                                       .get(HTTP_INTERACTION_ENDPOINT)

        checkResponseStatusCode(response)
        response.body().asString()
    }

    @Step("[GET] /collected-metrics")
    Map<String, Number> sendCollectedMetricsRequest() {
        def response = given(adminRequestSpecification).get(COLLECTED_METRICS_ENDPOINT)

        checkResponseStatusCode(response)
        decode(response.asString(), new TypeReference<Map<String, Number>>() {})
    }

    @Step("[GET] /pbs-admin/force-deals-update")
    void sendForceDealsUpdateRequest(ForceDealsUpdateRequest forceDealsUpdateRequest) {
        def response = given(adminRequestSpecification).queryParams(toMap(forceDealsUpdateRequest))
                                                       .get(FORCE_DEALS_UPDATE_ENDPOINT)

        checkResponseStatusCode(response, 204)
    }

    @Step("[GET] /pbs-admin/lineitem-status")
    LineItemStatusReport sendLineItemStatusRequest(String lineItemId) {
        def request = given(adminRequestSpecification)
        if (lineItemId != null) {
            request.queryParam("id", lineItemId)
        }

        def response = request.get(LINE_ITEM_STATUS_ENDPOINT)

        checkResponseStatusCode(response)
        response.as(LineItemStatusReport)
    }

    @Step("[GET] /metrics")
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

    private Response postCookieSync(CookieSyncRequest cookieSyncRequest, Map<String, String> header) {
        postCookieSync(cookieSyncRequest, null, header)
    }

    private Response postCookieSync(CookieSyncRequest cookieSyncRequest,
                                    UidsCookie uidsCookie = null,
                                    Map<String, ?> additionalCookies = null,
                                    Map<String, String> header = null) {

        def cookies = [:]

        if (additionalCookies) {
            cookies.putAll(additionalCookies)
        }

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

    private Response getAmp(AmpRequest ampRequest, Map<String, String> headers = [:]) {
        given(requestSpecification).headers(headers)
                                   .queryParams(toMap(ampRequest))
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

    private static Map<String, String> getHeaders(Response response) {
        response.headers().collectEntries { [it.name, it.value] }
    }

    private static UidsCookie getDecodedUidsCookie(Response response) {
        def uids = response.detailedCookie(UIDS_COOKIE_NAME)?.value
        if (uids) {
            return decode(new String(Base64.urlDecoder.decode(uids)), UidsCookie)
        } else {
            throw new IllegalStateException("uids cookie is missing in response")
        }
    }

    List<String> getLogsByTime(Instant testStart, Instant testEnd = Instant.now()) {
        if (testEnd.isBefore(testStart)) {
            throw new IllegalArgumentException("The end time of the test is less than the start time")
        }
        def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
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
