package org.prebid.server.bidder.yieldlab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.yieldlab.model.YieldlabResponse;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.yieldlab.ExtImpYieldlab;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

public class YieldlabBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private YieldlabBidder yieldlabBidder;

    @Before
    public void setUp() {
        yieldlabBidder = new YieldlabBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new YieldlabBidder("invalid_url", jacksonMapper))
                .withMessage("URL supplied is not valid: invalid_url");
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
                .regs(Regs.of(1, ExtRegs.of(1, "usPrivacy")))
                .user(User.builder().buyeruid("buyeruid").ext(ExtUser.builder().consent("consent").build()).build())
                .site(Site.builder().page("http://www.example.com").build())
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = yieldlabBidder.makeHttpRequests(bidRequest);

        // then
        final int expectedTime = (int) Instant.now().getEpochSecond();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .allSatisfy(uri -> {
                    assertThat(uri).startsWith("https://test.endpoint.com/1?content=json&pvid=true&ts=");
                    assertThat(uri).endsWith("&t=key1%3Dvalue1%26key2%3Dvalue2&ids=buyeruid&yl_rtb_ifa&"
                            + "yl_rtb_devicetype=1&gdpr=1&consent=consent");
                    final String ts = uri.substring(54, uri.indexOf("&t="));
                    assertThat(Integer.parseInt(ts)).isCloseTo(expectedTime, within(10));
                });

        assertThat(result.getValue()).hasSize(1)
                .flatExtracting(r -> r.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("Content-Type", "application/json;charset=utf-8"),
                        tuple("Accept", "application/json"),
                        tuple("User-Agent", "Agent"),
                        tuple("X-Forwarded-For", "ip"),
                        tuple("Referer", "http://www.example.com"),
                        tuple("Cookie", "id=buyeruid"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<Void> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = yieldlabBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Unrecognized token 'invalid':");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenResponseWithNoContent() {
        // given
        final HttpCall<Void> httpCall = HttpCall
                .success(null, HttpResponse.of(204, null, null), null);

        // when
        final Result<List<BidderBid>> result = yieldlabBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnCorrectBidderBid() throws JsonProcessingException {
        // given

        final Map<String, String> targeting = new HashMap<>();
        targeting.put("key1", "value1");
        targeting.put("key2", "value2");
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
                .regs(Regs.of(1, ExtRegs.of(1, "usPrivacy")))
                .user(User.builder().buyeruid("buyeruid").ext(ExtUser.builder().consent("consent").build()).build())
                .site(Site.builder().page("http://www.example.com").build())
                .build();

        final YieldlabResponse yieldlabResponse = YieldlabResponse.of(1, 201d, "yieldlab",
                "728x90", 1234, 5678, "40cb3251-1e1e-4cfd-8edc-7d32dc1a21e5");

        final HttpCall<Void> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(yieldlabResponse));

        // when
        final Result<List<BidderBid>> result = yieldlabBidder.makeBids(httpCall, bidRequest);

        // then
        final String timestamp = String.valueOf((int) Instant.now().getEpochSecond());
        final int weekNumber = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR);
        final String adm = String.format("<script src=\"https://ad.yieldlab.net/d/1/2/728x90?ts=%s&id=extId&pvid=40cb3251-1e1e-4cfd-8edc-7d32dc1a21e5&ids=buyeruid&gdpr=1&consent=consent\"></script>", timestamp);
        final BidderBid expected = BidderBid.of(
                Bid.builder()
                        .id("1")
                        .impid("test-imp-id")
                        .price(BigDecimal.valueOf(2.01))
                        .crid(String.format("11234%s", weekNumber))
                        .dealid("1234")
                        .w(728)
                        .h(90)
                        .adm(adm)
                        .build(),
                BidType.banner, "EUR");

        assertThat(result.getValue().get(0).getBid().getAdm()).isEqualTo(adm);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).doesNotContainNull()
                .hasSize(1).element(0).isEqualTo(expected);
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(yieldlabBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap());
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .banner(Banner.builder().id("banner_id").build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpYieldlab.builder()
                                .adslotId("1")
                                .supplyId("2")
                                .adSize("adSize")
                                .targeting(singletonMap("key", "value"))
                                .extId("extId")
                                .build()))))
                .build();
    }

    private static HttpCall<Void> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<Void>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
