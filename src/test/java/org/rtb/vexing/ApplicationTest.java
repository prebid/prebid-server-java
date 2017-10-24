package org.rtb.vexing;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.internal.mapping.Jackson2Mapper;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
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
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconParams;
import org.rtb.vexing.adapter.rubicon.model.RubiconPubExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconPubExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconSiteExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconSiteExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconTargeting;
import org.rtb.vexing.adapter.rubicon.model.RubiconTargetingExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconTargetingExtRp;
import org.rtb.vexing.model.request.AdUnit;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.response.BidderDebug;
import org.rtb.vexing.model.response.BidderStatus;
import org.rtb.vexing.model.response.PreBidResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(VertxUnitRunner.class)
public class ApplicationTest extends VertxTest {

    private static final String RUBICON = "rubicon";
    private static final int APP_PORT = 8080;
    private static final int RUBICON_PORT = 8090;

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(RUBICON_PORT);
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
                .put("http.port", APP_PORT)
                .put("http-client.max-pool-size", 32768)
                .put("http-client.default-timeout-ms", 1000)
                .put("adapters.rubicon.endpoint", "http://localhost:" + RUBICON_PORT + "/exchange?tk_xint=rp-pbs")
                .put("adapters.rubicon.usersync_url", "http://localhost:" + RUBICON_PORT + "/cookie")
                .put("adapters.rubicon.XAPI.Username", "rubicon_user")
                .put("adapters.rubicon.XAPI.Password", "rubicon_password")
                .put("datacache.type", "filecache")
                .put("datacache.filename", "src/test/resources/org/rtb/vexing/test-app-settings.yml");
    }

    @Test
    public void auctionShouldRespondWithBidsFromRubicon() throws JsonProcessingException {
        // given
        // pre-bid request
        final String preBidRequest = givenPreBidRequest("tid", 1000,
                asList(
                        givenAdUnitBuilder("adUnitCode1", 300, 250),
                        givenAdUnitBuilder("adUnitCode2", 300, 600)),
                givenBid(RUBICON, 2001, 3001, 4001, "bidId"));

        // bid response for ad unit 1
        final String bidRequest1 = givenBidRequest("tid", 1000L, "adUnitCode1", 300, 250, 15, 4001, "example.com",
                "http://www.example.com", 2001, 3001, "userAgent", "192.168.244.1", "J5VLCWQP-26-CWFT");
        final String bidResponse1 = givenBidResponse("bidResponseId1", "seatId1", "impId1", "8.43", "adm1", "crid1",
                300, 250, "dealId1",
                RubiconTargeting.builder().key("key1").values(asList("value11", "value12")).build());
        wireMockRule.stubFor(post(urlPathEqualTo("/exchange"))
                .withQueryParam("tk_xint", equalTo("rp-pbs"))
                .withBasicAuth("rubicon_user", "rubicon_password")
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("prebid-server/1.0"))
                .withRequestBody(equalToJson(bidRequest1))
                .willReturn(aResponse().withBody(bidResponse1)));

        // bid response for ad unit 2
        final String bidRequest2 = givenBidRequest("tid", 1000L, "adUnitCode2", 300, 600, 10, 4001, "example.com",
                "http://www.example.com", 2001, 3001, "userAgent", "192.168.244.1", "J5VLCWQP-26-CWFT");
        final String bidResponse2 = givenBidResponse("bidResponseId2", "seatId2", "impId2", "4.26", "adm2", "crid2",
                300, 600, "dealId2",
                RubiconTargeting.builder().key("key2").values(asList("value21", "value22")).build());
        wireMockRule.stubFor(post(urlPathEqualTo("/exchange"))
                .withRequestBody(equalToJson(bidRequest2))
                .willReturn(aResponse().withBody(bidResponse2)));

        // when
        final PreBidResponse preBidResponse = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","appnexus":"12345"},
                // "bday":"2017-08-15T19:47:59.523908376Z"}
                .header("Cookie", "uids=eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYXBwbmV4dX" +
                        "MiOiIxMjM0NSJ9LCJiZGF5IjoiMjAxNy0wOC0xNVQxOTo0Nzo1OS41MjM5MDgzNzZaIn0=; dummy=cookie")
                .queryParam("is_debug", "1")
                .body(preBidRequest)
                .post("/auction")
                .as(PreBidResponse.class);

        // then
        assertThat(preBidResponse.status).isEqualTo("OK");
        assertThat(preBidResponse.tid).isEqualTo("tid");
        assertThat(preBidResponse.bidderStatus).hasSize(1);
        final BidderStatus rubiconStatus = preBidResponse.bidderStatus.get(0);
        assertThat(rubiconStatus.bidder).isEqualTo(RUBICON);
        assertThat(rubiconStatus.numBids).isEqualTo(2);
        final Integer responseTime = rubiconStatus.responseTime;
        assertThat(responseTime).isPositive();
        assertThat(rubiconStatus.debug).hasSize(2).containsOnly(
                bidderDebug(bidRequest1, bidResponse1),
                bidderDebug(bidRequest2, bidResponse2));

        assertThat(preBidResponse.bids).hasSize(2).containsOnly(
                bid("impId1", "8.43", "adm1", "crid1", 300, 250, "dealId1", singletonMap("key1", "value11"), RUBICON,
                        "bidId", responseTime),
                bid("impId2", "4.26", "adm2", "crid2", 300, 600, "dealId2", singletonMap("key2", "value21"), RUBICON,
                        "bidId", responseTime));
    }

    @Test
    public void statusShouldReturnHttp200Ok() {
        given(spec)
                .when().get("/status")
                .then().assertThat().statusCode(200);
    }

    private static String givenPreBidRequest(String tid, int timeoutMillis, List<AdUnit.AdUnitBuilder> adUnitBuilders,
                                             org.rtb.vexing.model.request.Bid bid) throws JsonProcessingException {
        return mapper.writeValueAsString(PreBidRequest.builder()
                .accountId("1001")
                .tid(tid)
                .timeoutMillis(timeoutMillis)
                .adUnits(adUnitBuilders.stream()
                        .map(b -> b.bids(singletonList(bid)))
                        .map(AdUnit.AdUnitBuilder::build)
                        .collect(toList()))
                .build());
    }

    private static AdUnit.AdUnitBuilder givenAdUnitBuilder(String adUnitCode, int w, int h) {
        return AdUnit.builder()
                .code(adUnitCode)
                .sizes(singletonList(Format.builder().w(w).h(h).build()));
    }

    private static org.rtb.vexing.model.request.Bid givenBid(
            String bidder, int accountId, int siteId, int zoneId, String bidId) {
        return org.rtb.vexing.model.request.Bid.builder()
                .bidder(bidder)
                .params(defaultNamingMapper.valueToTree(RubiconParams.builder()
                        .accountId(accountId)
                        .siteId(siteId)
                        .zoneId(zoneId)
                        .build()))
                .bidId(bidId)
                .build();
    }

    private static String givenBidRequest(
            String tid, long tmax, String adUnitCode, int w, int h, int sizeId, int zoneId, String domain,
            String page, int accountId, int siteId, String userAgent, String ip, String buyerUid) throws
            JsonProcessingException {

        return mapper.writeValueAsString(BidRequest.builder()
                .id(tid)
                .at(1)
                .tmax(tmax)
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
                                        .build())
                                .build()))
                        .build()))
                .site(Site.builder()
                        .domain(domain)
                        .page(page)
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
                        .ua(userAgent)
                        .ip(ip)
                        .build())
                .user(User.builder()
                        .buyeruid(buyerUid)
                        .build())
                .source(Source.builder()
                        .fd(1)
                        .tid(tid)
                        .build())
                .build());
    }

    private static String givenBidResponse(
            String bidResponseId, String seatId, String impId, String price, String adm, String crid, int w, int h,
            String dealId, RubiconTargeting rubiconTargeting) throws JsonProcessingException {
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
                                .ext(mapper.valueToTree(RubiconTargetingExt.builder()
                                        .rp(RubiconTargetingExtRp.builder()
                                                .targeting(singletonList(rubiconTargeting))
                                                .build())
                                        .build()))
                                .build()))
                        .build()))
                .build());
    }

    private static BidderDebug bidderDebug(String bidRequest1, String bidResponse1) {
        return BidderDebug.builder()
                .requestUri("http://localhost:" + RUBICON_PORT + "/exchange?tk_xint=rp-pbs")
                .requestBody(bidRequest1)
                .responseBody(bidResponse1)
                .statusCode(200)
                .build();
    }

    private static org.rtb.vexing.model.response.Bid bid(
            String impId, String price, String adm, String crid, int width, int height, String dealId,
            Map<String, String> adServerTargeting, String bidder, String bidId, Integer responseTime) {
        return org.rtb.vexing.model.response.Bid.builder()
                .code(impId)
                .price(new BigDecimal(price))
                .adm(adm)
                .creativeId(crid)
                .width(width)
                .height(height)
                .dealId(dealId)
                .adServerTargeting(adServerTargeting)
                .bidder(bidder)
                .bidId(bidId)
                .responseTime(responseTime)
                .build();
    }
}
