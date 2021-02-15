package org.prebid.server.bidder.visx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.visx.model.VisxBid;
import org.prebid.server.bidder.visx.model.VisxResponse;
import org.prebid.server.bidder.visx.model.VisxSeatBid;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.visx.ExtImpVisx;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class VisxBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private VisxBidder visxBidder;

    @Before
    public void setUp() {
        visxBidder = new VisxBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new VisxBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldNotModifyIncomingRequest() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpVisx.of(123, Arrays.asList(10, 20)))))
                        .build()))
                .id("request_id")
                .cur(singletonList("USD"))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = visxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsOnly(bidRequest);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = visxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = visxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = visxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldAlwaysReturnBidWithTypeBanner() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(Arrays.asList(Imp.builder().id("123").video(Video.builder().build()).build(),
                                Imp.builder().id("345").banner(Banner.builder().build()).build(),
                                Imp.builder().id("567").build()))
                        .build(),
                mapper.writeValueAsString(
                        VisxResponse.of(singletonList(VisxSeatBid.of(Arrays.asList(
                                VisxBid.builder().impid("123").build(),
                                VisxBid.builder().impid("345").build(),
                                VisxBid.builder().impid("567").build()), null)))));
        // when
        final Result<List<BidderBid>> result = visxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(
                        BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"),
                        BidderBid.of(Bid.builder().impid("345").build(), banner, "USD"),
                        BidderBid.of(Bid.builder().impid("567").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnCorrectBidderBid() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder().id("id").build();
        final VisxResponse visxResponse = VisxResponse.of(singletonList(VisxSeatBid.of(singletonList(
                VisxBid.builder()
                        .impid("123")
                        .price(BigDecimal.valueOf(10))
                        .crid("creativeid")
                        .dealid("dealid")
                        .adm("adm")
                        .w(200)
                        .h(100)
                        .adomain(singletonList("adomain"))
                        .build()), "seat")));

        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(visxResponse));

        // when
        final Result<List<BidderBid>> result = visxBidder.makeBids(httpCall, bidRequest);

        // then
        final BidderBid expected = BidderBid.of(
                Bid.builder()
                        .id("id")
                        .impid("123")
                        .price(BigDecimal.valueOf(10))
                        .crid("creativeid")
                        .dealid("dealid")
                        .adm("adm")
                        .w(200)
                        .h(100)
                        .adomain(singletonList("adomain"))
                        .build(),
                BidType.banner, "USD");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).doesNotContainNull()
                .hasSize(1).element(0).isEqualTo(expected);
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
