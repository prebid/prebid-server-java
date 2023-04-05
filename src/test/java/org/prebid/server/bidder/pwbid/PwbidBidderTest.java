package org.prebid.server.bidder.pwbid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class PwbidBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://bid.pubwise.io/prebid";

    private PwbidBidder pwbidBidder;

    @Before
    public void setUp() {
        pwbidBidder = new PwbidBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PwbidBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHttpRequest() {
        // given
        final BidRequest bidRequest = BidRequest.builder().id("test-request-id").build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pwbidBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).allSatisfy(httpRequest -> {
            assertThat(httpRequest.getMethod()).isEqualTo(HttpMethod.POST);
            assertThat(httpRequest.getUri()).isEqualTo(ENDPOINT_URL);
            assertThat(httpRequest.getHeaders())
                    .extracting(Map.Entry::getKey, Map.Entry::getValue)
                    .containsExactly(
                            tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                            tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()));
            assertThat(httpRequest.getPayload()).isSameAs(bidRequest);
            assertThat(httpRequest.getBody()).isEqualTo(jacksonMapper.encodeToBytes(bidRequest));
        });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.id("123"));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = pwbidBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidsFromDifferentSeatBidsInResponse() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(asList(
                        SeatBid.builder()
                                .bid(singletonList(Bid.builder().id("bidId1").impid("impId1").build()))
                                .build(),
                        SeatBid.builder()
                                .bid(singletonList(Bid.builder().id("bidId2").impid("impId2").build()))
                                .build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder().id("impId1").banner(Banner.builder().build()).build(),
                        Imp.builder().id("impId2").banner(Banner.builder().build()).build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, response);

        // when
        final Result<List<BidderBid>> result = pwbidBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsExactly("bidId1", "bidId2");
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderWithErrorWhenResponseCanNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "{");

        // when
        final Result<List<BidderBid>> result = pwbidBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).allSatisfy(bidderError -> {
            assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(bidderError.getMessage()).startsWith("Failed to decode:");
        });
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(UnaryOperator.identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .id("test-request-id")
                        .site(Site.builder().id("site_id").build())
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123"))
                .banner(Banner.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createObjectNode())))
                .build();
    }
}
