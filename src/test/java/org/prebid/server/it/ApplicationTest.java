package org.prebid.server.it;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.iabtcf.encoder.TCStringEncoder;
import com.iabtcf.utils.BitSetIntIterable;
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
import org.apache.commons.collections4.CollectionUtils;
import org.hamcrest.Matchers;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.bidder.UsersyncMethodType;
import org.prebid.server.cookie.model.CookieSyncStatus;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@RunWith(SpringRunner.class)
public class ApplicationTest extends IntegrationTest {

    private static final String APPNEXUS = "appnexus";
    private static final String APPNEXUS_COOKIE_FAMILY = "adnxs";
    private static final String RUBICON = "rubicon";
    private static final String GENERIC = "generic";
    private static final String GENERIC_ALIAS = "genericAlias";

    private static final int ADMIN_PORT = 8060;

    private static final RequestSpecification ADMIN_SPEC = new RequestSpecBuilder()
            .setBaseUri("http://localhost")
            .setPort(ADMIN_PORT)
            .setConfig(RestAssuredConfig.config()
                    .objectMapperConfig(new ObjectMapperConfig(new Jackson2Mapper((aClass, s) -> mapper))))
            .build();

    @Test
    public void testOpenrtb2AuctionCoreFunctionality() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/generic_core_functionality/test-generic-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/generic_core_functionality/test-generic-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/generic_core_functionality/test-cache-generic-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/generic_core_functionality/test-cache-generic-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"rubicon":"RUB-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJSVUItVUlEIn19")
                .body(jsonFrom("openrtb2/generic_core_functionality/test-auction-generic-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/generic_core_functionality/test-auction-generic-response.json",
                response, singletonList("generic"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void openrtb2MultiBidAuctionShouldRespondWithMoreThanOneBid() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/multi_bid/test-generic-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom(
                        "openrtb2/multi_bid/test-generic-bid-response-1.json"))));

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/genericAlias-exchange"))
                .withRequestBody(equalToJson(
                        jsonFrom(
                                "openrtb2/multi_bid/test-genericAlias-bid-request-1.json"
                        )))
                .willReturn(aResponse().withBody(jsonFrom(
                        "openrtb2/multi_bid/test-genericAlias-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToBidCacheRequest(
                        jsonFrom(
                                "openrtb2/multi_bid/test-cache-generic-genericAlias-request.json"
                        )))
                .willReturn(aResponse()
                        .withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName",
                                "openrtb2/multi_bid/test-cache-matcher-generic-genericAlias.json")));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ==")
                .body(jsonFrom("openrtb2/multi_bid/test-auction-generic-genericAlias-request.json"))
                .post("/openrtb2/auction");

        // then
        assertJsonEquals("openrtb2/multi_bid/test-auction-generic-genericAlias-response.json",
                response, asList(GENERIC, GENERIC_ALIAS));
    }

    @Test
    public void openrtb2AuctionShouldRespondWithStoredBidResponse() throws IOException, JSONException {
        // given
        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/storedresponse/test-cache-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/storedresponse/test-cache-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ==")
                .body(jsonFrom("openrtb2/storedresponse/test-auction-request.json"))
                .post("/openrtb2/auction");

        // then
        assertJsonEquals("openrtb2/storedresponse/test-auction-response.json",
                response, singletonList(GENERIC), openrtbCacheDebugCustomization());
    }

    @Test
    public void ampShouldReturnTargeting() throws IOException, JSONException {
        // given
        // generic exchange
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .withRequestBody(equalToJson(jsonFrom("amp/test-generic-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("amp/test-generic-bid-response.json"))));

        // genericAlias exchange
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/genericAlias-exchange"))
                .withRequestBody(equalToJson(jsonFrom("amp/test-genericAlias-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("amp/test-genericAlias-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToBidCacheRequest(jsonFrom("amp/test-cache-request.json")))
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
                        + "&targeting=%7B%22gam-key1%22%3A%22val1%22%2C%22gam-key2%22%3A%22val2%22%7D"
                        + "&curl=https%3A%2F%2Fgoogle.com"
                        + "&account=accountId"
                        + "&addtl_consent=someConsent"
                        + "&gdpr_applies=false"
                        + "&consent_type=3"
                        + "&consent_string=1YNN");

        // then
        assertJsonEquals("amp/test-amp-response.json", response, asList(GENERIC, GENERIC_ALIAS));
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
                // this uids cookie value stands for { "tempUIDs":{ "rubicon":{ "uid": "J5VLCWQP-26-CWFT",
                // "expires": "2023-12-05T19:00:05.103329-03:00" }, "adnxs":{ "uid": "12345",
                // "expires": "2023-12-05T19:00:05.103329-03:00" } } }
                .cookie("uids", "eyAidGVtcFVJRHMiOnsgInJ1Ymljb24iOnsgInVpZCI6ICJKNVZMQ1dRUC0yNi1DV0ZUIiwg"
                        + "ImV4cGlyZXMiOiAiMjAyMy0xMi0wNVQxOTowMDowNS4xMDMzMjktMDM6MDAiIH0sICJhZG5"
                        + "4cyI6eyAidWlkIjogIjEyMzQ1IiwgImV4cGlyZXMiOiAiMjAyMy0xMi0wNVQxOTowMDowNS"
                        + "4xMDMzMjktMDM6MDAiIH0gfSB9")
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
        final String gdprConsent = TCStringEncoder.newBuilder()
                .version(2)
                .consentLanguage("EN")
                .vendorListVersion(52)
                .tcfPolicyVersion(2)
                .addPurposesConsent(BitSetIntIterable.from(1))
                .addVendorConsent(BitSetIntIterable.from(1, 32, 52))
                .encode();

        // when
        final CookieSyncResponse cookieSyncResponse = given(SPEC)
                .cookies("host-cookie-name", "host-cookie-uid")
                .body(CookieSyncRequest.builder()
                        .bidders(Set.of(RUBICON, APPNEXUS))
                        .gdpr(1)
                        .gdprConsent(gdprConsent)
                        .usPrivacy("1YNN")
                        .coopSync(false)
                        .build())
                .when()
                .post("/cookie_sync")
                .then()
                .spec(new ResponseSpecBuilder().setDefaultParser(Parser.JSON).build())
                .extract()
                .as(CookieSyncResponse.class);

        // then
        assertThat(cookieSyncResponse.getStatus()).isEqualTo(CookieSyncStatus.OK);
        assertThat(cookieSyncResponse.getBidderStatus()).containsExactlyInAnyOrder(
                BidderUsersyncStatus.builder()
                        .bidder(RUBICON)
                        .noCookie(true)
                        .usersync(UsersyncInfo.of(
                                "http://localhost:8080/setuid?bidder=rubicon"
                                        + "&gdpr=1&gdpr_consent=" + gdprConsent
                                        + "&us_privacy=1YNN"
                                        + "&gpp="
                                        + "&gpp_sid="
                                        + "&f=i"
                                        + "&uid=host-cookie-uid",
                                UsersyncMethodType.REDIRECT, false))
                        .build(),
                BidderUsersyncStatus.builder()
                        .bidder(APPNEXUS_COOKIE_FAMILY)
                        .noCookie(true)
                        .usersync(UsersyncInfo.of(
                                "//usersync-url/getuid?http%3A%2F%2Flocalhost%3A8080%2Fsetuid%3Fbidder"
                                        + "%3Dadnxs%26gdpr%3D1%26gdpr_consent%3D" + gdprConsent
                                        + "%26us_privacy%3D1YNN"
                                        + "%26gpp%3D"
                                        + "%26gpp_sid%3D"
                                        + "%26f%3Db"
                                        + "%26uid%3D%24UID",
                                UsersyncMethodType.IFRAME, false))
                        .build());
    }

    @Test
    public void setuidShouldUpdateRubiconUidInUidCookie() throws IOException {
        // when
        final Cookie uidsCookie = given(SPEC)
                // this uids cookie value stands for { "tempUIDs":{ "rubicon":{ "uid":"J5VLCWQP-26-CWFT",
                // "expires":"2023-12-05T19:00:05.103329-03:00" } } }
                .cookie("uids", "eyAidGVtcFVJRHMiOnsgInJ1Ymljb24iOnsgInVpZCI6Iko1VkxDV1FQ"
                        + "LTI2LUNXRlQiLCAiZXhwaXJlcyI6IjIwMjMtMTItMDVUMTk6MDA6MDUuMTAzMzI5LTAzOjAwIiB9IH0gfQ==")
                // this constant is ok to use as long as it coincides with family name
                .queryParam("bidder", RUBICON)
                .queryParam("uid", "updatedUid")
                .queryParam("gdpr", "1")
                .queryParam("gdpr_consent", "CPBCKiyPBCKiyAAAAAENA0CAAIAAAAAAACiQAaQAwAAgAgABoAAAAAA")
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
        assertThat(uids.getUids())
                .extracting(Map::keySet)
                .extracting(ArrayList::new)
                .asList()
                .containsExactly(RUBICON);
        assertThat(uids.getUids().get(RUBICON).getUid()).isEqualTo("updatedUid");
        assertThat(uids.getUids().get(RUBICON).getExpires().toInstant())
                .isCloseTo(Instant.now().plus(14, ChronoUnit.DAYS), within(10, ChronoUnit.SECONDS));
    }

    @Test
    public void getuidsShouldReturnJsonWithUids() throws JSONException, IOException {
        // given and when
        final Response response = given(SPEC)
                // this uids cookie value stands for { "tempUIDs":{ "rubicon":{ "uid": "J5VLCWQP-26-CWFT",
                // "expires": "2023-12-05T19:00:05.103329-03:00" }, "adnxs":{ "uid": "12345",
                // "expires": "2023-12-05T19:00:05.103329-03:00" } } }
                .cookie("uids", "eyAidGVtcFVJRHMiOnsgInJ1Ymljb24iOnsgInVpZCI6ICJKNVZMQ1dRUC0yNi1DV0ZUIiwg"
                        + "ImV4cGlyZXMiOiAiMjAyMy0xMi0wNVQxOTowMDowNS4xMDMzMjktMDM6MDAiIH0sICJhZG5"
                        + "4cyI6eyAidWlkIjogIjEyMzQ1IiwgImV4cGlyZXMiOiAiMjAyMy0xMi0wNVQxOTowMDowNS"
                        + "4xMDMzMjktMDM6MDAiIH0gfSB9")
                .when()
                .get("/getuids");

        // then
        assertJsonEquals("uid/test-uid-response.json", response, emptyList());
    }

    @Test
    public void vtrackShouldReturnJsonWithUids() throws JSONException, IOException {
        // given and when
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToBidCacheRequest(jsonFrom("vtrack/test-cache-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("vtrack/test-cache-response.json"))));

        final Response response = given(SPEC)
                .when()
                .body(jsonFrom("vtrack/test-vtrack-request.json"))
                .queryParam("a", "14062")
                .post("/vtrack");

        // then
        assertJsonEquals("vtrack/test-vtrack-response.json", response, emptyList());
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
    public void biddersParamsShouldReturnBidderSchemas() throws JSONException, IOException {
        // when
        final Response response = given(SPEC)
                .when()
                .get("/bidders/params");

        // then
        final Map<String, JsonNode> responseAsMap = jacksonMapper.decodeValue(response.asString(),
                new TypeReference<>() {
                });

        final List<String> bidders = getBidderNamesFromParamFiles();
        final Map<String, String> aliases = getBidderAliasesFromConfigFiles();
        //todo: necessary since the config file is not a source of truth in terms of defining aliases for the bidders
        // the suggestion is eventually resolving static json file name from the bidder config file
        // and not from the name hard-coded in the Configuration class
        aliases.put("cadent_aperture_mx", "emx_digital");
        final Map<String, JsonNode> expectedMap = CollectionUtils.union(bidders, aliases.keySet()).stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        bidderName -> jsonSchemaToJsonNode(aliases.getOrDefault(bidderName, bidderName))));

        assertThat(responseAsMap.keySet()).hasSameElementsAs(expectedMap.keySet());
        assertThat(responseAsMap).containsAllEntriesOf(expectedMap);

        JSONAssert.assertEquals(expectedMap.toString(), response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void infoBiddersShouldReturnRegisteredActiveBidderNames() throws IOException {
        // when
        final Response response = given(SPEC)
                .when()
                .queryParam("enabledonly", "false")
                .get("/info/bidders");

        // then
        final List<String> responseAsList = jacksonMapper.decodeValue(response.asString(),
                new TypeReference<>() {
                });

        final List<String> bidders = getBidderNamesFromParamFiles();
        final Map<String, String> aliases = getBidderAliasesFromConfigFiles();
        final Collection<String> expectedBidders = CollectionUtils.union(bidders, aliases.keySet());

        assertThat(responseAsList).hasSameElementsAs(expectedBidders);
    }

    @Test
    public void infoBiddersShouldReturnBaseAdaptersBidderNames() throws IOException {
        // when
        final Response response = given(SPEC)
                .when()
                .queryParam("baseadaptersonly", "true")
                .get("/info/bidders");

        // then
        final List<String> responseAsList = jacksonMapper.decodeValue(response.asString(),
                new TypeReference<>() {
                });

        final Set<String> expectedBidders = new HashSet<>(getBidderNamesFromParamFiles());
        final Map<String, String> aliases = getBidderAliasesFromConfigFiles();
        expectedBidders.removeAll(aliases.keySet());

        assertThat(responseAsList).hasSameElementsAs(expectedBidders);
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

        // generic bid response
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .withRequestBody(equalToJson(jsonFrom("cache/update/test-generic-bid-request1.json")))
                .willReturn(aResponse().withBody(jsonFrom("cache/update/test-generic-bid-response1.json"))));

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/genericAlias-exchange"))
                .withRequestBody(equalToJson(jsonFrom("cache/update/test-genericAlias-bid-request2.json")))
                .willReturn(aResponse().withBody(jsonFrom("cache/update/test-genericAlias-bid-response2.json"))));

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
        assertJsonEquals("cache/update/test-auction-response.json", response, asList(GENERIC, GENERIC_ALIAS));
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
    public void loggingHttpInteractionShouldRespondWithOk() {
        given(ADMIN_SPEC)
                .get("/logging/httpinteraction?limit=100")
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void loggingChangeLevekShouldRespondWithOk() {
        given(ADMIN_SPEC)
                .get("/logging/changelevel?level=info&duration=1")
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void currencyRatesHandlerShouldReturnExpectedResponse() {
        given(ADMIN_SPEC)
                .when()
                .get("/currency/rates")
                .then()
                .assertThat()
                .body("active", Matchers.equalTo(true))
                .statusCode(200);
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

    @Test
    public void traceHandlerShouldReturn200Ok() {
        given(ADMIN_SPEC)
                .when()
                .param("level", "error")
                .param("duration", "1000")
                .param("account", "1001")
                .param("bidderCode", "rubicon")
                .post("/pbs-admin/tracelog")
                .then()
                .assertThat()
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
                    .toList();
        }
        return Collections.emptyList();
    }

    private static Map<String, String> getBidderAliasesFromConfigFiles() throws IOException {
        final String folderPath = "src/main/resources/bidder-config";
        final File folder = new File(folderPath);
        final String[] files = folder.list();
        if (files != null) {
            final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

            final Map<String, String> aliases = new HashMap<>();
            for (String fileName : files) {
                final JsonNode configNode = mapper.readValue(new File(folderPath, fileName), JsonNode.class);
                final Map.Entry<String, JsonNode> bidderEntry = configNode.get("adapters").fields().next();
                final String bidderName = bidderEntry.getKey();
                final JsonNode aliasesNode = bidderEntry.getValue().get("aliases");

                if (aliasesNode != null && aliasesNode.isObject()) {
                    final Iterator<String> iterator = aliasesNode.fieldNames();
                    while (iterator.hasNext()) {
                        aliases.put(iterator.next().trim(), bidderName);
                    }
                }
            }
            return aliases;
        }
        return Collections.emptyMap();
    }

    private static JsonNode jsonSchemaToJsonNode(String bidderName) {
        final String path = "static/bidder-params/" + bidderName + ".json";
        try {
            return mapper.readTree(ResourceUtil.readFromClasspath(path));
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Exception occurred during %s bidder schema processing: %s".formatted(bidderName, e.getMessage()));
        }
    }
}
