package org.prebid.server.functional.service

import com.fasterxml.jackson.core.type.TypeReference
import io.qameta.allure.Step
import io.restassured.authentication.BasicAuthScheme
import io.restassured.builder.RequestSpecBuilder
import io.restassured.response.Response
import io.restassured.specification.RequestSpecification
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.mock.services.prebidcache.response.PrebidCacheResponse
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.event.EventRequest
import org.prebid.server.functional.model.request.logging.httpinteraction.HttpInteractionRequest
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.model.request.setuid.UidsCookie
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

class PrebidServerService {

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

    private final PrebidServerContainer pbsContainer
    private final ObjectMapperWrapper mapper
    private final RequestSpecification requestSpecification
    private final RequestSpecification adminRequestSpecification

    private final Logger log = LoggerFactory.getLogger(PrebidServerService.class)

    PrebidServerService(PrebidServerContainer pbsContainer, ObjectMapperWrapper mapper) {
        def authenticationScheme = new BasicAuthScheme()
        authenticationScheme.userName = pbsContainer.ADMIN_ENDPOINT_USERNAME
        authenticationScheme.password = pbsContainer.ADMIN_ENDPOINT_PASSWORD
        this.pbsContainer = pbsContainer
        this.mapper = mapper
        requestSpecification = new RequestSpecBuilder().setBaseUri(pbsContainer.rootUri)
                                                       .build()
        adminRequestSpecification = new RequestSpecBuilder().setBaseUri(pbsContainer.adminRootUri)
                                                            .setAuth(authenticationScheme)
                                                            .build()
    }

    @Step("[POST] /openrtb2/auction")
    BidResponse sendAuctionRequest(BidRequest bidRequest, Map<String, String> headers = [:]) {
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
        def payload = mapper.encode(request)
        def response = given(requestSpecification).body(payload)
                                                  .post(COOKIE_SYNC_ENDPOINT)

        checkResponseStatusCode(response)
        response.as(CookieSyncResponse)
    }

    @Step("[POST] /cookie_sync with cookie")
    CookieSyncResponse sendCookieSyncRequest(CookieSyncRequest request, UidsCookie uidsCookie) {
        def uidsCookieAsJson = mapper.encode(uidsCookie)
        def uidsCookieAsEncodedJson = Base64.urlEncoder.encodeToString(uidsCookieAsJson.bytes)

        def payload = mapper.encode(request)
        def response = given(requestSpecification).cookie("uids", uidsCookieAsEncodedJson)
                                                  .body(payload)
                                                  .post(COOKIE_SYNC_ENDPOINT)

        checkResponseStatusCode(response)
        response.as(CookieSyncResponse)
    }

    @Step("[GET] /setuid")
    SetuidResponse sendSetUidRequest(SetuidRequest request, UidsCookie uidsCookie) {
        def uidsCookieAsJson = mapper.encode(uidsCookie)
        def uidsCookieAsEncodedJson = Base64.urlEncoder.encodeToString(uidsCookieAsJson.bytes)
        def response = given(requestSpecification).cookie("uids", uidsCookieAsEncodedJson)
                                                  .queryParams(mapper.toMap(request))
                                                  .get(SET_UID_ENDPOINT)

        checkResponseStatusCode(response)

        def setuidResponse = new SetuidResponse()
        setuidResponse.uidsCookie = response.detailedCookie("uids")
        setuidResponse.responseBody = response.asString()
        setuidResponse
    }

    @Step("[GET] /getuids")
    GetuidResponse sendGetUidRequest(UidsCookie uidsCookie) {
        def uidsCookieAsJson = mapper.encode(uidsCookie)
        def uidsCookieAsEncodedJson = Base64.urlEncoder.encodeToString(uidsCookieAsJson.bytes)

        def response = given(requestSpecification).cookie("uids", uidsCookieAsEncodedJson)
                                                  .get(GET_UIDS_ENDPOINT)

        checkResponseStatusCode(response)
        response.as(GetuidResponse)
    }

    @Step("[GET] /event")
    byte[] sendEventRequest(EventRequest eventRequest) {
        def response = given(requestSpecification).queryParams(mapper.toMap(eventRequest))
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
        mapper.decode(response.asString(), new TypeReference<List<String>>() {})
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
        def response = given(adminRequestSpecification).queryParams(mapper.toMap(httpInteractionRequest))
                                                       .get(HTTP_INTERACTION_ENDPOINT)

        checkResponseStatusCode(response)
        response.body().asString()
    }

    @Step("[GET] /collected-metrics")
    Map<String, Number> sendCollectedMetricsRequest() {
        def response = given(adminRequestSpecification).get(COLLECTED_METRICS_ENDPOINT)

        checkResponseStatusCode(response)
        mapper.decode(response.asString(), new TypeReference<Map<String, Number>>() {})
    }

    private Response postAuction(BidRequest bidRequest, Map<String, String> headers = [:]) {
        def payload = mapper.encode(bidRequest)

        given(requestSpecification).headers(headers)
                                   .body(payload)
                                   .post(AUCTION_ENDPOINT)
    }

    private Response getAmp(AmpRequest ampRequest, Map<String, String> headers = [:]) {
        given(requestSpecification).headers(headers)
                                   .queryParams(mapper.toMap(ampRequest))
                                   .get(AMP_ENDPOINT)
    }

    private void checkResponseStatusCode(Response response) {
        def statusCode = response.statusCode
        if (statusCode != 200) {
            def responseBody = response.body.asString()
            log.error(responseBody)
            throw new PrebidServerException(statusCode, responseBody, getHeaders(response))
        }
    }

    private static Map<String, String> getHeaders(Response response) {
        response.headers().collectEntries { [it.name, it.value] }
    }

    List<String> getLogsByTime(Instant testStart,
                               Instant testEnd = Instant.now()) {
        if (!testEnd.isAfter(testStart)) {
            throw new IllegalArgumentException("The end time of the test is less than the start time")
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                                       .withZone(ZoneId.from(UTC))
        def logs = Arrays.asList(pbsContainer.logs.split("\n"))
        def filteredLogs = []

        def deltaTime = Duration.between(testStart, testEnd).seconds

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
}
