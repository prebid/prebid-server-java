package org.prebid.server;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.Cookie;
import io.restassured.internal.mapping.Jackson2Mapper;
import io.restassured.parsing.Parser;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import org.hamcrest.Matchers;
import org.json.JSONException;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.proto.response.BidderUsersyncStatus;
import org.prebid.server.proto.response.CookieSyncResponse;
import org.prebid.server.proto.response.UsersyncInfo;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(locations = "classpath:org/prebid/server/ApplicationTest/test-application.properties")
public class ApplicationTest extends VertxTest {

    private static final String RUBICON = "rubicon";
    private static final String APPNEXUS = "appnexus";
    private static final String FACEBOOK = "audienceNetwork";
    private static final String PULSEPOINT = "pulsepoint";
    private static final String INDEXEXCHANGE = "indexExchange";
    private static final String LIFESTREET = "lifestreet";
    private static final String PUBMATIC = "pubmatic";
    private static final String CONVERSANT = "conversant";
    private static final String ADFORM = "adform";
    private static final String SOVRN = "sovrn";
    private static final String OPENX = "openx";
    private static final String ADTELLIGENT = "adtelligent";
    private static final String APPNEXUS_ALIAS = "appnexusAlias";
    private static final String CONVERSANT_ALIAS = "conversantAlias";

    private static final int APP_PORT = 8080;
    private static final int WIREMOCK_PORT = 8090;

    @ClassRule
    public static final WireMockClassRule wireMockRule = new WireMockClassRule(WIREMOCK_PORT);
    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    private static final RequestSpecification spec = new RequestSpecBuilder()
            .setBaseUri("http://localhost")
            .setPort(APP_PORT)
            .setConfig(RestAssuredConfig.config()
                    .objectMapperConfig(new ObjectMapperConfig(new Jackson2Mapper((aClass, s) -> mapper))))
            .build();

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromDifferentExchanges() throws IOException, JSONException {
        // given
        // rubicon bid response for imp 1
        wireMockRule.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withQueryParam("tk_xint", equalTo("rp-pbs"))
                .withBasicAuth("rubicon_user", "rubicon_password")
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("prebid-server/1.0"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/test-rubicon-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-rubicon-bid-response-1.json"))));

        // rubicon bid response for imp 2
        wireMockRule.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/test-rubicon-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-rubicon-bid-response-2.json"))));

        // appnexus bid response for imp 3
        wireMockRule.stubFor(post(urlPathEqualTo("/appnexus-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/test-appnexus-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-appnexus-bid-response-1.json"))));

        // appnexus bid response for imp 3 with alias parameters
        wireMockRule.stubFor(post(urlPathEqualTo("/appnexus-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/test-appnexus-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-appnexus-bid-response-2.json"))));

        // conversant bid response for imp 4
        wireMockRule.stubFor(post(urlPathEqualTo("/conversant-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/test-conversant-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-conversant-bid-response-1.json"))));

        // conversant bid response for imp 4 with alias parameters
        wireMockRule.stubFor(post(urlPathEqualTo("/conversant-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/test-conversant-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-conversant-bid-response-2.json"))));

        // facebook bid response for imp 5
        wireMockRule.stubFor(post(urlPathEqualTo("/facebook-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/test-facebook-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-facebook-bid-response-1.json"))));

        // index bid response for imp 6
        wireMockRule.stubFor(post(urlPathEqualTo("/indexexchange-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/test-indexexchange-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-indexexchange-bid-response-1.json"))));

        // lifestreet bid response for imp 7
        wireMockRule.stubFor(post(urlPathEqualTo("/lifestreet-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/test-lifestreet-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-lifestreet-bid-response-1.json"))));

        // lifestreet bid response for imp 71
        wireMockRule.stubFor(post(urlPathEqualTo("/lifestreet-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/test-lifestreet-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-lifestreet-bid-response-2.json"))));

        // pulsepoint bid response for imp 8
        wireMockRule.stubFor(post(urlPathEqualTo("/pulsepoint-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/test-pulsepoint-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-pulsepoint-bid-response-1.json"))));

        // pubmatic bid response for imp 9
        wireMockRule.stubFor(post(urlPathEqualTo("/pubmatic-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/test-pubmatic-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-pubmatic-bid-response-1.json"))));

        // adform bid response for imp 12
        wireMockRule.stubFor(get(urlPathEqualTo("/adform-exchange/"))
                .withQueryParam("CC", equalTo("1"))
                .withQueryParam("rp", equalTo("4"))
                .withQueryParam("fd", equalTo("1"))
                .withQueryParam("stid", equalTo("tid"))
                .withQueryParam("ip", equalTo("192.168.244.1"))
                .withQueryParam("adid", equalTo("ifaId"))
                // bWlkPTE1 is Base64 encoded "mid=15"
                .withQueryParam("bWlkPTE1", equalTo(""))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Request-Agent", equalTo("PrebidAdapter 0.1.1"))
                .withHeader("X-Forwarded-For", equalTo("192.168.244.1"))
                .withHeader("Cookie", equalTo("uid=AF-UID"))
                .withRequestBody(equalTo(""))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-adform-bid-response-1.json"))));

        // sovrn bid response for imp 13
        wireMockRule.stubFor(post(urlPathEqualTo("/sovrn-exchange"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("192.168.244.1"))
                .withHeader("DNT", equalTo("2"))
                .withHeader("Accept-Language", equalTo("en"))
                .withCookie("ljt_reader", equalTo("990011"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/test-sovrn-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-sovrn-bid-response-1.json"))));

        // adtelligent bid response for imp 14
        wireMockRule.stubFor(post(urlPathEqualTo("/adtelligent-exchange"))
                .withQueryParam("aid", WireMock.equalToJson("1000"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/test-adtelligent-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-adtelligent-bid-response-1.json"))));

        // openx bid response for imp 01 and 02
        wireMockRule.stubFor(post(urlPathEqualTo("/openx-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/test-openx-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-openx-bid-response-1.json"))));

        // openx bid response for imp 03
        wireMockRule.stubFor(post(urlPathEqualTo("/openx-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/test-openx-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-openx-bid-response-2.json"))));

        // openx bid response for imp 04
        wireMockRule.stubFor(post(urlPathEqualTo("/openx-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/test-openx-bid-request-3.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-openx-bid-response-3.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/test-cache-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/test-cache-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for
                // {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345","audienceNetwork":"FB-UID",
                // "pulsepoint":"PP-UID","indexExchange":"IE-UID","lifestreet":"LS-UID","pubmatic":"PM-UID",
                // "conversant":"CV-UID","sovrn":"990011","adtelligent":"AT-UID","adform":"AF-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSIsImF1ZGllbm"
                        + "NlTmV0d29yayI6IkZCLVVJRCIsInB1bHNlcG9pbnQiOiJQUC1VSUQiLCJpbmRleEV4Y2hhbmdlIjoiSUUtVUlEIiwi"
                        + "bGlmZXN0cmVldCI6IkxTLVVJRCIsInB1Ym1hdGljIjoiUE0tVUlEIiwiY29udmVyc2FudCI6IkNWLVVJRCIsInNvdn"
                        + "JuIjoiOTkwMDExIiwiYWR0ZWxsaWdlbnQiOiJBVC1VSUQiLCJhZGZvcm0iOiJBRi1VSUQifX0=")
                .body(jsonFrom("openrtb2/test-auction-request.json"))
                .post("/openrtb2/auction");

        // then
        String expectedAuctionResponse = auctionResponseFrom(jsonFrom("openrtb2/test-auction-response.json"),
                response, "ext.responsetimemillis.%s");

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    private static String setResponseTime(Response response, String expectedResponseJson, String exchange,
                                          String responseTimePath) {
        final Object val = response.path(format(responseTimePath, exchange));
        final Integer responseTime = val instanceof Integer ? (Integer) val : null;
        if (responseTime != null) {
            expectedResponseJson = expectedResponseJson.replaceAll("\"\\{\\{ " + exchange + "\\.response_time_ms }}\"",
                    responseTime.toString());
        }
        return expectedResponseJson;
    }

    @Test
    public void ampShouldReturnTargeting() throws IOException {
        // given
        // rubicon exchange
        wireMockRule.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("amp/test-rubicon-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("amp/test-rubicon-bid-response.json"))));

        // appnexus exchange
        wireMockRule.stubFor(post(urlPathEqualTo("/appnexus-exchange"))
                .withRequestBody(equalToJson(jsonFrom("amp/test-appnexus-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("amp/test-appnexus-bid-response.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("amp/test-cache-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("amp/test-cache-response.json"))));

        // when and then
        given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for
                // {"uids":{"rubicon":"J5VLCWQP-26-CWFT"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIn19")
                .when()
                .get("/openrtb2/amp" +
                        "?tag_id=test-amp-stored-request" +
                        "&ow=980" +
                        "&oh=120" +
                        "&timeout=10000000" +
                        "&slot=overwrite-tagId" +
                        "&curl=https%3A%2F%2Fgoogle.com")
                .then()
                .assertThat()
                .body(Matchers.equalTo(jsonFrom("amp/test-amp-response.json")));
    }

    @Test
    public void auctionShouldRespondWithBidsFromDifferentExchanges() throws IOException {
        // given
        // rubicon bid response for ad unit 1
        wireMockRule.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withQueryParam("tk_xint", equalTo("rp-pbs"))
                .withBasicAuth("rubicon_user", "rubicon_password")
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("prebid-server/1.0"))
                .withRequestBody(equalToJson(jsonFrom("auction/test-rubicon-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/test-rubicon-bid-response-1.json"))));

        // rubicon bid response for ad unit 2
        wireMockRule.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/test-rubicon-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/test-rubicon-bid-response-2.json"))));

        // rubicon bid response for ad unit 3
        wireMockRule.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/test-rubicon-bid-request-3.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/test-rubicon-bid-response-3.json"))));

        // appnexus bid response for ad unit 4
        wireMockRule.stubFor(post(urlPathEqualTo("/appnexus-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/test-appnexus-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/test-appnexus-bid-response-1.json"))));

        // facebook bid response for ad unit 5
        wireMockRule.stubFor(post(urlPathEqualTo("/facebook-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/test-facebook-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/test-facebook-bid-response-1.json"))));

        // pulsepoint bid response for ad unit 6
        wireMockRule.stubFor(post(urlPathEqualTo("/pulsepoint-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/test-pulsepoint-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/test-pulsepoint-bid-response-1.json"))));

        // pulsepoint bid response for ad unit 7
        wireMockRule.stubFor(post(urlPathEqualTo("/indexexchange-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/test-indexexchange-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/test-indexexchange-bid-response-1.json"))));

        // pulsepoint bid response for ad unit 8
        wireMockRule.stubFor(post(urlPathEqualTo("/lifestreet-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/test-lifestreet-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/test-lifestreet-bid-response-1.json"))));

        // pulsepoint bid response for ad unit 9
        wireMockRule.stubFor(post(urlPathEqualTo("/pubmatic-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/test-pubmatic-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/test-pubmatic-bid-response-1.json"))));

        // pulsepoint bid response for ad unit 10
        wireMockRule.stubFor(post(urlPathEqualTo("/conversant-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/test-conversant-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/test-conversant-bid-response-1.json"))));

        // sovrn bid response for ad unit 11
        wireMockRule.stubFor(post(urlPathEqualTo("/sovrn-exchange"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("192.168.244.1"))
                .withHeader("DNT", equalTo("10"))
                .withHeader("Accept-Language", equalTo("en"))
                .withCookie("ljt_reader", equalTo("990011"))
                .withRequestBody(equalToJson(jsonFrom("auction/test-sovrn-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/test-sovrn-bid-response-1.json"))));

        // adform bid response for ad unit 12
        wireMockRule.stubFor(get(urlPathEqualTo("/adform-exchange/"))
                .withQueryParam("CC", equalTo("1"))
                .withQueryParam("rp", equalTo("4"))
                .withQueryParam("fd", equalTo("1"))
                .withQueryParam("stid", equalTo("tid"))
                .withQueryParam("ip", equalTo("192.168.244.1"))
                .withQueryParam("adid", equalTo("ifaId"))
                // bWlkPTE1 is Base64 encoded "mid=15"
                .withQueryParam("bWlkPTE1", equalTo(""))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Request-Agent", equalTo("PrebidAdapter 0.1.1"))
                .withHeader("X-Forwarded-For", equalTo("192.168.244.1"))
                .withHeader("Cookie", equalTo("uid=AF-UID"))
                .withRequestBody(equalTo(""))
                .willReturn(aResponse().withBody(jsonFrom("auction/test-adform-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("auction/test-cache-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/test-cache-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for
                // {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345","audienceNetwork":"FB-UID",
                // "pulsepoint":"PP-UID","indexExchange":"IE-UID","lifestreet":"LS-UID","pubmatic":"PM-UID",
                // "conversant":"CV-UID","sovrn":"990011","adform":"AF-UID"}}
                .cookie("uids",
                        "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSIsImF1ZGllbmNlTmV0d29yayI6IkZCLVVJRCIsInB1bHNlcG9pbnQiOiJQUC1VSUQiLCJpbmRleEV4Y2hhbmdlIjoiSUUtVUlEIiwibGlmZXN0cmVldCI6IkxTLVVJRCIsInB1Ym1hdGljIjoiUE0tVUlEIiwiY29udmVyc2FudCI6IkNWLVVJRCIsInNvdnJuIjoiOTkwMDExIiwiYWRmb3JtIjoiQUYtVUlEIn19")
                .queryParam("debug", "1")
                .body(jsonFrom("auction/test-auction-request.json"))
                .post("/auction");

        // then
        assertThat(response.header("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(response.header("Pragma")).isEqualTo("no-cache");
        assertThat(response.header("Expires")).isEqualTo("0");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://www.example.com");

        final String expectedAuctionResponse = auctionResponseFrom(jsonFrom("auction/test-auction-response.json"),
                response, "bidder_status.find { it.bidder == '%s' }.response_time_ms");
        assertThat(response.asString()).isEqualTo(expectedAuctionResponse);
    }

    @Test
    public void statusShouldReturnReadyWithinResponseBodyAndHttp200Ok() {
        assertThat(given(spec).when().get("/status"))
                .extracting(Response::getStatusCode, response -> response.getBody().asString())
                .containsOnly(200, "ok");
    }

    @Test
    public void optoutShouldSetOptOutFlagAndRedirectToOptOutUrl() {
        wireMockRule.stubFor(post("/optout")
                .withRequestBody(equalTo("secret=abc&response=recaptcha1"))
                .willReturn(aResponse().withBody("{\"success\": true}")));

        final Response response = given(spec)
                .header("Content-Type", "application/x-www-form-urlencoded")
                // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ==")
                .body("g-recaptcha-response=recaptcha1&optout=1")
                .post("/optout");

        assertThat(response.statusCode()).isEqualTo(301);
        assertThat(response.header("location")).isEqualTo("http://optout/url");

        final Cookie cookie = response.getDetailedCookie("uids");
        assertThat(cookie.getDomain()).isEqualTo("cookie-domain");

        // this uids cookie value stands for {"uids":{},"optout":true}
        final Uids uids = decodeUids(cookie.getValue());
        assertThat(uids.getUids()).isEmpty();
        assertThat(uids.getUidsLegacy()).isEmpty();
        assertThat(uids.getOptout()).isTrue();
    }

    @Test
    public void staticShouldReturnHttp200Ok() {
        given(spec)
                .when()
                .get("/static")
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void cookieSyncShouldReturnBidderStatusWithRubiconUsersyncInfo() {
        final CookieSyncResponse cookieSyncResponse = given(spec)
                .body(CookieSyncRequest.of(singletonList(RUBICON)))
                .when()
                .post("/cookie_sync")
                .then()
                .spec(new ResponseSpecBuilder().setDefaultParser(Parser.JSON).build())
                .extract()
                .as(CookieSyncResponse.class);

        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("no_cookie",
                singletonList(BidderUsersyncStatus.builder()
                        .bidder(RUBICON)
                        .noCookie(true)
                        .usersync(UsersyncInfo.of("http://localhost:" + WIREMOCK_PORT + "/cookie", "redirect", false))
                        .build())));
    }

    @Test
    public void setuidShouldUpdateRubiconUidInUidCookie() {
        final Cookie uidsCookie = given(spec)
                // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"},
                // "bday":"2017-08-15T19:47:59.523908376Z"}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0"
                        + "NSJ9LCJiZGF5IjoiMjAxNy0wOC0xNVQxOTo0Nzo1OS41MjM5MDgzNzZaIn0=")
                // this constant is ok to use as long as it coincides with family name
                .queryParam("bidder", RUBICON)
                .queryParam("uid", "updatedUid")
                .when()
                .get("/setuid")
                .then()
                .extract()
                .detailedCookie("uids");

        assertThat(uidsCookie.getDomain()).isEqualTo("cookie-domain");
        assertThat(uidsCookie.getMaxAge()).isEqualTo(7776000);
        assertThat(uidsCookie.getExpiryDate().toInstant())
                .isCloseTo(Instant.now().plus(90, ChronoUnit.DAYS), within(10, ChronoUnit.SECONDS));

        final Uids uids = decodeUids(uidsCookie.getValue());
        assertThat(uids.getBday()).isEqualTo("2017-08-15T19:47:59.523908376Z"); // should be unchanged
        assertThat(uids.getUidsLegacy()).isEmpty();
        assertThat(uids.getUids().get(RUBICON).getUid()).isEqualTo("updatedUid");
        assertThat(uids.getUids().get(RUBICON).getExpires().toInstant())
                .isCloseTo(Instant.now().plus(14, ChronoUnit.DAYS), within(10, ChronoUnit.SECONDS));
        assertThat(uids.getUids().get("adnxs").getUid()).isEqualTo("12345");
        assertThat(uids.getUids().get("adnxs").getExpires().toInstant())
                .isCloseTo(Instant.now().minus(5, ChronoUnit.MINUTES), within(10, ChronoUnit.SECONDS));
    }

    @Test
    public void optionsRequestShouldRespondWithOriginalPolicyHeaders() {
        final Response response = given(spec)
                .header("Origin", "origin.com")
                .header("Access-Control-Request-Method", "GET")
                .when()
                .options("/");

        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("origin.com");
        assertThat(response.header("Access-Control-Allow-Methods")).contains(asList("HEAD", "OPTIONS", "GET", "POST"));
        assertThat(response.header("Access-Control-Allow-Headers"))
                .isEqualTo("Origin,Accept,X-Requested-With,Content-Type");
    }

    @Test
    public void biddersParamsShouldReturnBidderSchemas() throws IOException {
        given(spec)
                .when()
                .get("/bidders/params")
                .then()
                .assertThat()
                .body(Matchers.equalTo(jsonFrom("params/test-bidder-params-schemas.json")));
    }

    @Test
    public void infoBiddersShouldReturnRegisteredBidderNames() throws IOException {
        given(spec)
                .when()
                .get("/info/bidders")
                .then()
                .assertThat()
                .body(Matchers.equalTo(jsonFrom("info-bidders/test-info-bidders-response.json")));
    }

    @Test
    public void infoBidderDetailsShouldReturnMetadataForBidder() throws IOException {
        given(spec)
                .when()
                .get("/info/bidders/rubicon")
                .then()
                .assertThat()
                .body(Matchers.equalTo(jsonFrom("info-bidders/test-info-bidder-details-response.json")));
    }

    @Test
    public void validateShouldReturnResponseWithValidationMessages() throws IOException {
        final Response response = given(spec)
                .body(jsonFrom("auction/test-auction-request.json"))
                .when()
                .post("/validate");

        assertThat(response.asString()).isEqualTo("$.ad_units[0].video.protocols: array found, integer expected\n" +
                "$.ad_units[3].video.protocols: array found, integer expected");
    }

    private String jsonFrom(String file) throws IOException {
        // workaround to clear formatting
        return mapper.writeValueAsString(mapper.readTree(this.getClass().getResourceAsStream(
                this.getClass().getSimpleName() + "/" + file)));
    }

    private static String auctionResponseFrom(String template, Response response, String responseTimePath) {
        final Map<String, String> exchanges = new HashMap<>();
        exchanges.put(RUBICON, "http://localhost:" + WIREMOCK_PORT + "/rubicon-exchange?tk_xint=rp-pbs");
        exchanges.put(APPNEXUS, "http://localhost:" + WIREMOCK_PORT + "/appnexus-exchange");
        exchanges.put(FACEBOOK, "http://localhost:" + WIREMOCK_PORT + "/facebook-exchange");
        exchanges.put(PULSEPOINT, "http://localhost:" + WIREMOCK_PORT + "/pulsepoint-exchange");
        exchanges.put(INDEXEXCHANGE, "http://localhost:" + WIREMOCK_PORT + "/indexexchange-exchange");
        exchanges.put(LIFESTREET, "http://localhost:" + WIREMOCK_PORT + "/lifestreet-exchange");
        exchanges.put(PUBMATIC, "http://localhost:" + WIREMOCK_PORT + "/pubmatic-exchange");
        exchanges.put(CONVERSANT, "http://localhost:" + WIREMOCK_PORT + "/conversant-exchange");
        exchanges.put(ADFORM, "http://localhost:" + WIREMOCK_PORT + "/adform-exchange");
        exchanges.put(SOVRN, "http://localhost:" + WIREMOCK_PORT + "/sovrn-exchange");
        exchanges.put(ADTELLIGENT, "http://localhost:" + WIREMOCK_PORT + "/adtelligent-exchange");
        exchanges.put(OPENX, "http://localhost:" + WIREMOCK_PORT + "/openx-exchange");

        // inputs for aliases
        exchanges.put(APPNEXUS_ALIAS, null);
        exchanges.put(CONVERSANT_ALIAS, null);

        String result = template.replaceAll("\\{\\{ cache_resource_url }}",
                "http://localhost:" + WIREMOCK_PORT + "/cache?uuid=");

        for (final Map.Entry<String, String> exchangeEntry : exchanges.entrySet()) {
            final String exchange = exchangeEntry.getKey();
            result = result.replaceAll("\\{\\{ " + exchange + "\\.exchange_uri }}", exchangeEntry.getValue());
            result = setResponseTime(response, result, exchange, responseTimePath);
        }

        return result;
    }

    private static Uids decodeUids(String value) {
        return Json.decodeValue(Buffer.buffer(Base64.getUrlDecoder().decode(value)), Uids.class);
    }
}
