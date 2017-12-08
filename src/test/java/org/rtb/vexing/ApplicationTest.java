package org.rtb.vexing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.Cookie;
import io.restassured.internal.mapping.Jackson2Mapper;
import io.restassured.parsing.Parser;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.rtb.vexing.adapter.rubicon.model.RubiconBannerExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconBannerExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconDeviceExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconDeviceExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExtRpTrack;
import org.rtb.vexing.adapter.rubicon.model.RubiconParams;
import org.rtb.vexing.adapter.rubicon.model.RubiconPubExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconPubExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconSiteExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconSiteExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconUserExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconUserExtDt;
import org.rtb.vexing.adapter.rubicon.model.RubiconUserExtRp;
import org.rtb.vexing.cache.model.request.BidCacheRequest;
import org.rtb.vexing.cache.model.request.PutObject;
import org.rtb.vexing.cache.model.request.PutValue;
import org.rtb.vexing.cache.model.response.BidCacheResponse;
import org.rtb.vexing.cache.model.response.CacheObject;
import org.rtb.vexing.model.Uids;
import org.rtb.vexing.model.request.AdUnit;
import org.rtb.vexing.model.request.CookieSyncRequest;
import org.rtb.vexing.model.request.DigiTrust;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.request.Sdk;
import org.rtb.vexing.model.response.BidderDebug;
import org.rtb.vexing.model.response.BidderStatus;
import org.rtb.vexing.model.response.CookieSyncResponse;
import org.rtb.vexing.model.response.PreBidResponse;
import org.rtb.vexing.model.response.UsersyncInfo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@RunWith(VertxUnitRunner.class)
public class ApplicationTest extends VertxTest {

    private static final String RUBICON = "rubicon";
    private static final int APP_PORT = 8080;
    private static final int WIREMOCK_PORT = 8090;

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(WIREMOCK_PORT);
    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    private static final RequestSpecification spec = new RequestSpecBuilder()
            .setBaseUri("http://localhost")
            .setPort(8080)
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
                .put("adapters.rubicon.endpoint", "http://localhost:" + WIREMOCK_PORT + "/exchange?tk_xint=rp-pbs")
                .put("adapters.rubicon.usersync_url", "http://localhost:" + WIREMOCK_PORT + "/cookie")
                .put("adapters.rubicon.XAPI.Username", "rubicon_user")
                .put("adapters.rubicon.XAPI.Password", "rubicon_password")
                .put("datacache.type", "filecache")
                .put("datacache.filename", "src/test/resources/org/rtb/vexing/test-app-settings.yml")
                .put("metrics.metricType", "flushingCounter")
                .put("cache.scheme", "http")
                .put("cache.host", "localhost:" + WIREMOCK_PORT)
                .put("cache.query", "uuid=%PBS_CACHE_UUID%")
                .put("recaptcha_url", "http://localhost:" + WIREMOCK_PORT + "/optout")
                .put("recaptcha_secret", "abc")
                .put("host_cookie.opt_out_url", "http://optout/url")
                .put("host_cookie.opt_in_url", "http://optin/url");
    }

    @Test
    public void auctionShouldRespondWithBidsFromRubicon() throws JsonProcessingException {
        // given
        // pre-bid request
        final ObjectNode inventory = mapper.createObjectNode();
        inventory.set("rating", mapper.createArrayNode().add(new TextNode("5-star")));
        inventory.set("prodtype", mapper.createArrayNode().add((new TextNode("tech"))));

        final ObjectNode visitor = mapper.createObjectNode();
        visitor.set("ucat", mapper.createArrayNode().add(new TextNode("new")));
        visitor.set("search", mapper.createArrayNode().add((new TextNode("iphone"))));

        final DigiTrust dt = DigiTrust.builder().id("id").keyv(123).pref(0).build();

        final PreBidRequest preBidRequest = givenPreBidRequest(dt, inventory, visitor);

        // bid response for ad unit 1
        final String bidRequest1 =
                givenBidRequest("adUnitCode1", 300, 250, 15, 4001, 2001, 3001, inventory, visitor, dt);
        final String bidResponse1 = givenBidResponse("bidResponseId1", "seatId1", "adUnitCode1", "8.43", "adm1",
                "crid1", 300, 250, "dealId1");
        wireMockRule.stubFor(post(urlPathEqualTo("/exchange"))
                .withQueryParam("tk_xint", equalTo("rp-pbs"))
                .withBasicAuth("rubicon_user", "rubicon_password")
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("prebid-server/1.0"))
                .withRequestBody(equalToJson(bidRequest1))
                .willReturn(aResponse().withBody(bidResponse1)));

        // bid response for ad unit 2
        final String bidRequest2 =
                givenBidRequest("adUnitCode2", 300, 600, 10, 7001, 5001, 6001, inventory, visitor, dt);
        final String bidResponse2 = givenBidResponse("bidResponseId2", "seatId2", "adUnitCode2", "4.26", "adm2",
                "crid2", 300, 600, "dealId2");
        wireMockRule.stubFor(post(urlPathEqualTo("/exchange"))
                .withRequestBody(equalToJson(bidRequest2))
                .willReturn(aResponse().withBody(bidResponse2)));

        // pre-bid cache
        final String bidCacheRequestAsString = givenBidCacheRequest(asList(
                PutValue.builder().adm("adm1").width(300).height(250).build(),
                PutValue.builder().adm("adm2").width(300).height(600).build()
        ));
        final String bidCacheResponseAsString = givenBidCacheResponse(asList(
                "883db7d2-3013-4ce0-a454-adc7d208ef0c",
                "0b4f60d1-fb99-4d95-ba6f-30ac90f9a315"
        ));
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(bidCacheRequestAsString))
                .willReturn(aResponse().withBody(bidCacheResponseAsString)));

        // when
        final PreBidResponse preBidResponse = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","appnexus":"12345"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYXBwbmV4dXMiOiIxMjM0NSJ9fQ==")
                .queryParam("debug", "1")
                .body(preBidRequest)
                .post("/auction")
                .as(PreBidResponse.class);

        // then
        assertThat(preBidResponse.status).isEqualTo("no_cookie");
        assertThat(preBidResponse.tid).isEqualTo("tid");
        assertThat(preBidResponse.bidderStatus).hasSize(1);
        final BidderStatus rubiconStatus = preBidResponse.bidderStatus.get(0);
        assertThat(rubiconStatus.bidder).isEqualTo(RUBICON);
        assertThat(rubiconStatus.numBids).isEqualTo(2);
        final Integer responseTime = rubiconStatus.responseTimeMs;
        assertThat(responseTime).isPositive();
        assertThat(rubiconStatus.debug).hasSize(2).containsOnly(
                bidderDebug(bidRequest1, bidResponse1),
                bidderDebug(bidRequest2, bidResponse2));

        assertThat(preBidResponse.bids).hasSize(2).containsOnly(
                bid("adUnitCode1", "8.43", "883db7d2-3013-4ce0-a454-adc7d208ef0c",
                        "http://localhost:" + WIREMOCK_PORT + "/cache?uuid=883db7d2-3013-4ce0-a454-adc7d208ef0c",
                        "crid1", 300, 250, "dealId1", "8.40", RUBICON, "bidId1", responseTime),
                bid("adUnitCode2", "4.26", "0b4f60d1-fb99-4d95-ba6f-30ac90f9a315",
                        "http://localhost:" + WIREMOCK_PORT + "/cache?uuid=0b4f60d1-fb99-4d95-ba6f-30ac90f9a315",
                        "crid2", 300, 600, "dealId2", "4.20", RUBICON, "bidId2",
                        responseTime));
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
                // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","appnexus":"12345"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYXBwbmV4dXMiOiIxMjM0NSJ9fQ==")
                .body("g-recaptcha-response=recaptcha1&optout=1")
                .post("/optout");

        assertThat(response.statusCode()).isEqualTo(301);
        assertThat(response.header("location")).isEqualTo("http://optout/url");
        // this uids cookie value stands for {"uids":{},"optout":true}
        final Uids uids = decodeUids(response.cookie("uids"));
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
                // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","appnexus":"12345"},
                // "bday":"2017-08-15T19:47:59.523908376Z"}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYXBwbmV4dXMiOiIxMjM0NSJ9LCJiZGF5" +
                        "IjoiMjAxNy0wOC0xNVQxOTo0Nzo1OS41MjM5MDgzNzZaIn0=")
                // this constant is ok to use as long as it coincides with family name
                .queryParam("bidder", RUBICON)
                .queryParam("uid", "updatedUid")
                .when()
                .get("/setuid")
                .then()
                .extract()
                .detailedCookie("uids");

        assertThat(uidsCookie.getMaxAge()).isEqualTo(15552000);
        assertThat(uidsCookie.getExpiryDate().toInstant())
                .isCloseTo(Instant.now().plus(180, ChronoUnit.DAYS), within(10, ChronoUnit.SECONDS));

        final Uids uids = decodeUids(uidsCookie.getValue());
        assertThat(uids.bday).isEqualTo("2017-08-15T19:47:59.523908376Z"); // should be unchanged
        assertThat(uids.uidsLegacy).isEmpty();
        assertThat(uids.uids.get(RUBICON).uid).isEqualTo("updatedUid");
        assertThat(uids.uids.get(RUBICON).expires.toInstant())
                .isCloseTo(Instant.now().plus(14, ChronoUnit.DAYS), within(10, ChronoUnit.SECONDS));
        assertThat(uids.uids.get("appnexus").uid).isEqualTo("12345");
        assertThat(uids.uids.get("appnexus").expires.toInstant())
                .isCloseTo(Instant.now().minus(5, ChronoUnit.MINUTES), within(10, ChronoUnit.SECONDS));
    }

    private static PreBidRequest givenPreBidRequest(DigiTrust dt, ObjectNode inventory, ObjectNode visitor) {
        return PreBidRequest.builder()
                .accountId("1001")
                .tid("tid")
                .cacheMarkup(1)
                .sortBids(1)
                .digiTrust(dt)
                .timeoutMillis((long) 1000)
                .adUnits(asList(
                        givenAdUnitBuilder("adUnitCode1", 300, 250).bids(singletonList(
                                givenBid(RUBICON, inventory, visitor))).build(),
                        givenAdUnitBuilder("adUnitCode2", 300, 600).configId("14062").build()))
                .device(Device.builder()
                        .pxratio(new BigDecimal("4.2"))
                        .ext(mapper.valueToTree(RubiconDeviceExt.builder()
                                .rp(RubiconDeviceExtRp.builder().pixelratio(new BigDecimal("4.2")).build())
                                .build()))
                        .build())
                .sdk(Sdk.builder().source("source1").platform("platform1").version("version1").build())
                .build();
    }

    private static AdUnit.AdUnitBuilder givenAdUnitBuilder(String adUnitCode, int w, int h) {
        return AdUnit.builder()
                .code(adUnitCode)
                .sizes(singletonList(Format.builder().w(w).h(h).build()));
    }

    private static org.rtb.vexing.model.request.Bid givenBid(String bidder, JsonNode inventory, JsonNode visitor) {
        return org.rtb.vexing.model.request.Bid.builder()
                .bidder(bidder)
                .params(defaultNamingMapper.valueToTree(RubiconParams.builder()
                        .accountId(2001)
                        .siteId(3001)
                        .zoneId(4001)
                        .inventory(inventory)
                        .visitor(visitor)
                        .build()))
                .bidId("bidId1")
                .build();
    }

    private static String givenBidRequest(
            String adUnitCode, int w, int h, int sizeId, int zoneId, int accountId, int siteId, JsonNode inventory,
            JsonNode visitor, DigiTrust dt) throws JsonProcessingException {

        return mapper.writeValueAsString(BidRequest.builder()
                .id("tid")
                .at(1)
                .tmax(1000L)
                .imp(singletonList(Imp.builder()
                        .id(adUnitCode)
                        .banner(Banner.builder()
                                .w(w)
                                .h(h)
                                .format(singletonList(Format.builder()
                                        .w(w)
                                        .h(h)
                                        .build()))
                                .ext(mapper.valueToTree(RubiconBannerExt.builder()
                                        .rp(RubiconBannerExtRp.builder()
                                                .sizeId(sizeId)
                                                .mime("text/html")
                                                .build())
                                        .build()))
                                .build())
                        .ext(mapper.valueToTree(RubiconImpExt.builder()
                                .rp(RubiconImpExtRp.builder()
                                        .zoneId(zoneId)
                                        .target(inventory)
                                        .track(RubiconImpExtRpTrack.builder()
                                                .mint("prebid")
                                                .mintVersion("source1_platform1_version1")
                                                .build())
                                        .build())
                                .build()))
                        .build()))
                .site(Site.builder()
                        .domain("example.com")
                        .page("http://www.example.com")
                        .publisher(Publisher.builder()
                                .ext(mapper.valueToTree(RubiconPubExt.builder()
                                        .rp(RubiconPubExtRp.builder()
                                                .accountId(accountId)
                                                .build())
                                        .build()))
                                .build())
                        .ext(mapper.valueToTree(RubiconSiteExt.builder()
                                .rp(RubiconSiteExtRp.builder()
                                        .siteId(siteId)
                                        .build())
                                .build()))
                        .build())
                .device(Device.builder()
                        .ua("userAgent")
                        .ip("192.168.244.1")
                        .pxratio(new BigDecimal("4.2"))
                        .ext(mapper.valueToTree(RubiconDeviceExt.builder()
                                .rp(RubiconDeviceExtRp.builder().pixelratio(new BigDecimal("4.2")).build())
                                .build()))
                        .build())
                .user(User.builder()
                        .buyeruid("J5VLCWQP-26-CWFT")
                        .ext(mapper.valueToTree(RubiconUserExt.builder()
                                .rp(RubiconUserExtRp.builder()
                                        .target(visitor)
                                        .build())
                                .dt(RubiconUserExtDt.builder()
                                        .id(dt.id)
                                        .keyv(dt.keyv)
                                        .preference(dt.pref)
                                        .build())
                                .build()))
                        .build())
                .source(Source.builder()
                        .fd(1)
                        .tid("tid")
                        .build())
                .build());
    }

    private static String givenBidResponse(
            String bidResponseId, String seatId, String impId, String price, String adm, String crid, int w, int h,
            String dealId) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .id(bidResponseId)
                .seatbid(singletonList(SeatBid.builder()
                        .seat(seatId)
                        .bid(singletonList(Bid.builder()
                                .impid(impId)
                                .price(new BigDecimal(price))
                                .adm(adm)
                                .crid(crid)
                                .w(w)
                                .h(h)
                                .dealid(dealId)
                                .build()))
                        .build()))
                .build());
    }

    private static BidderDebug bidderDebug(String bidRequest1, String bidResponse1) {
        return BidderDebug.builder()
                .requestUri("http://localhost:" + WIREMOCK_PORT + "/exchange?tk_xint=rp-pbs")
                .requestBody(bidRequest1)
                .responseBody(bidResponse1)
                .statusCode(200)
                .build();
    }

    private static org.rtb.vexing.model.response.Bid bid(
            String impId, String price, String cacheId, String cacheUrl, String crid, int width, int height,
            String dealId, String priceBucket, String bidder, String bidId, Integer responseTime) {

        final Map<String, String> adServerTargeting = new HashMap<>();
        adServerTargeting.put("hb_pb", priceBucket);
        adServerTargeting.put("hb_pb_rubicon", priceBucket);
        adServerTargeting.put("hb_cache_id", cacheId);
        adServerTargeting.put("hb_cache_id_rubicon", cacheId);
        adServerTargeting.put("hb_deal", dealId);
        adServerTargeting.put("hb_deal_rubicon", dealId);
        adServerTargeting.put("hb_size", width + "x" + height);
        adServerTargeting.put("hb_size_rubicon", width + "x" + height);
        adServerTargeting.put("hb_bidder", bidder);
        adServerTargeting.put("hb_bidder_rubicon", bidder);
        adServerTargeting.put("hb_creative_loadtype", "html");

        return org.rtb.vexing.model.response.Bid.builder()
                .code(impId)
                .price(new BigDecimal(price))
                .cacheId(cacheId)
                .cacheUrl(cacheUrl)
                .creativeId(crid)
                .width(width)
                .height(height)
                .dealId(dealId)
                .adServerTargeting(adServerTargeting)
                .bidder(bidder)
                .bidId(bidId)
                .responseTimeMs(responseTime)
                .build();
    }

    private static String givenBidCacheRequest(List<PutValue> putValues) throws JsonProcessingException {
        final List<PutObject> putObjects = putValues.stream()
                .map(putValue -> PutObject.builder()
                        .type("json")
                        .value(putValue)
                        .build())
                .collect(Collectors.toList());
        final BidCacheRequest bidCacheRequest = BidCacheRequest.builder()
                .puts(putObjects)
                .build();
        return mapper.writeValueAsString(bidCacheRequest);
    }

    private static String givenBidCacheResponse(List<String> uuids) throws JsonProcessingException {
        List<CacheObject> cacheObjects = uuids.stream()
                .map(uuid -> CacheObject.builder().uuid(uuid).build())
                .collect(Collectors.toList());
        BidCacheResponse bidCacheResponse = BidCacheResponse.builder()
                .responses(cacheObjects)
                .build();
        return mapper.writeValueAsString(bidCacheResponse);
    }

    private static Uids decodeUids(String value) {
        return Json.decodeValue(Buffer.buffer(Base64.getUrlDecoder().decode(value)), Uids.class);
    }
}
