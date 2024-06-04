package org.prebid.server.bidder.yieldlab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.yieldlab.model.YieldlabDigitalServicesActResponse;
import org.prebid.server.bidder.yieldlab.model.YieldlabResponse;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.request.DsaTransparency;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.yieldlab.ExtImpYieldlab;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class YieldlabBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private Clock clock;

    private YieldlabBidder target;

    @Before
    public void setUp() {
        clock = Clock.fixed(Instant.parse("2019-07-26T10:00:00Z"), ZoneId.systemDefault());
        target = new YieldlabBidder(ENDPOINT_URL, clock, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new YieldlabBidder("invalid_url", clock, jacksonMapper))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfEndpointUrlComposingFails() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpYieldlab.builder()
                                        .adslotId("invalid path")
                                        .build())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Invalid url: https://test.endpoint.com/invalid path");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                });
    }

    @Test
    public void makeHttpRequestsShouldSendRequestToModifiedUrlWithHeaders() {
        // given
        final Map<String, String> targeting = new HashMap<>();
        targeting.put("key1", "value1");
        targeting.put("key2", "value2");
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().w(1).h(1).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpYieldlab.builder()
                                        .adslotId("1")
                                        .supplyId("2")
                                        .adSize("adSize")
                                        .targeting(targeting)
                                        .extId("extId")
                                        .build())))
                        .build()))
                .device(Device.builder().ip("ip").ua("Agent").language("fr").devicetype(1).build())
                .regs(Regs.builder().coppa(1).ext(ExtRegs.of(1, "usPrivacy", null, null)).build())
                .user(User.builder().buyeruid("buyeruid").ext(ExtUser.builder().consent("consent").build()).build())
                .site(Site.builder().page("http://www.example.com").build())
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = target.makeHttpRequests(bidRequest);

        // then
        final long expectedTime = clock.instant().getEpochSecond();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .allSatisfy(uri -> {
                    assertThat(uri).startsWith("https://test.endpoint.com/1?content=json&pvid=true&ts=");
                    assertThat(uri).endsWith("&t=key1%3Dvalue1%26key2%3Dvalue2&ids=buyeruid&yl_rtb_ifa&"
                            + "yl_rtb_devicetype=1&gdpr=1&consent=consent");
                    final String ts = uri.substring(54, uri.indexOf("&t="));
                    assertThat(Long.parseLong(ts)).isEqualTo(expectedTime);
                });

        assertThat(result.getValue()).hasSize(1)
                .flatExtracting(r -> r.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("Accept", "application/json"),
                        tuple("User-Agent", "Agent"),
                        tuple("X-Forwarded-For", "ip"),
                        tuple("Referer", "http://www.example.com"),
                        tuple("Cookie", "id=buyeruid"));
    }

    @Test
    public void constructExtImpShouldWorkWithDuplicateKeysTargeting() {
        // given
        final Map<String, String> targeting = new HashMap<>();
        targeting.put("key1", "value1");

        final List<Imp> imps = new ArrayList<>();
        imps.add(Imp.builder()
                .banner(Banner.builder().w(1).h(1).build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpYieldlab.builder()
                                .adslotId("1")
                                .supplyId("2")
                                .adSize("adSize")
                                .targeting(targeting)
                                .extId("extId")
                                .build())))
                .build());
        imps.add(Imp.builder()
                .banner(Banner.builder().w(1).h(1).build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpYieldlab.builder()
                                .adslotId("2")
                                .supplyId("2")
                                .adSize("adSize")
                                .targeting(targeting)
                                .extId("extId")
                                .build())))
                .build());

        final BidRequest bidRequest = BidRequest.builder()
                .imp(imps)
                .device(Device.builder().ip("ip").ua("Agent").language("fr").devicetype(1).build())
                .regs(Regs.builder().coppa(1).ext(ExtRegs.of(1, "usPrivacy", null, null)).build())
                .user(User.builder().buyeruid("buyeruid").ext(ExtUser.builder().consent("consent").build()).build())
                .site(Site.builder().page("http://www.example.com").build())
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = target.makeHttpRequests(bidRequest);
        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .allSatisfy(uri -> {
                    assertThat(uri).startsWith("https://test.endpoint.com/1,2?content=json&pvid=true&ts=");
                    assertThat(uri).endsWith("&t=key1%3Dvalue1&ids=buyeruid&yl_rtb_ifa&"
                            + "yl_rtb_devicetype=1&gdpr=1&consent=consent");
                });
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<Void> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                && error.getMessage().startsWith("Unrecognized token 'invalid"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnCorrectBidderBid() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("test-imp-id")
                        .banner(Banner.builder().w(1).h(1).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpYieldlab.builder()
                                .adslotId("1")
                                .supplyId("2")
                                .adSize("adSize")
                                .targeting(singletonMap("key", "value"))
                                .extId("extId")
                                .build())))
                        .build()))
                .device(Device.builder().ip("ip").ua("Agent").language("fr").devicetype(1).build())
                .regs(Regs.builder().coppa(1).ext(ExtRegs.of(1, "usPrivacy", null, null)).build())
                .user(User.builder().buyeruid("buyeruid").ext(ExtUser.builder().consent("consent").build()).build())
                .site(Site.builder().page("http://www.example.com").build())
                .build();

        final YieldlabResponse yieldlabResponse = YieldlabResponse.of(1, 201d, "yieldlab",
                "728x90", 1234, 5678, "40cb3251-1e1e-4cfd-8edc-7d32dc1a21e5", null);

        final BidderCall<Void> httpCall = givenHttpCall(mapper.writeValueAsString(yieldlabResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        final String timestamp = String.valueOf(clock.instant().getEpochSecond());
        final int weekNumber = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR);
        final String adm = """
                <script src="https://ad.yieldlab.net/d/1/2/728x90?ts=%s\
                &id=extId&pvid=40cb3251-1e1e-4cfd-8edc-7d32dc1a21e5&ids=buyeruid&gdpr=1&consent=consent">\
                </script>""".formatted(timestamp);
        final BidderBid expected = BidderBid.of(
                Bid.builder()
                        .id("1")
                        .impid("test-imp-id")
                        .price(BigDecimal.valueOf(2.01))
                        .crid("11234" + weekNumber)
                        .dealid("1234")
                        .w(728)
                        .h(90)
                        .adm(adm)
                        .build(),
                BidType.banner, "EUR");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(expected);
    }

    @Test
    public void makeBidsShouldReturnCorrectAdm() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("test-imp-id")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpYieldlab.builder()
                                .adslotId("12345")
                                .supplyId("123456789")
                                .adSize("728x90")
                                .extId("abc")
                                .build())))
                        .video(Video.builder().build())
                        .build()))
                .build();

        final YieldlabResponse yieldlabResponse = YieldlabResponse.of(12345, 201d, "yieldlab",
                "728x90", 1234, 5678, "40cb3251-1e1e-4cfd-8edc-7d32dc1a21e5", null);

        final BidderCall<Void> httpCall = givenHttpCall(mapper.writeValueAsString(yieldlabResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        final String timestamp = String.valueOf(clock.instant().getEpochSecond());
        final String expectedAdm = """
                <VAST version="2.0"><Ad id="12345"><Wrapper>
                <AdSystem>Yieldlab</AdSystem>
                <VASTAdTagURI><![CDATA[ %s%s%s ]]></VASTAdTagURI>
                <Impression></Impression>
                <Creatives></Creatives>
                </Wrapper></Ad></VAST>""".formatted(
                "https://ad.yieldlab.net/d/12345/123456789/728x90?ts=",
                timestamp,
                "&id=abc&pvid=40cb3251-1e1e-4cfd-8edc-7d32dc1a21e5");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly(expectedAdm);
    }

    @Test
    public void makeHttpRequestsShouldAddDsaRequestParamsToRequestWhenDsaIsPresent() {
        //given
        final ExtRegsDsa dsa = ExtRegsDsa.of(
                1, 2, 3, List.of(DsaTransparency.of("testDomain", List.of(1, 2, 3)))
        );
        final Regs regs = Regs.builder()
                    .ext(ExtRegs.of(null, null, null, dsa))
                    .build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                    .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpYieldlab.builder()
                            .adslotId("123")
                            .build())))
                    .build()))
                .regs(regs)
                .build();

        //when
        final Result<List<HttpRequest<Void>>> result = target.makeHttpRequests(bidRequest);

        //then
        final List<String> expectations = List.of(
                "&dsarequired=1",
                "&dsapubrender=2",
                "&dsadatatopub=3",
                "&dsatransparency=testDomain%7E1_2_3"
        );
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue().getFirst().getUri())
                .contains(expectations);
    }

    @Test
    public void makeHttpRequestsEncodesDsaTransparencyCorrectlyWhenBidRequestContainsDsaTransparencyInformation() {
        //given
        final ExtRegsDsa dsa = ExtRegsDsa.of(
                1, 2, 3, List.of(
                    DsaTransparency.of("testDomain", List.of(1, 2, 3)),
                    DsaTransparency.of("testDomain2", List.of(4, 5, 6))
                )
        );
        final Regs regs = Regs.builder()
                .ext(ExtRegs.of(null, null, null, dsa))
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                    .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpYieldlab.builder()
                            .adslotId("123")
                            .build())))
                    .build()))
                .regs(regs)
                .build();

        //when
        final Result<List<HttpRequest<Void>>> result = target.makeHttpRequests(bidRequest);

        //then
        final List<String> expectations = List.of(
                "&dsarequired=1",
                "&dsapubrender=2",
                "&dsadatatopub=3",
                "&dsatransparency=testDomain%7E1_2_3%7E%7EtestDomain2%7E4_5_6"
        );
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue().getFirst().getUri())
                .contains(expectations);
    }

    @Test
    public void makeHttpRequestsShouldNotSendDsaInfoInBidRequestWhenDsaIsMissing() {
        //given
        final ExtRegsDsa dsa = ExtRegsDsa.of(
                2, null, 3, null
        );
        final Regs regs = Regs.builder()
                .ext(ExtRegs.of(null, null, null, dsa))
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                    .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpYieldlab.builder()
                            .adslotId("123")
                            .build())))
                    .build()))
                .regs(regs)
                .build();

        //when
        final Result<List<HttpRequest<Void>>> result = target.makeHttpRequests(bidRequest);

        //then
        final List<String> expectations = List.of(
                "dsarequired=2",
                "dsadatatopub=3"
        );
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue().getFirst().getUri())
                .contains(expectations);
    }

    @Test
    public void makeHttpRequestsShouldNotAddDsaTransparencyParamsToBidRequestWhenParamsAreMissing() {
        //given
        final ExtRegsDsa dsa = ExtRegsDsa.of(
                2, null, 3, List.of(DsaTransparency.of("domain", null))
        );
        final Regs regs = Regs.builder()
                .ext(ExtRegs.of(null, null, null, dsa))
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                    .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpYieldlab.builder()
                            .adslotId("123")
                            .build())))
                    .build()))
                .regs(regs)
                .build();

        //when
        final Result<List<HttpRequest<Void>>> result = target.makeHttpRequests(bidRequest);

        //then
        final List<String> expectations = List.of(
                "dsarequired=2",
                "dsadatatopub=3"
        );
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue().getFirst().getUri())
                .contains(expectations);
    }

    @Test
    public void makeBidsShouldAddDsaParamsWhenDsaIsPresentInResponse() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                    .id("test-imp-id")
                    .banner(Banner.builder().w(1).h(1).build())
                    .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpYieldlab.builder()
                        .adslotId("1")
                        .supplyId("2")
                        .adSize("adSize")
                        .targeting(singletonMap("key", "value"))
                        .extId("extId")
                        .build())))
                    .build()))
                .build();

        final YieldlabDigitalServicesActResponse dsaResponse = YieldlabDigitalServicesActResponse.of(
                "yieldlab",
                "yieldlab",
                2,
                List.of(
                    YieldlabDigitalServicesActResponse.Transparency.of(
                        "yieldlab.de",
                        List.of(1, 2, 3)
                    )
                )
        );

        final YieldlabResponse yieldlabResponse = YieldlabResponse.of(1, 201d, "yieldlab",
                "728x90", 1234, 5678, "40cb3251-1e1e-4cfd-8edc-7d32dc1a21e5", dsaResponse
        );

        final BidderCall<Void> httpCall = givenHttpCall(mapper.writeValueAsString(yieldlabResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        final ObjectNode expectedTransparency = mapper.createObjectNode();
        expectedTransparency.put("domain", "yieldlab.de");
        expectedTransparency.set("dsaparams", mapper.convertValue(List.of(1, 2, 3), JsonNode.class));

        final ArrayNode transparencies = mapper.createArrayNode();
        transparencies.add(expectedTransparency);

        final ObjectNode expectedDsa = mapper.createObjectNode();
        expectedDsa.put("paid", "yieldlab");
        expectedDsa.put("behalf", "yieldlab");
        expectedDsa.put("adrender", 2);
        expectedDsa.set("transparency", transparencies);

        final JsonNode actualDsa = result.getValue().getFirst().getBid().getExt().get("dsa");

        assertThat(result.getErrors()).isEmpty();
        assertThat(actualDsa).isEqualTo(expectedDsa);
    }

    private static BidderCall<Void> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<Void>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
