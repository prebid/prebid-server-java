package org.prebid.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.iab.gdpr.consent.VendorConsentEncoder;
import com.iab.gdpr.consent.implementation.v1.VendorConsentBuilder;
import com.iab.gdpr.consent.range.StartEndRangeEntry;
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
import io.vertx.core.json.JsonObject;
import org.hamcrest.Matchers;
import org.json.JSONException;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.cache.proto.request.BidCacheRequest;
import org.prebid.server.cache.proto.request.PutObject;
import org.prebid.server.cache.proto.response.BidCacheResponse;
import org.prebid.server.cache.proto.response.CacheObject;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.trustStore;
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
    private static final String ADKERNELADN = "adkernelAdn";
    private static final String RHYTHMONE = "rhythmone";
    private static final String PULSEPOINT = "pulsepoint";
    private static final String IX = "ix";
    private static final String LIFESTREET = "lifestreet";
    private static final String PUBMATIC = "pubmatic";
    private static final String CONVERSANT = "conversant";
    private static final String ADFORM = "adform";
    private static final String BRIGHTROLL = "brightroll";
    private static final String SOVRN = "sovrn";
    private static final String OPENX = "openx";
    private static final String ADTELLIGENT = "adtelligent";
    private static final String EPLANNING = "eplanning";
    private static final String SOMOAUDIENCE = "somoaudience";
    private static final String BEACHFRONT = "beachfront";
    private static final String APPNEXUS_ALIAS = "appnexusAlias";
    private static final String CONVERSANT_ALIAS = "conversantAlias";

    private static final int APP_PORT = 8080;
    private static final int WIREMOCK_PORT = 8090;
    private static final int ADMIN_PORT = 8060;

    @ClassRule
    public static final WireMockClassRule wireMockRule = new WireMockClassRule(wireMockConfig().port(WIREMOCK_PORT).extensions(CacheResponseTransformer.class));

    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    private static final RequestSpecification spec = new RequestSpecBuilder()
            .setBaseUri("http://localhost")
            .setPort(APP_PORT)
            .setConfig(RestAssuredConfig.config()
                    .objectMapperConfig(new ObjectMapperConfig(new Jackson2Mapper((aClass, s) -> mapper))))
            .build();

    private static final RequestSpecification adminSpec = new RequestSpecBuilder()
            .setBaseUri("http://localhost")
            .setPort(ADMIN_PORT)
            .setConfig(RestAssuredConfig.config()
                    .objectMapperConfig(new ObjectMapperConfig(new Jackson2Mapper((aClass, s) -> mapper))))
            .build();

    @BeforeClass
    public static void setUp() throws IOException {
        wireMockRule.stubFor(get(urlPathEqualTo("/periodic-update"))
                .willReturn(aResponse().withBody(jsonFrom("storedrequests/test-periodic-refresh.json"))));
        wireMockRule.stubFor(get(urlPathEqualTo("/currency-rates"))
                .willReturn(aResponse().withBody(jsonFrom("currency/latest.json"))));
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromConversant() throws IOException, JSONException {
        // given
        // conversant bid response for imp 4
        wireMockRule.stubFor(post(urlPathEqualTo("/conversant-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/conversant/test-conversant-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/conversant/test-conversant-bid-response-1.json"))));

        // conversant bid response for imp 4 with alias parameters
        wireMockRule.stubFor(post(urlPathEqualTo("/conversant-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/conversant/test-conversant-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/conversant/test-conversant-bid-response-2.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/conversant/test-cache-conversant-request.json"), true, false))
                .willReturn(aResponse()
                        .withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName", "openrtb2/conversant/test-cache-matcher-conversant.json")
                ));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"conversant":"CV-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImNvbnZlcnNhbnQiOiJDVi1VSUQifX0=")
                .body(jsonFrom("openrtb2/conversant/test-auction-conversant-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/conversant/test-auction-conversant-response.json",
                response, asList(CONVERSANT, CONVERSANT_ALIAS));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromIx() throws IOException, JSONException {
        // given
        // ix bid response for imp 6 and imp 61
        wireMockRule.stubFor(post(urlPathEqualTo("/ix-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/ix/test-ix-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/ix/test-ix-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/ix/test-cache-ix-request.json"), true, false))
                .willReturn(aResponse()
                        .withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName", "openrtb2/ix/test-cache-matcher-ix.json")
                ));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"ix":"IE-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7Iml4IjoiSUUtVUlEIn19")
                .body(jsonFrom("openrtb2/ix/test-auction-ix-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/ix/test-auction-ix-response.json",
                response, singletonList(IX));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromFacebook() throws IOException, JSONException {
        // given
        // facebook bid response for imp 5
        wireMockRule.stubFor(post(urlPathEqualTo("/audienceNetwork-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/facebook/test-facebook-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/facebook/test-facebook-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/facebook/test-cache-facebook-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/facebook/test-cache-facebook-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"audienceNetwork":"FB-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImF1ZGllbmNlTmV0d29yayI6IkZCLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/facebook/test-auction-facebook-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/facebook/test-auction-facebook-response.json",
                response, singletonList(FACEBOOK));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromLifestreet() throws IOException, JSONException {
        // given
        // lifestreet bid response for imp 7
        wireMockRule.stubFor(post(urlPathEqualTo("/lifestreet-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/lifestreet/test-lifestreet-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/lifestreet/test-lifestreet-bid-response-1.json"))));

        // lifestreet bid response for imp 71
        wireMockRule.stubFor(post(urlPathEqualTo("/lifestreet-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/lifestreet/test-lifestreet-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/lifestreet/test-lifestreet-bid-response-2.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"lifestreet":"LS-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImxpZmVzdHJlZXQiOiJMUy1VSUQifX0=")
                .body(jsonFrom("openrtb2/lifestreet/test-auction-lifestreet-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/lifestreet/test-auction-lifestreet-response.json",
                response, singletonList(LIFESTREET));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromPulsepoint() throws IOException, JSONException {
        // given
        // pulsepoint bid response for imp 8
        wireMockRule.stubFor(post(urlPathEqualTo("/pulsepoint-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/pulsepoint/test-pulsepoint-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/pulsepoint/test-pulsepoint-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/pulsepoint/test-cache-pulsepoint-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/pulsepoint/test-cache-pulsepoint-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"pulsepoint":"PP-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InB1bHNlcG9pbnQiOiJQUC1VSUQifX0=")
                .body(jsonFrom("openrtb2/pulsepoint/test-auction-pulsepoint-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/pulsepoint/test-auction-pulsepoint-response.json",
                response, singletonList(PULSEPOINT));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromPubmatic() throws IOException, JSONException {
        // given
        // pubmatic bid response for imp 9
        wireMockRule.stubFor(post(urlPathEqualTo("/pubmatic-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/pubmatic/test-pubmatic-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/pubmatic/test-pubmatic-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/pubmatic/test-cache-pubmatic-request.json"), true, false))
                .willReturn(aResponse()
                .withTransformers("cache-response-transformer")
                .withTransformerParameter("matcherName", "openrtb2/pubmatic/test-cache-matcher-pubmatic.json")
        ));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"pubmatic":"PM-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InB1Ym1hdGljIjoiUE0tVUlEIn19")
                .body(jsonFrom("openrtb2/pubmatic/test-auction-pubmatic-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/pubmatic/test-auction-pubmatic-response.json",
                response, singletonList(PUBMATIC));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdform() throws IOException, JSONException {
        // given
        // adform bid response for imp 12
        wireMockRule.stubFor(get(urlPathEqualTo("/adform-exchange"))
                .withQueryParam("CC", equalTo("1"))
                .withQueryParam("rp", equalTo("4"))
                .withQueryParam("fd", equalTo("1"))
                .withQueryParam("stid", equalTo("tid"))
                .withQueryParam("pt", equalTo("gross"))
                .withQueryParam("ip", equalTo("192.168.244.1"))
                .withQueryParam("adid", equalTo("ifaId"))
                .withQueryParam("gdpr", equalTo("0"))
                .withQueryParam("gdpr_consent", equalTo("consentValue"))
                // bWlkPTE1 is Base64 encoded "mid=15"
                .withQueryParam("bWlkPTE1", equalTo(""))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Request-Agent", equalTo("PrebidAdapter 0.1.2"))
                .withHeader("X-Forwarded-For", equalTo("192.168.244.1"))
                .withHeader("Cookie", equalTo(
                        "uid=AF-UID;DigiTrust.v1.identity="
                                // Base 64 encoded {"id":"id","version":1,"keyv":123,"privacy":{"optout":false}}
                                + "eyJpZCI6ImlkIiwidmVyc2lvbiI6MSwia2V5diI6MTIzLCJwcml2YWN5Ijp7Im9wdG91dCI6ZmFsc2V9fQ"))
                .withRequestBody(equalTo(""))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adform/test-adform-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adform/test-cache-adform-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adform/test-cache-adform-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"adform":"AF-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImFkZm9ybSI6IkFGLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/adform/test-auction-adform-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/adform/test-auction-adform-response.json",
                response, singletonList(ADFORM));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);

    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSovrn() throws IOException, JSONException {
        // given
        // sovrn bid response for imp 13
        wireMockRule.stubFor(post(urlPathEqualTo("/sovrn-exchange"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("192.168.244.1"))
                .withHeader("DNT", equalTo("2"))
                .withHeader("Accept-Language", equalTo("en"))
                .withCookie("ljt_reader", equalTo("990011"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/sovrn/test-sovrn-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/sovrn/test-sovrn-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/sovrn/test-cache-sovrn-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/sovrn/test-cache-sovrn-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"sovrn":"990011"}}
                .cookie("uids", "eyJ1aWRzIjp7InNvdnJuIjoiOTkwMDExIn19")
                .body(jsonFrom("openrtb2/sovrn/test-auction-sovrn-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/sovrn/test-auction-sovrn-response.json",
                response, singletonList(SOVRN));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdtelligent() throws IOException, JSONException {
        // given
        // adtelligent bid response for imp 14
        wireMockRule.stubFor(post(urlPathEqualTo("/adtelligent-exchange"))
                .withQueryParam("aid", equalTo("1000"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adtelligent/test-adtelligent-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adtelligent/test-adtelligent-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adtelligent/test-cache-adtelligent-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adtelligent/test-cache-adtelligent-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"adtelligent":"AT-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImFkdGVsbGlnZW50IjoiQVQtVUlEIn19")
                .body(jsonFrom("openrtb2/adtelligent/test-auction-adtelligent-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/adtelligent/test-auction-adtelligent-response.json",
                response, singletonList(ADTELLIGENT));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromOpenx() throws IOException, JSONException {
        // given
        // openx bid response for imp 011 and 02
        wireMockRule.stubFor(post(urlPathEqualTo("/openx-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/openx/test-openx-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/openx/test-openx-bid-response-1.json"))));

        // openx bid response for imp 03
        wireMockRule.stubFor(post(urlPathEqualTo("/openx-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/openx/test-openx-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/openx/test-openx-bid-response-2.json"))));

        // openx bid response for imp 04
        wireMockRule.stubFor(post(urlPathEqualTo("/openx-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/openx/test-openx-bid-request-3.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/openx/test-openx-bid-response-3.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/openx/test-cache-openx-request.json"), true, false))
                .willReturn(aResponse()
                        .withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName", "openrtb2/openx/test-cache-matcher-openx.json")
                ));
        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"openx":"OX-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7Im9wZW54IjoiT1gtVUlEIn19")
                .body(jsonFrom("openrtb2/openx/test-auction-openx-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/openx/test-auction-openx-response.json",
                response, singletonList(OPENX));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromBrightroll() throws IOException, JSONException {
        // given
        // brightroll bid response for imp 15
        wireMockRule.stubFor(post(urlPathEqualTo("/brightroll-exchange"))
                .withQueryParam("publisher", equalTo("publisher"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("192.168.244.1"))
                .withHeader("DNT", equalTo("2"))
                .withHeader("Accept-Language", equalTo("en"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/brightroll/test-brightroll-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/brightroll/test-brightroll-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/brightroll/test-cache-brightroll-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/brightroll/test-cache-brightroll-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for{"uids":{"brightroll":"BR-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImJyaWdodHJvbGwiOiJCUi1VSUQifX0=")
                .body(jsonFrom("openrtb2/brightroll/test-auction-brightroll-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/brightroll/test-auction-brightroll-response.json",
                response, singletonList(BRIGHTROLL));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromEplanning() throws IOException, JSONException {
        // given
        // eplanning bid response for imp15
        wireMockRule.stubFor(post(urlPathEqualTo("/eplanning-exchange/exchangeId1"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("192.168.244.1"))
                .withHeader("DNT", equalTo("2"))
                .withHeader("Accept-Language", equalTo("en"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/eplanning/test-eplanning-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/eplanning/test-eplanning-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/eplanning/test-cache-eplanning-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/eplanning/test-cache-eplanning-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{""eplanning":"EP-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImVwbGFubmluZyI6IkVQLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/eplanning/test-auction-eplanning-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/eplanning/test-auction-eplanning-response.json",
                response, singletonList(EPLANNING));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }


    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSomoaudience() throws IOException, JSONException {
        // given
        // Somoaudience bid response for imp 16 & 17
        wireMockRule.stubFor(post(urlPathEqualTo("/somoaudience-exchange"))
                .withQueryParam("s", equalTo("placementId02"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("192.168.244.1"))
                .withHeader("Accept-Language", equalTo("en"))
                .withHeader("DNT", equalTo("2"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/somoaudience/test-somoaudience-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom(
                        "openrtb2/somoaudience/test-somoaudience-bid-response-1.json"))));

        // Somoaudience bid response for imp 18
        wireMockRule.stubFor(post(urlPathEqualTo("/somoaudience-exchange"))
                .withQueryParam("s", equalTo("placementId03"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("192.168.244.1"))
                .withHeader("Accept-Language", equalTo("en"))
                .withHeader("DNT", equalTo("2"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/somoaudience/test-somoaudience-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom(
                        "openrtb2/somoaudience/test-somoaudience-bid-response-2.json"))));

        // Somoaudience bid response for imp 19
        wireMockRule.stubFor(post(urlPathEqualTo("/somoaudience-exchange"))
                .withQueryParam("s", equalTo("placementId04"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("192.168.244.1"))
                .withHeader("Accept-Language", equalTo("en"))
                .withHeader("DNT", equalTo("2"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/somoaudience/test-somoaudience-bid-request-3.json")))
                .willReturn(aResponse().withBody(jsonFrom(
                        "openrtb2/somoaudience/test-somoaudience-bid-response-3.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/somoaudience/test-cache-somoaudience-request.json"), true, false))
                .willReturn(aResponse()
                        .withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName", "openrtb2/somoaudience/test-cache-matcher-somoaudience.json")
                ));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"somoaudience":"SM-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InNvbW9hdWRpZW5jZSI6IlNNLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/somoaudience/test-auction-somoaudience-request.json"))
                .post("/openrtb2/auction");

        //then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/somoaudience/test-auction-somoaudience-response.json",
                response, singletonList(SOMOAUDIENCE));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromBeachfront() throws IOException, JSONException {
        // given
        // beachfront bid response for imp 18
        wireMockRule.stubFor(post(urlPathEqualTo("/beachfront-exchange/video"))
                .withQueryParam("exchange_id", equalTo("beachfrontAppId"))
                .withQueryParam("prebidserver", equalTo(""))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/beachfront/test-beachfront-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/beachfront/test-beachfront-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/beachfront/test-cache-beachfront-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/beachfront/test-cache-beachfront-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                .body(jsonFrom("openrtb2/beachfront/test-auction-beachfront-request.json"))
                // this uids cookie value stands for {"uids":{"beachfront":"BF-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImJlYWNoZnJvbnQiOiJCRi1VSUQifX0=")
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/beachfront/test-auction-beachfront-response.json",
                response, singletonList(BEACHFRONT));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdkerneladn() throws IOException, JSONException {
        // given
        // adkernelAdn bid response for imp 021
        wireMockRule.stubFor(post(urlPathEqualTo("/adkernelAdn-exchange"))
                .withQueryParam("account", equalTo("101"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adkerneladn/test-adkerneladn-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adkerneladn/test-adkerneladn-bid-response-1.json"))));

        // adkernelAdn bid response for imp 022
        wireMockRule.stubFor(post(urlPathEqualTo("/adkernelAdn-exchange"))
                .withQueryParam("account", equalTo("102"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adkerneladn/test-adkerneladn-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adkerneladn/test-adkerneladn-bid-response-2.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adkerneladn/test-cache-adkerneladn-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adkerneladn/test-cache-adkerneladn-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"adkernelAdn":"AK-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImFka2VybmVsQWRuIjoiQUstVUlEIn19")
                .body(jsonFrom("openrtb2/adkerneladn/test-auction-adkerneladn-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/adkerneladn/test-auction-adkerneladn-response.json",
                response, singletonList(ADKERNELADN));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromRhythmone() throws IOException, JSONException {
        // given
        // rhythmone bid response for imp002
        wireMockRule.stubFor(post(urlPathEqualTo("/rhythmone-exchange/72721/0/mvo"))
                .withQueryParam("z", equalTo("1r"))
                .withQueryParam("s2s", equalTo("true"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/rhythmone/test-rhythmone-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/rhythmone/test-rhythmone-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/rhythmone/test-cache-rhythmone-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/rhythmone/test-cache-rhythmone-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"rhythmone":"RO-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InJoeXRobW9uZSI6IlJPLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/rhythmone/test-auction-rhythmone-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/rhythmone/test-auction-rhythmone-response.json",
                response, singletonList(RHYTHMONE));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromRubiconAndAppnexus() throws IOException, JSONException {
        // given
        // rubicon bid response for imp 1
        wireMockRule.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withQueryParam("tk_xint", equalTo("rp-pbs"))
                .withBasicAuth("rubicon_user", "rubicon_password")
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("prebid-server/1.0"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/rubicon_appnexus/test-rubicon-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom(
                        "openrtb2/rubicon_appnexus/test-rubicon-bid-response-1.json"))));

        // rubicon bid response for imp 2
        wireMockRule.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/rubicon_appnexus/test-rubicon-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom(
                        "openrtb2/rubicon_appnexus/test-rubicon-bid-response-2.json"))));

        // appnexus bid response for imp 3
        wireMockRule.stubFor(post(urlPathEqualTo("/appnexus-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/rubicon_appnexus/test-appnexus-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom(
                        "openrtb2/rubicon_appnexus/test-appnexus-bid-response-1.json"))));

        // appnexus bid response for imp 3 with alias parameters
        wireMockRule.stubFor(post(urlPathEqualTo("/appnexus-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/rubicon_appnexus/test-appnexus-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom(
                        "openrtb2/rubicon_appnexus/test-appnexus-bid-response-2.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom(
                        "openrtb2/rubicon_appnexus/test-cache-rubicon-appnexus-request.json"), true, false))
                .willReturn(aResponse()
                        .withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName", "openrtb2/rubicon_appnexus/test-cache-matcher-rubicon-appnexus.json")
                ));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
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

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void ampShouldReturnTargeting() throws IOException, JSONException {
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
                .withRequestBody(equalToJson(jsonFrom("amp/test-cache-request.json"), true, false))
                .willReturn(aResponse()
                        .withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName", "amp/test-cache-matcher-amp.json")
                ));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIn19")
                .when()
                .get("/openrtb2/amp" +
                        "?tag_id=test-amp-stored-request" +
                        "&ow=980" +
                        "&oh=120" +
                        "&timeout=10000000" +
                        "&slot=overwrite-tagId" +
                        "&curl=https%3A%2F%2Fgoogle.com");

        // then
        JSONAssert.assertEquals(jsonFrom("amp/test-amp-response.json"), response.asString(),
                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void auctionShouldRespondWithBidsFromFacebook() throws IOException {
        // given
        // facebook bid response for ad unit 5
        wireMockRule.stubFor(post(urlPathEqualTo("/audienceNetwork-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/facebook/test-facebook-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/facebook/test-facebook-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))//
                .withRequestBody(equalToJson(jsonFrom("auction/facebook/test-cache-facebook-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/facebook/test-cache-facebook-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                //this uids cookie value stands for {"uids":{"audienceNetwork":"FB-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImF1ZGllbmNlTmV0d29yayI6IkZCLVVJRCJ9fQ==")
                .queryParam("debug", "1")
                .body(jsonFrom("auction/facebook/test-auction-facebook-request.json"))
                .post("/auction");

        // then
        assertThat(response.header("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(response.header("Pragma")).isEqualTo("no-cache");
        assertThat(response.header("Expires")).isEqualTo("0");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://www.example.com");

        final String expectedAuctionResponse = legacyAuctionResponseFrom(
                "auction/facebook/test-auction-facebook-response.json",
                response, singletonList(FACEBOOK));
        assertThat(response.asString()).isEqualTo(expectedAuctionResponse);
    }

    @Test
    public void auctionShouldRespondWithBidsFromPulsepoint() throws IOException {
        // given
        // pulsepoint bid response for ad unit 6
        wireMockRule.stubFor(post(urlPathEqualTo("/pulsepoint-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/pulsepoint/test-pulsepoint-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/pulsepoint/test-pulsepoint-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))//
                .withRequestBody(equalToJson(jsonFrom("auction/pulsepoint/test-cache-pulsepoint-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/pulsepoint/test-cache-pulsepoint-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                //this uids cookie value stands for {"uids":{"pulsepoint":"PP-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InB1bHNlcG9pbnQiOiJQUC1VSUQifX0=")
                .queryParam("debug", "1")
                .body(jsonFrom("auction/pulsepoint/test-auction-pulsepoint-request.json"))
                .post("/auction");

        // then
        assertThat(response.header("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(response.header("Pragma")).isEqualTo("no-cache");
        assertThat(response.header("Expires")).isEqualTo("0");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://www.example.com");

        final String expectedAuctionResponse = legacyAuctionResponseFrom(
                "auction/pulsepoint/test-auction-pulsepoint-response.json",
                response, singletonList(PULSEPOINT));
        assertThat(response.asString()).isEqualTo(expectedAuctionResponse);
    }

    @Test
    public void auctionShouldRespondWithBidsFromIx() throws IOException {
        // given
        // pulsepoint bid response for ad unit 7
        wireMockRule.stubFor(post(urlPathEqualTo("/ix-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/ix/test-ix-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/ix/test-ix-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))//
                .withRequestBody(equalToJson(jsonFrom("auction/ix/test-cache-ix-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/ix/test-cache-ix-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                //this uids cookie value stands for {"uids":{"ix":"IE-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7Iml4IjoiSUUtVUlEIn19")
                .queryParam("debug", "1")
                .body(jsonFrom("auction/ix/test-auction-ix-request.json"))
                .post("/auction");

        // then
        assertThat(response.header("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(response.header("Pragma")).isEqualTo("no-cache");
        assertThat(response.header("Expires")).isEqualTo("0");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://www.example.com");

        final String expectedAuctionResponse = legacyAuctionResponseFrom(
                "auction/ix/test-auction-ix-response.json",
                response, singletonList(IX));
        assertThat(response.asString()).isEqualTo(expectedAuctionResponse);
    }

    @Test
    public void auctionShouldRespondWithBidsFromLifestreet() throws IOException {
        // given
        // pulsepoint bid response for ad unit 8
        wireMockRule.stubFor(post(urlPathEqualTo("/lifestreet-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/lifestreet/test-lifestreet-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/lifestreet/test-lifestreet-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))//
                .withRequestBody(equalToJson(jsonFrom("auction/lifestreet/test-cache-lifestreet-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/lifestreet/test-cache-lifestreet-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                //this uids cookie value stands for {"uids":{"lifestreet":"LS-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImxpZmVzdHJlZXQiOiJMUy1VSUQifX0=")
                .queryParam("debug", "1")
                .body(jsonFrom("auction/lifestreet/test-auction-lifestreet-request.json"))
                .post("/auction");

        // then
        assertThat(response.header("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(response.header("Pragma")).isEqualTo("no-cache");
        assertThat(response.header("Expires")).isEqualTo("0");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://www.example.com");

        final String expectedAuctionResponse = legacyAuctionResponseFrom(
                "auction/lifestreet/test-auction-lifestreet-response.json",
                response, singletonList(LIFESTREET));
        assertThat(response.asString()).isEqualTo(expectedAuctionResponse);
    }

    @Test
    public void auctionShouldRespondWithBidsFromPubmatic() throws IOException {
        // given
        // pulsepoint bid response for ad unit 9
        wireMockRule.stubFor(post(urlPathEqualTo("/pubmatic-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/pubmatic/test-pubmatic-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/pubmatic/test-pubmatic-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))//
                .withRequestBody(equalToJson(jsonFrom("auction/pubmatic/test-cache-pubmatic-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/pubmatic/test-cache-pubmatic-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                //this uids cookie value stands for {"uids":{"pubmatic":"PM-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InB1Ym1hdGljIjoiUE0tVUlEIn19")
                .queryParam("debug", "1")
                .body(jsonFrom("auction/pubmatic/test-auction-pubmatic-request.json"))
                .post("/auction");

        // then
        assertThat(response.header("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(response.header("Pragma")).isEqualTo("no-cache");
        assertThat(response.header("Expires")).isEqualTo("0");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://www.example.com");

        final String expectedAuctionResponse = legacyAuctionResponseFrom(
                "auction/pubmatic/test-auction-pubmatic-response.json",
                response, singletonList(PUBMATIC));
        assertThat(response.asString()).isEqualTo(expectedAuctionResponse);
    }

    @Test
    public void auctionShouldRespondWithBidsFromConversant() throws IOException {
        // given
        // pulsepoint bid response for ad unit 10
        wireMockRule.stubFor(post(urlPathEqualTo("/conversant-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/conversant/test-conversant-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/conversant/test-conversant-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))//
                .withRequestBody(equalToJson(jsonFrom("auction/conversant/test-cache-conversant-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/conversant/test-cache-conversant-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                //this uids cookie value stands for {"uids":{"conversant":"CV-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImNvbnZlcnNhbnQiOiJDVi1VSUQifX0=")
                .queryParam("debug", "1")
                .body(jsonFrom("auction/conversant/test-auction-conversant-request.json"))
                .post("/auction");

        // then
        assertThat(response.header("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(response.header("Pragma")).isEqualTo("no-cache");
        assertThat(response.header("Expires")).isEqualTo("0");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://www.example.com");

        final String expectedAuctionResponse = legacyAuctionResponseFrom(
                "auction/conversant/test-auction-conversant-response.json",
                response, asList(CONVERSANT, CONVERSANT_ALIAS));
        assertThat(response.asString()).isEqualTo(expectedAuctionResponse);
    }

    @Test
    public void auctionShouldRespondWithBidsFromSovrn() throws IOException {
        // given
        // sovrn bid response for ad unit 11
        wireMockRule.stubFor(post(urlPathEqualTo("/sovrn-exchange"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("192.168.244.1"))
                .withHeader("DNT", equalTo("10"))
                .withHeader("Accept-Language", equalTo("en"))
                .withCookie("ljt_reader", equalTo("990011"))
                .withRequestBody(equalToJson(jsonFrom("auction/sovrn/test-sovrn-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/sovrn/test-sovrn-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))//
                .withRequestBody(equalToJson(jsonFrom("auction/sovrn/test-cache-sovrn-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/sovrn/test-cache-sovrn-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                //this uids cookie value stands for {"uids":{"sovrn":"990011"}}
                .cookie("uids", "eyJ1aWRzIjp7InNvdnJuIjoiOTkwMDExIn19")
                .queryParam("debug", "1")
                .body(jsonFrom("auction/sovrn/test-auction-sovrn-request.json"))
                .post("/auction");

        // then
        assertThat(response.header("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(response.header("Pragma")).isEqualTo("no-cache");
        assertThat(response.header("Expires")).isEqualTo("0");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://www.example.com");

        final String expectedAuctionResponse = legacyAuctionResponseFrom(
                "auction/sovrn/test-auction-sovrn-response.json",
                response, singletonList(SOVRN));
        assertThat(response.asString()).isEqualTo(expectedAuctionResponse);
    }

    @Test
    public void auctionShouldRespondWithBidsFromAdform() throws IOException {
        // given
        // adform bid response for ad unit 12
        wireMockRule.stubFor(get(urlPathEqualTo("/adform-exchange"))
                .withQueryParam("CC", equalTo("1"))
                .withQueryParam("rp", equalTo("4"))
                .withQueryParam("fd", equalTo("1"))
                .withQueryParam("stid", equalTo("tid"))
                .withQueryParam("ip", equalTo("192.168.244.1"))
                .withQueryParam("adid", equalTo("ifaId"))
                .withQueryParam("gdpr", equalTo("1"))
                .withQueryParam("gdpr_consent", equalTo("consent1"))
                .withQueryParam("pt", equalTo("gross"))
                // bWlkPTE1 is Base64 encoded "mid=15"
                .withQueryParam("bWlkPTE1", equalTo(""))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Request-Agent", equalTo("PrebidAdapter 0.1.2"))
                .withHeader("X-Forwarded-For", equalTo("192.168.244.1"))
                .withHeader("Cookie", equalTo("uid=AF-UID;DigiTrust.v1.identity"
                        //{"id":"id","version":1,"keyv":123,"privacy":{"optout":true}}
                        + "=eyJpZCI6ImlkIiwidmVyc2lvbiI6MSwia2V5diI6MTIzLCJwcml2YWN5Ijp7Im9wdG91dCI6dHJ1ZX19"))
                .withRequestBody(equalTo(""))
                .willReturn(aResponse().withBody(jsonFrom("auction/adform/test-adform-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))//
                .withRequestBody(equalToJson(jsonFrom("auction/adform/test-cache-adform-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/adform/test-cache-adform-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                //this uids cookie value stands for {"uids":{"adform":"AF-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImFkZm9ybSI6IkFGLVVJRCJ9fQ==")
                .queryParam("debug", "1")
                .body(jsonFrom("auction/adform/test-auction-adform-request.json"))
                .post("/auction");

        // then
        assertThat(response.header("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(response.header("Pragma")).isEqualTo("no-cache");
        assertThat(response.header("Expires")).isEqualTo("0");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://www.example.com");

        final String expectedAuctionResponse = legacyAuctionResponseFrom(
                "auction/adform/test-auction-adform-response.json",
                response, singletonList(ADFORM));
        assertThat(response.asString()).isEqualTo(expectedAuctionResponse);
    }

    @Test
    public void auctionShouldRespondWithBidsFromRubiconAndAppnexus() throws IOException {
        // given
        // rubicon bid response for ad unit 1
        wireMockRule.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withQueryParam("tk_xint", equalTo("rp-pbs"))
                .withBasicAuth("rubicon_user", "rubicon_password")
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("prebid-server/1.0"))
                .withRequestBody(equalToJson(jsonFrom("auction/rubicon_appnexus/test-rubicon-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/rubicon_appnexus/test-rubicon-bid-response-1.json"))));

        // rubicon bid response for ad unit 2
        wireMockRule.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/rubicon_appnexus/test-rubicon-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/rubicon_appnexus/test-rubicon-bid-response-2.json"))));

        // rubicon bid response for ad unit 3
        wireMockRule.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/rubicon_appnexus/test-rubicon-bid-request-3.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/rubicon_appnexus/test-rubicon-bid-response-3.json"))));

        // appnexus bid response for ad unit 4
        wireMockRule.stubFor(post(urlPathEqualTo("/appnexus-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/rubicon_appnexus/test-appnexus-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom(
                        "auction/rubicon_appnexus/test-appnexus-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))//
                .withRequestBody(equalToJson(jsonFrom(
                        "auction/rubicon_appnexus/test-cache-rubicon-appnexus-request.json")))
                .willReturn(aResponse().withBody(jsonFrom(
                        "auction/rubicon_appnexus/test-cache-rubicon-appnexus-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
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
    public void statusShouldReturnReadyWithinResponseBodyAndHttp200Ok() {
        assertThat(given(spec).when().get("/status"))
                .extracting(Response::getStatusCode, response -> response.getBody().asString())
                .containsOnly(200, "ok");
    }

    @Test
    public void optoutShouldSetOptOutFlagAndRedirectToOptOutUrl() {
        // given
        wireMockRule.stubFor(post("/optout")
                .withRequestBody(equalTo("secret=abc&response=recaptcha1"))
                .willReturn(aResponse().withBody("{\"success\": true}")));

        // when
        final Response response = given(spec)
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
        given(spec)
                .when()
                .get("/static")
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void cookieSyncShouldReturnBidderStatusWithRubiconUsersyncInfo() {
        // given
        final String gdprConsent = VendorConsentEncoder.toBase64String(new VendorConsentBuilder()
                .withConsentRecordCreatedOn(Instant.now())
                .withConsentRecordLastUpdatedOn(Instant.now())
                .withConsentLanguage("en")
                .withVendorListVersion(79)
                .withRangeEntries(singletonList(new StartEndRangeEntry(1, 100)))
                .withMaxVendorId(100)
                .withBitField(new HashSet<>(asList(1, 52)))
                .withAllowedPurposeIds(new HashSet<>(asList(1, 3)))
                .build());

        // when
        final CookieSyncResponse cookieSyncResponse = given(spec)
                .body(CookieSyncRequest.of(singletonList(RUBICON), 1, gdprConsent))
                .when()
                .post("/cookie_sync")
                .then()
                .spec(new ResponseSpecBuilder().setDefaultParser(Parser.JSON).build())
                .extract()
                .as(CookieSyncResponse.class);

        // then
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("no_cookie",
                singletonList(BidderUsersyncStatus.builder()
                        .bidder(RUBICON)
                        .noCookie(true)
                        .usersync(UsersyncInfo.of(
                                "http://localhost:" + WIREMOCK_PORT
                                        + "/rubicon-usersync?gdpr=1&gdpr_consent=" + gdprConsent,
                                "redirect", false))
                        .build())));
    }

    @Test
    public void setuidShouldUpdateRubiconUidInUidCookie() {
        // when
        final Cookie uidsCookie = given(spec)
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
    public void optionsRequestShouldRespondWithOriginalPolicyHeaders() {
        // when
        final Response response = given(spec)
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
    public void shouldAskExchangeWithUpdatedSettingsFromCache() throws IOException, JSONException {
        // given
        // update stored settings cache
        given(adminSpec)
                .body(jsonFrom("cache/update/test-update-settings-request.json"))
                .when()
                .post("/storedrequests/openrtb2")
                .then()
                .assertThat()
                .body(Matchers.equalTo(""))
                .statusCode(200);

        // rubicon bid response
        wireMockRule.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("cache/update/test-rubicon-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("cache/update/test-rubicon-bid-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for
                // {"uids":{"rubicon":"J5VLCWQP-26-CWFT"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIn19")
                .body(jsonFrom("cache/update/test-auction-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "cache/update/test-auction-response.json",
                response, singletonList(RUBICON));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void versionHandlerShouldRespondWithCommitRevision() {
        given(adminSpec)
                .get("/version")
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void currencyRatesHandlerShouldRespondWithLastUpdateDate() {
        // given
        final Instant testTime = Instant.now();

        // when
        final Response response = given(adminSpec).get("/currency-rates");

        // then
        final String lastUpdateValue = response.jsonPath().getString("last_update");
        final Instant lastUpdateTime = Instant.parse(lastUpdateValue);
        assertThat(testTime).isAfter(lastUpdateTime);
    }

    @Test
    public void invalidateSettingsCacheShouldReturnExpectedResponse() {
        given(adminSpec)
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
        given(adminSpec)
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
        given(adminSpec)
                .body("{\"requests\":[],\"imps\":[]}")
                .when()
                .delete("/storedrequests/amp")
                .then()
                .assertThat()
                .body(Matchers.equalTo(""))
                .statusCode(200);
    }

    private static String jsonFrom(String file) throws IOException {
        // workaround to clear formatting
        return mapper.writeValueAsString(mapper.readTree(ApplicationTest.class.getResourceAsStream(
                ApplicationTest.class.getSimpleName() + "/" + file)));
    }

    private static String cacheResponseFromRequestJson(String requestAsString, String requestCacheIdMapFile) throws
            IOException {
        List<CacheObject> responseCacheObjects = new ArrayList<>();

        try {
            final BidCacheRequest cacheRequest = mapper.treeToValue(mapper.readTree(requestAsString), BidCacheRequest.class);
            final JsonNode jsonNodeMatcher = mapper.readTree(ApplicationTest.class.getResourceAsStream(ApplicationTest.class.getSimpleName() + "/" + requestCacheIdMapFile));
            final List<PutObject> puts = cacheRequest.getPuts();

            for (PutObject putItem : puts) {
                if (putItem.getType().equals("json")) {
                    String id = putItem.getValue().get("id").textValue() + "@" + putItem.getValue().get("price");
                    String uuid = jsonNodeMatcher.get(id).textValue();
                    responseCacheObjects.add(CacheObject.of(uuid));
                } else {
                    String id = putItem.getValue().textValue();
                    if(id == null) { //workaround for conversant
                        id = "null";
                    }
                    String uuid = jsonNodeMatcher.get(id).textValue();
                    responseCacheObjects.add(CacheObject.of(uuid));
                }
            }

            final BidCacheResponse bidCacheResponse = BidCacheResponse.of(responseCacheObjects);
            return Json.encode(bidCacheResponse);
        } catch (IOException e) {
           throw new IOException("Error while matching cache ids");
        }
    }

    private static String legacyAuctionResponseFrom(String templatePath, Response response, List<String> bidders)
            throws IOException {

        return auctionResponseFrom(templatePath, response,
                "bidder_status.find { it.bidder == '%s' }.response_time_ms", bidders);
    }

    private static String openrtbAuctionResponseFrom(String templatePath, Response response, List<String> bidders)
            throws IOException {

        return auctionResponseFrom(templatePath, response, "ext.responsetimemillis.%s", bidders);
    }

    private static String auctionResponseFrom(String templatePath, Response response, String responseTimePath,
                                              List<String> bidders) throws IOException {
        final String hostAndPort = "localhost:" + WIREMOCK_PORT;
        final String cachePath = "/cache";
        final String cacheEndpoint = "http://" + hostAndPort + cachePath;

        String result = jsonFrom(templatePath)
                .replaceAll("\\{\\{ cache.resource_url }}", cacheEndpoint + "?uuid=")
                .replaceAll("\\{\\{ cache.host }}", hostAndPort)
                .replaceAll("\\{\\{ cache.path }}", cachePath)
                .replaceAll("\\{\\{ cache.hostpath }}", cacheEndpoint);

        for (final String bidder : bidders) {
            result = result.replaceAll("\\{\\{ " + bidder + "\\.exchange_uri }}",
                    "http://" + hostAndPort + "/" + bidder + "-exchange");
            result = setResponseTime(response, result, bidder, responseTimePath);
        }

        return result;
    }

    private static String setResponseTime(Response response, String expectedResponseJson, String bidder,
                                          String responseTimePath) {
        final Object val = response.path(format(responseTimePath, bidder));
        final Integer responseTime = val instanceof Integer ? (Integer) val : null;
        if (responseTime != null) {
            expectedResponseJson = expectedResponseJson.replaceAll("\"\\{\\{ " + bidder + "\\.response_time_ms }}\"",
                    responseTime.toString());
        }
        return expectedResponseJson;
    }

    private static Uids decodeUids(String value) {
        return Json.decodeValue(Buffer.buffer(Base64.getUrlDecoder().decode(value)), Uids.class);
    }

    public static class CacheResponseTransformer extends ResponseTransformer {
        @Override
        public com.github.tomakehurst.wiremock.http.Response transform(Request request, com.github.tomakehurst.wiremock.http.Response response, FileSource files, Parameters parameters) {
            final String newResponse;
            try {
                newResponse = cacheResponseFromRequestJson(request.getBodyAsString(), parameters.getString("matcherName"));
            } catch (IOException e) {
                return com.github.tomakehurst.wiremock.http.Response.response().body(e.getMessage()).status(500).build();
            }
            return com.github.tomakehurst.wiremock.http.Response.response().body(newResponse).status(200).build();
        }

        @Override
        public String getName() {
            return "cache-response-transformer";
        }

        @Override
        public boolean applyGlobally() {
            return false;
        }
    }
}
