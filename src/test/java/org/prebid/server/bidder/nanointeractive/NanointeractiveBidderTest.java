package org.prebid.server.bidder.nanointeractive;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.nanointeractive.ExtImpNanointeractive;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class NanointeractiveBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/";

    private NanointeractiveBidder nanointeractiveBidder;

    @Before
    public void setUp() {
        nanointeractiveBidder = new NanointeractiveBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NanointeractiveBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldSkipInvalidImpressionAndAddError() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(identity()),
                        givenImp(impBuilder -> impBuilder.banner(null))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = nanointeractiveBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput(
                        "invalid MediaType. NanoInteractive only supports Banner type."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = nanointeractiveBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfPidEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpNanointeractive.of(null, singletonList("123"), "category", "subId", "ref")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = nanointeractiveBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly(BidderError.badInput("pid is empty"));
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithHttpRequestsContainingExpectedHeaders() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .ua("userAgent")
                        .ip("123.123.123.12")
                        .build())
                .user(User.builder()
                        .buyeruid("701")
                        .build())
                .site(Site.builder()
                        .page("http://www.example.com")
                        .build())
                .imp(Collections.singletonList(Imp.builder().build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = nanointeractiveBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .flatExtracting(r -> r.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("Content-Type", "application/json;charset=utf-8"),
                        tuple("Accept", "application/json"),
                        tuple("x-openrtb-version", "2.5"),
                        tuple("User-Agent", "userAgent"),
                        tuple("X-Forwarded-For", "123.123.123.12"),
                        tuple("Referer", "http://www.example.com"),
                        tuple("Cookie", "Nano=701"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = nanointeractiveBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidOnlyWithNotZeroPrice() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(asList(Bid.builder().impid("impId1").price(BigDecimal.valueOf(5.67)).build(),
                                Bid.builder().impid("impId2").price(BigDecimal.valueOf(0)).build()))
                        .build()))
                .cur("USD")
                .build());
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId").banner(Banner.builder().build()).build()))
                .build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest, response);

        // when
        final Result<List<BidderBid>> result = nanointeractiveBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(Bid.builder().impid("impId1").price(BigDecimal.valueOf(5.67)).build(),
                        BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = nanointeractiveBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(nanointeractiveBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap());
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
                .banner(Banner.builder().id("banner_id").build()).ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpNanointeractive.of("pid", singletonList("123"), "category", "subId", "ref")))))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

}
