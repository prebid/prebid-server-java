package org.prebid.server.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.iab.gdpr.consent.VendorConsentEncoder;
import com.iab.gdpr.consent.implementation.v1.VendorConsentBuilder;
import com.iab.gdpr.consent.range.StartEndRangeEntry;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.Cookie;
import io.restassured.http.Header;
import io.restassured.internal.mapping.Jackson2Mapper;
import io.restassured.parsing.Parser;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.Vertx;
import org.apache.commons.collections4.CollectionUtils;
import org.hamcrest.Matchers;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.proto.response.BidderUsersyncStatus;
import org.prebid.server.proto.response.CookieSyncResponse;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.ResourceUtil;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@RunWith(SpringRunner.class)
public class ApplicationTest extends IntegrationTest {

    private static final String ADFORM = "adform";
    private static final String APPNEXUS = "appnexus";
    private static final String APPNEXUS_ALIAS = "appnexusAlias";
    private static final String APPNEXUS_CONFIGURED_ALIAS = "districtm";
    private static final String RUBICON = "rubicon";

    private static final int ADMIN_PORT = 8060;

    private static final RequestSpecification ADMIN_SPEC = new RequestSpecBuilder()
            .setBaseUri("http://localhost")
            .setPort(ADMIN_PORT)
            .setConfig(RestAssuredConfig.config()
                    .objectMapperConfig(new ObjectMapperConfig(new Jackson2Mapper((aClass, s) -> mapper))))
            .build();

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromRubiconAndAppnexus() throws IOException, JSONException {
        // given
        // rubicon bid response for imp 1
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withQueryParam("tk_xint", equalTo("dmbjs"))
                .withBasicAuth("rubicon_user", "rubicon_password")
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("prebid-server/1.0"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/rubicon_appnexus/test-rubicon-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom(
                        "openrtb2/rubicon_appnexus/test-rubicon-bid-response-1.json"))));

        // rubicon bid response for imp 2
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/rubicon_appnexus/test-rubicon-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom(
                        "openrtb2/rubicon_appnexus/test-rubicon-bid-response-2.json"))));

        // appnexus bid response for imp 3
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/appnexus-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/rubicon_appnexus/test-appnexus-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom(
                        "openrtb2/rubicon_appnexus/test-appnexus-bid-response-1.json"))));

        // appnexus bid response for imp 3 with alias parameters
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/appnexus-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/rubicon_appnexus/test-appnexus-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom(
                        "openrtb2/rubicon_appnexus/test-appnexus-bid-response-2.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom(
                        "openrtb2/rubicon_appnexus/test-cache-rubicon-appnexus-request.json"), true, false))
                .willReturn(aResponse()
                        .withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName",
                                "openrtb2/rubicon_appnexus/test-cache-matcher-rubicon-appnexus.json")
                ));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ==")
                .body(jsonFrom("openrtb2/rubicon_appnexus/test-auction-rubicon-appnexus-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/rubicon_appnexus/test-auction-rubicon-appnexus-response.json",
                response, asList(RUBICON, APPNEXUS, APPNEXUS_ALIAS));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), openrtbCacheDebugComparator());
    }

    @Test
    public void auctionShouldRespondWithBidsFromAppnexusAlias() throws IOException {
        // given
        // appnexus bid response for ad unit 4
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/appnexus-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/districtm/test-districtm-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/districtm/test-districtm-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("auction/districtm/test-cache-districtm-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/districtm/test-cache-districtm-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"adnxs":"12345"}}
                .cookie("uids", "eyJ1aWRzIjp7ImFkbnhzIjoiMTIzNDUifX0=")
                .queryParam("debug", "1")
                .body(jsonFrom("auction/districtm/test-auction-districtm-request.json"))
                .post("/auction");

        // then
        assertThat(response.header("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(response.header("Pragma")).isEqualTo("no-cache");
        assertThat(response.header("Expires")).isEqualTo("0");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://www.example.com");

        final String expectedAuctionResponse = legacyAuctionResponseFrom(
                "auction/districtm/test-auction-districtm-response.json",
                response, asList(APPNEXUS, APPNEXUS_CONFIGURED_ALIAS));
        assertThat(response.asString()).isEqualTo(expectedAuctionResponse);
    }

    @Test
    public void auctionShouldRespondWithBidsFromRubiconAndAppnexus() throws IOException {
        // given
        // rubicon bid response for ad unit 1
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withQueryParam("tk_xint", equalTo("rp-pbs"))
                .withBasicAuth("rubicon_user", "rubicon_password")
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("prebid-server/1.0"))
                .withRequestBody(equalToJson(jsonFrom("auction/rubicon_appnexus/test-rubicon-bid-request-1.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("auction/rubicon_appnexus/test-rubicon-bid-response-1.json"))));

        // rubicon bid response for ad unit 2
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/rubicon_appnexus/test-rubicon-bid-request-2.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("auction/rubicon_appnexus/test-rubicon-bid-response-2.json"))));

        // rubicon bid response for ad unit 3
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/rubicon_appnexus/test-rubicon-bid-request-3.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("auction/rubicon_appnexus/test-rubicon-bid-response-3.json"))));

        // appnexus bid response for ad unit 4
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/appnexus-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/rubicon_appnexus/test-appnexus-bid-request-1.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("auction/rubicon_appnexus/test-appnexus-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom(
                        "auction/rubicon_appnexus/test-cache-rubicon-appnexus-request.json")))
                .willReturn(aResponse().withBody(jsonFrom(
                        "auction/rubicon_appnexus/test-cache-rubicon-appnexus-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                //this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ==")
                .queryParam("debug", "1")
                .body(jsonFrom("auction/rubicon_appnexus/test-auction-rubicon-appnexus-request.json"))
                .post("/auction");

        // then
        assertThat(response.header("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(response.header("Pragma")).isEqualTo("no-cache");
        assertThat(response.header("Expires")).isEqualTo("0");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://www.example.com");

        final String expectedAuctionResponse = legacyAuctionResponseFrom(
                "auction/rubicon_appnexus/test-auction-rubicon-appnexus-response.json",
                response, asList(RUBICON, APPNEXUS, APPNEXUS_ALIAS));
        assertThat(response.asString()).isEqualTo(expectedAuctionResponse);
    }

    @Test
    public void ampShouldReturnTargeting() throws IOException, JSONException {
        // given
        // rubicon exchange
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("amp/test-rubicon-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("amp/test-rubicon-bid-response.json"))));

        // appnexus exchange
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/appnexus-exchange"))
                .withRequestBody(equalToJson(jsonFrom("amp/test-appnexus-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("amp/test-appnexus-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("amp/test-cache-request.json"), true, false))
                .willReturn(aResponse()
                        .withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName", "amp/test-cache-matcher-amp.json")
                ));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIn19")
                .when()
                .get("/openrtb2/amp"
                        + "?tag_id=test-amp-stored-request"
                        + "&ow=980"
                        + "&oh=120"
                        + "&timeout=10000000"
                        + "&slot=overwrite-tagId"
                        + "&curl=https%3A%2F%2Fgoogle.com"
                        + "&account=accountId"
                        + "&us_privacy=1YNN");

        // then
        JSONAssert.assertEquals(jsonFrom("amp/test-amp-response.json"), response.asString(),
                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void statusShouldReturnReadyWithinResponseBodyAndHttp200Ok() {
        assertThat(given(SPEC).when().get("/status"))
                .extracting(Response::getStatusCode, response -> response.getBody().asString())
                .containsOnly(200, "{\"application\":{\"status\":\"ok\"}}");
    }

    @Test
    public void optoutShouldSetOptOutFlagAndRedirectToOptOutUrl() throws IOException {
        // given
        WIRE_MOCK_RULE.stubFor(post("/optout")
                .withRequestBody(equalTo("secret=abc&response=recaptcha1"))
                .willReturn(aResponse().withBody("{\"success\": true}")));

        // when
        final Response response = given(SPEC)
                .header("Content-Type", "application/x-www-form-urlencoded")
                // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ==")
                .body("g-recaptcha-response=recaptcha1&optout=1")
                .post("/optout");

        // then
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
        given(SPEC)
                .when()
                .get("/static/index.html")
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void cookieSyncShouldReturnBidderStatusWithExpectedUsersyncInfo() {
        // given
        final String gdprConsent = VendorConsentEncoder.toBase64String(new VendorConsentBuilder()
                .withConsentRecordCreatedOn(Instant.now())
                .withConsentRecordLastUpdatedOn(Instant.now())
                .withConsentLanguage("en")
                .withVendorListVersion(79)
                .withRangeEntries(singletonList(new StartEndRangeEntry(1, 100)))
                .withMaxVendorId(100)
                .withBitField(new HashSet<>(asList(1, 32, 52)))
                .withAllowedPurposeIds(new HashSet<>(asList(1, 3)))
                .build());

        // when
        final CookieSyncResponse cookieSyncResponse = given(SPEC)
                .cookies("host-cookie-name", "host-cookie-uid")
                .body(CookieSyncRequest.of(asList(RUBICON, APPNEXUS, ADFORM), 1, gdprConsent, "1YNN", false, null,
                        null))
                .when()
                .post("/cookie_sync")
                .then()
                .spec(new ResponseSpecBuilder().setDefaultParser(Parser.JSON).build())
                .extract()
                .as(CookieSyncResponse.class);

        // then
        assertThat(cookieSyncResponse.getStatus()).isEqualTo("ok");
        assertThat(cookieSyncResponse.getBidderStatus())
                .hasSize(3)
                .containsOnly(BidderUsersyncStatus.builder()
                                .bidder(RUBICON)
                                .noCookie(true)
                                .usersync(UsersyncInfo.of(
                                        "http://localhost:8000/setuid?bidder=rubicon"
                                                + "&gdpr=1&gdpr_consent=" + gdprConsent
                                                + "&us_privacy=1YNN"
                                                + "&uid=host-cookie-uid",
                                        "redirect", false))
                                .build(),
                        BidderUsersyncStatus.builder()
                                .bidder(APPNEXUS)
                                .noCookie(true)
                                .usersync(UsersyncInfo.of(
                                        "//usersync-url/getuid?http%3A%2F%2Flocalhost%3A8000%2Fsetuid%3Fbidder"
                                                + "%3Dadnxs%26gdpr%3D1%26gdpr_consent%3D" + gdprConsent
                                                + "%26us_privacy%3D1YNN"
                                                + "%26uid%3D%24UID",
                                        "redirect", false))
                                .build(),
                        BidderUsersyncStatus.builder()
                                .bidder(ADFORM)
                                .error("Rejected by TCF")
                                .build());
    }

    @Test
    public void setuidShouldUpdateRubiconUidInUidCookie() throws IOException {
        // when
        final Cookie uidsCookie = given(SPEC)
                // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"},
                // "bday":"2017-08-15T19:47:59.523908376Z"}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0"
                        + "NSJ9LCJiZGF5IjoiMjAxNy0wOC0xNVQxOTo0Nzo1OS41MjM5MDgzNzZaIn0=")
                // this constant is ok to use as long as it coincides with family name
                .queryParam("bidder", RUBICON)
                .queryParam("uid", "updatedUid")
                .queryParam("gdpr", "1")
                .queryParam("gdpr_consent", "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA")
                .when()
                .get("/setuid")
                .then()
                .extract()
                .detailedCookie("uids");

        // then
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
    public void getuidsShouldReturnJsonWithUids() throws JSONException {
        // given and when
        final Response response = given(SPEC)
                // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"},
                // "bday":"2017-08-15T19:47:59.523908376Z"}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0"
                        + "NSJ9LCJiZGF5IjoiMjAxNy0wOC0xNVQxOTo0Nzo1OS41MjM5MDgzNzZaIn0=")
                .when()
                .get("/getuids");

        // then
        JSONAssert.assertEquals("{\"buyeruids\":{\"rubicon\":\"J5VLCWQP-26-CWFT\",\"adnxs\":\"12345\"}}",
                response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void vtrackShouldReturnJsonWithUids() throws JSONException, IOException {
        // given and when
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("vtrack/test-cache-request.json"), true, false))
                .willReturn(aResponse().withBody(jsonFrom("vtrack/test-vtrack-response.json"))));

        final Response response = given(SPEC)
                .when()
                .body(jsonFrom("vtrack/test-vtrack-request.json"))
                .queryParam("a", "14062")
                .post("/vtrack");

        // then
        JSONAssert.assertEquals("{\"responses\":[{\"uuid\":\"94531ab8-c662-4fc7-904e-6b5d3be43b1a\"}]}",
                response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void optionsRequestShouldRespondWithOriginalPolicyHeaders() {
        // when
        final Response response = given(SPEC)
                .header("Origin", "origin.com")
                .header("Access-Control-Request-Method", "GET")
                .when()
                .options("/");

        // then
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("origin.com");
        assertThat(response.header("Access-Control-Allow-Methods")).contains(asList("HEAD", "OPTIONS", "GET", "POST"));
        assertThat(response.header("Access-Control-Allow-Headers"))
                .isEqualTo("Origin,Accept,X-Requested-With,Content-Type");
    }

    @Test
    public void biddersParamsShouldReturnBidderSchemas() throws JSONException {
        // given
        final Map<String, JsonNode> bidderNameToSchema = getBidderNamesFromParamFiles().stream()
                .collect(Collectors.toMap(Function.identity(), ApplicationTest::jsonSchemaToJsonNode));

        // when
        final Response response = given(SPEC)
                .when()
                .get("/bidders/params");

        // then
        JSONAssert.assertEquals(bidderNameToSchema.toString(), response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void infoBiddersShouldReturnRegisteredActiveBidderNames() throws JSONException, IOException {
        // given
        final List<String> bidderNames = getBidderNamesFromParamFiles();
        final List<String> bidderAliases = getBidderAliasesFromConfigFiles();

        // when
        final Response response = given(SPEC)
                .when()
                .get("/info/bidders");

        // then
        final String expectedResponse = CollectionUtils.union(bidderNames, bidderAliases).toString();
        JSONAssert.assertEquals(expectedResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void infoBidderDetailsShouldReturnMetadataForBidder() throws IOException {
        given(SPEC)
                .when()
                .get("/info/bidders/rubicon")
                .then()
                .assertThat()
                .body(Matchers.equalTo(jsonFrom("info-bidders/test-info-bidder-details-response.json")));
    }

    @Test
    public void eventHandlerShouldRespondWithTrackingPixel() throws IOException {
        final Response response = given(SPEC)
                .queryParam("t", "win")
                .queryParam("b", "bidId")
                .queryParam("a", "14062")
                .queryParam("f", "i")
                .queryParam("bidder", "bidder")
                .queryParam("ts", "1000")
                .get("/event");

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getHeaders()).contains(new Header("content-type", "image/png"));
        assertThat(response.getBody().asByteArray())
                .isEqualTo(ResourceUtil.readByteArrayFromClassPath("static/tracking-pixel.png"));
    }

    @Test
    public void shouldAskExchangeWithUpdatedSettingsFromCache() throws IOException, JSONException {
        // given
        // update stored settings cache
        given(ADMIN_SPEC)
                .body(jsonFrom("cache/update/test-update-settings-request.json"))
                .when()
                .post("/storedrequests/openrtb2")
                .then()
                .assertThat()
                .body(Matchers.equalTo(""))
                .statusCode(200);

        // rubicon bid response
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("cache/update/test-rubicon-bid-request1.json")))
                .willReturn(aResponse().withBody(jsonFrom("cache/update/test-rubicon-bid-response1.json"))));

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("cache/update/test-rubicon-bid-request2.json")))
                .willReturn(aResponse().withBody(jsonFrom("cache/update/test-rubicon-bid-response2.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for
                // {"uids":{"rubicon":"J5VLCWQP-26-CWFT"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIn19")
                .body(jsonFrom("cache/update/test-auction-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "cache/update/test-auction-response.json", response, singletonList(RUBICON));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void versionHandlerShouldRespondWithCommitRevision() {
        given(ADMIN_SPEC)
                .get("/version")
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void adminHandlerShouldRespondWithOk() {
        given(ADMIN_SPEC)
                .get("/admin?logging=error&records=1200")
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void currencyRatesHandlerShouldRespondWithLastUpdateDate() {
        // given
        final Instant currentTime = Instant.now();

        // ask endpoint after some time to ensure currency rates have already been fetched
        Vertx.vertx().setTimer(1000L, ignored -> {
            // when
            final Response response = given(ADMIN_SPEC).get("/currency-rates");

            // then
            final String lastUpdateValue = response.jsonPath().getString("last_update");
            final Instant lastUpdateTime = Instant.parse(lastUpdateValue);
            assertThat(currentTime).isAfter(lastUpdateTime);
        });
    }

    @Test
    public void invalidateSettingsCacheShouldReturnExpectedResponse() {
        given(ADMIN_SPEC)
                .body("{\"requests\":[],\"imps\":[]}")
                .when()
                .delete("/storedrequests/openrtb2")
                .then()
                .assertThat()
                .body(Matchers.equalTo(""))
                .statusCode(200);
    }

    @Test
    public void updateAmpSettingsCacheShouldReturnExpectedResponse() {
        given(ADMIN_SPEC)
                .body("{\"requests\":{},\"imps\":{}}")
                .when()
                .post("/storedrequests/amp")
                .then()
                .assertThat()
                .body(Matchers.equalTo(""))
                .statusCode(200);
    }

    @Test
    public void invalidateAmpSettingsCacheShouldReturnExpectedResponse() {
        given(ADMIN_SPEC)
                .body("{\"requests\":[],\"imps\":[]}")
                .when()
                .delete("/storedrequests/amp")
                .then()
                .assertThat()
                .body(Matchers.equalTo(""))
                .statusCode(200);
    }

    private Uids decodeUids(String value) throws IOException {
        return mapper.readValue(Base64.getUrlDecoder().decode(value), Uids.class);
    }

    private static List<String> getBidderNamesFromParamFiles() {
        final File biddersFolder = new File("src/main/resources/static/bidder-params");
        final String[] listOfFiles = biddersFolder.list();
        if (listOfFiles != null) {
            return Arrays.stream(listOfFiles)
                    .map(s -> s.substring(0, s.indexOf('.')))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private static List<String> getBidderAliasesFromConfigFiles() throws IOException {
        final String folderPath = "src/main/resources/bidder-config";
        final File folder = new File(folderPath);
        final String[] files = folder.list();
        if (files != null) {
            final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

            final List<String> aliases = new ArrayList<>();
            for (String fileName : files) {
                final JsonNode configNode = mapper.readValue(new File(folderPath, fileName), JsonNode.class);
                final JsonNode aliasesNode = configNode.get("adapters").fields().next().getValue().get("aliases");

                if (!aliasesNode.isNull()) {
                    for (String alias : aliasesNode.textValue().split(",")) {
                        aliases.add(alias.trim());
                    }
                }
            }
            return aliases;
        }
        return Collections.emptyList();
    }

    private static JsonNode jsonSchemaToJsonNode(String bidderName) {
        final String path = "static/bidder-params/" + bidderName + ".json";
        try {
            return mapper.readTree(ResourceUtil.readFromClasspath(path));
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    String.format("Exception occurred during %s bidder schema processing: %s",
                            bidderName, e.getMessage()));
        }
    }
}
