package org.rtb.vexing;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
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
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.rtb.vexing.model.Uids;
import org.rtb.vexing.model.request.CookieSyncRequest;
import org.rtb.vexing.model.response.BidderStatus;
import org.rtb.vexing.model.response.CookieSyncResponse;
import org.rtb.vexing.model.response.UsersyncInfo;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@RunWith(VertxUnitRunner.class)
public class ApplicationTest extends VertxTest {

    private static final String RUBICON = "rubicon";
    private static final String APPNEXUS = "appnexus";
    private static final String FACEBOOK = "audienceNetwork";
    private static final String PULSEPOINT = "pulsepoint";
    private static final String INDEXEXCHANGE = "indexExchange";

    private static final int APP_PORT = 8080;
    private static final int WIREMOCK_PORT = 8090;

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(WIREMOCK_PORT);
    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    private static final RequestSpecification spec = new RequestSpecBuilder()
            .setBaseUri("http://localhost")
            .setPort(APP_PORT)
            .setConfig(RestAssuredConfig.config()
                    .objectMapperConfig(new ObjectMapperConfig(new Jackson2Mapper((aClass, s) -> mapper))))
            .build();

    private Vertx vertx;

    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();
        final DeploymentOptions options = new DeploymentOptions().setConfig(config());
        vertx.deployVerticle(Application.class.getName(), options, context.asyncAssertSuccess());
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    private static JsonObject config() {
        return new JsonObject()
                .put("external_url", "http://localhost:" + APP_PORT)
                .put("http.port", APP_PORT)
                .put("http-client.max-pool-size", 32768)
                .put("http-client.connect-timeout-ms", 1000)
                .put("default-timeout-ms", 250L)
                .put("adapters.rubicon.endpoint",
                        "http://localhost:" + WIREMOCK_PORT + "/rubicon-exchange?tk_xint=rp-pbs")
                .put("adapters.rubicon.usersync_url", "http://localhost:" + WIREMOCK_PORT + "/cookie")
                .put("adapters.rubicon.XAPI.Username", "rubicon_user")
                .put("adapters.rubicon.XAPI.Password", "rubicon_password")
                .put("adapters.appnexus.endpoint", "http://localhost:" + WIREMOCK_PORT + "/appnexus-exchange")
                .put("adapters.appnexus.usersync_url", "//usersync-url/getuid?")
                .put("adapters.facebook.endpoint", "http://localhost:" + WIREMOCK_PORT + "/facebook-exchange")
                .put("adapters.facebook.nonSecureEndpoint", "http://localhost:" + WIREMOCK_PORT + "/facebook-exchange")
                .put("adapters.facebook.usersync_url", "//facebook-usersync")
                .put("adapters.facebook.platform_id", "101")
                .put("adapters.pulsepoint.endpoint", "http://localhost:" + WIREMOCK_PORT + "/pulsepoint-exchange")
                .put("adapters.pulsepoint.usersync_url", "//pulsepoint-usersync")
                .put("adapters.indexexchange.endpoint", "http://localhost:" + WIREMOCK_PORT + "/indexexchange-exchange")
                .put("adapters.indexexchange.usersync_url", "//indexexchange-usersync")
                .put("datacache.type", "filecache")
                .put("datacache.filename", "src/test/resources/org/rtb/vexing/test-app-settings.yml")
                .put("metrics.metricType", "flushingCounter")
                .put("cache.scheme", "http")
                .put("cache.host", "localhost:" + WIREMOCK_PORT)
                .put("cache.query", "uuid=%PBS_CACHE_UUID%")
                .put("recaptcha_url", "http://localhost:" + WIREMOCK_PORT + "/optout")
                .put("recaptcha_secret", "abc")
                .put("host_cookie.domain", "cookie-domain")
                .put("host_cookie.opt_out_url", "http://optout/url")
                .put("host_cookie.opt_in_url", "http://optin/url");
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
                .withRequestBody(equalToJson(jsonFrom("test-rubicon-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("test-rubicon-bid-response-1.json"))));

        // rubicon bid response for ad unit 2
        wireMockRule.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("test-rubicon-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("test-rubicon-bid-response-2.json"))));

        // rubicon bid response for ad unit 3
        wireMockRule.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("test-rubicon-bid-request-3.json")))
                .willReturn(aResponse().withBody(jsonFrom("test-rubicon-bid-response-3.json"))));

        // appnexus bid response for ad unit 4
        wireMockRule.stubFor(post(urlPathEqualTo("/appnexus-exchange"))
                .withRequestBody(equalToJson(jsonFrom("test-appnexus-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("test-appnexus-bid-response-1.json"))));

        // facebook bid response for ad unit 5
        wireMockRule.stubFor(post(urlPathEqualTo("/facebook-exchange"))
                .withRequestBody(equalToJson(jsonFrom("test-facebook-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("test-facebook-bid-response-1.json"))));

        // pulsepoint bid response for ad unit 6
        wireMockRule.stubFor(post(urlPathEqualTo("/pulsepoint-exchange"))
                .withRequestBody(equalToJson(jsonFrom("test-pulsepoint-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("test-pulsepoint-bid-response-1.json"))));

        // pulsepoint bid response for ad unit 7
        wireMockRule.stubFor(post(urlPathEqualTo("/indexexchange-exchange"))
                .withRequestBody(equalToJson(jsonFrom("test-indexexchange-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("test-indexexchange-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("test-cache-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("test-cache-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for
                // {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345","audienceNetwork":"FB-UID","pulsepoint":"PP-UID","indexExchange":"IE-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSIsImF"
                        + "1ZGllbmNlTmV0d29yayI6IkZCLVVJRCIsInB1bHNlcG9pbnQiOiJQUC1VSUQiLCJpbmRleEV4Y2hhbmdlIjoiSUUtVUlEIn19")
                .queryParam("debug", "1")
                .body(jsonFrom("test-auction-request.json"))
                .post("/auction");

        // then
        assertThat(response.header("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(response.header("Pragma")).isEqualTo("no-cache");
        assertThat(response.header("Expires")).isEqualTo("0");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://www.example.com");

        final String expectedAuctionResponse = auctionResponseFrom(jsonFrom("test-auction-response.json"), response);

        assertThat(response.asString()).isEqualTo(expectedAuctionResponse);
    }

    @Test
    public void statusShouldReturnHttp200Ok() {
        given(spec)
                .when().get("/status")
                .then().assertThat().statusCode(200);
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
        assertThat(uids.uids).isEmpty();
        assertThat(uids.uidsLegacy).isEmpty();
        assertThat(uids.optout).isTrue();
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
                .body(CookieSyncRequest.builder().uuid("uuid").bidders(singletonList(RUBICON)).build())
                .when()
                .post("/cookie_sync")
                .then()
                .spec(new ResponseSpecBuilder().setDefaultParser(Parser.JSON).build())
                .extract()
                .as(CookieSyncResponse.class);

        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.builder()
                .uuid("uuid")
                .status("no_cookie")
                .bidderStatus(singletonList(BidderStatus.builder()
                        .bidder(RUBICON)
                        .noCookie(true)
                        .usersync(defaultNamingMapper.valueToTree(UsersyncInfo.builder()
                                .url("http://localhost:" + WIREMOCK_PORT + "/cookie")
                                .type("redirect")
                                .supportCORS(false)
                                .build()))
                        .build()))
                .build());
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
        assertThat(uidsCookie.getMaxAge()).isEqualTo(15552000);
        assertThat(uidsCookie.getExpiryDate().toInstant())
                .isCloseTo(Instant.now().plus(180, ChronoUnit.DAYS), within(10, ChronoUnit.SECONDS));

        final Uids uids = decodeUids(uidsCookie.getValue());
        assertThat(uids.bday).isEqualTo("2017-08-15T19:47:59.523908376Z"); // should be unchanged
        assertThat(uids.uidsLegacy).isEmpty();
        assertThat(uids.uids.get(RUBICON).uid).isEqualTo("updatedUid");
        assertThat(uids.uids.get(RUBICON).expires.toInstant())
                .isCloseTo(Instant.now().plus(14, ChronoUnit.DAYS), within(10, ChronoUnit.SECONDS));
        assertThat(uids.uids.get("adnxs").uid).isEqualTo("12345");
        assertThat(uids.uids.get("adnxs").expires.toInstant())
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
        assertThat(response.header("Access-Control-Allow-Headers")).isEqualTo("Origin,Accept,Content-Type");
    }

    @Test
    public void ipShouldReturnClientInfo() {
        final Response response = given(spec)
                .header("User-Agent", "Test-Agent")
                .header("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178")
                .header("X-Real-IP", "54.83.132.159")
                .header("Content-Type", "application/json")
                .header("Test-Header", "test-header-value")
                .when().get("/ip");

        assertThat(response.statusCode()).isEqualTo(200);

        //removing port number info
        final String responseAsString =
                StringUtils.removePattern(response.asString(), "[\\n\\r][ \\t]*Port:\\s*([^\\n\\r]*)");

        assertThat(responseAsString).isEqualTo("User Agent: Test-Agent\n" +
                "IP: 127.0.0.1\n" +
                "Forwarded IP: 203.0.113.195\n" +
                "Real IP: 203.0.113.195\n" +
                "Content-Type: application/json; charset=UTF-8\n" +
                "Test-Header: test-header-value\n" +
                "Accept: */*\n" +
                "User-Agent: Test-Agent\n" +
                "X-Forwarded-For: 203.0.113.195, 70.41.3.18, 150.172.238.178\n" +
                "X-Real-IP: 54.83.132.159\n" +
                "Content-Length: 0\n" +
                "Host: localhost:8080\n" +
                "Connection: Keep-Alive\n" +
                "Accept-Encoding: gzip,deflate");
    }

    private String jsonFrom(String file) throws IOException {
        // workaround to clear formatting
        return mapper.writeValueAsString(mapper.readTree(this.getClass().getResourceAsStream(file)));
    }

    private static String auctionResponseFrom(String template, Response response) {
        final Map<String, String> exchanges = new HashMap<>();
        exchanges.put(RUBICON, "http://localhost:" + WIREMOCK_PORT + "/rubicon-exchange?tk_xint=rp-pbs");
        exchanges.put(APPNEXUS, "http://localhost:" + WIREMOCK_PORT + "/appnexus-exchange");
        exchanges.put(FACEBOOK, "http://localhost:" + WIREMOCK_PORT + "/facebook-exchange");
        exchanges.put(PULSEPOINT, "http://localhost:" + WIREMOCK_PORT + "/pulsepoint-exchange");
        exchanges.put(INDEXEXCHANGE, "http://localhost:" + WIREMOCK_PORT + "/indexexchange-exchange");

        String result = template.replaceAll("\\{\\{ cache_resource_url }}",
                "http://localhost:" + WIREMOCK_PORT + "/cache?uuid=");

        for (final Map.Entry<String, String> exchangeEntry : exchanges.entrySet()) {
            final String exchange = exchangeEntry.getKey();
            result = result
                    .replaceAll("\\{\\{ " + exchange + "\\.exchange_uri }}", exchangeEntry.getValue())
                    .replaceAll("\"\\{\\{ " + exchange + "\\.response_time_ms }}\"",
                            response.path("bidder_status.find { it.bidder == '" + exchange + "' }.response_time_ms")
                                    .toString());
        }

        return result;
    }

    private static Uids decodeUids(String value) {
        return Json.decodeValue(Buffer.buffer(Base64.getUrlDecoder().decode(value)), Uids.class);
    }
}
