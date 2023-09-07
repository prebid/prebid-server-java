package org.prebid.server.bidder.infytv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.prebid.server.util.HttpUtil.headers;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class InfytvBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://nxs.infy.tv/pbs/openrtb";

    private final InfytvBidder target = new InfytvBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new InfytvBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHttpRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final MultiMap expectedHeaders = headers()
                .set(CONTENT_TYPE_HEADER, APPLICATION_JSON_CONTENT_TYPE)
                .set(ACCEPT_HEADER, APPLICATION_JSON_VALUE);
        final Result<List<HttpRequest<BidRequest>>> expectedResult = Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(ENDPOINT_URL)
                .headers(expectedHeaders)
                .impIds(Set.of("IMP_ID"))
                .body(jacksonMapper.encodeToBytes(bidRequest))
                .payload(bidRequest)
                .build());
        assertThat(result.getValue()).usingRecursiveComparison().isEqualTo(expectedResult.getValue());
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnExpectedResult() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder().id("impId").build();
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .cur("PLN")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bid))
                        .build()))
                .build());

        final BidderCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).allSatisfy(bidderBid -> {
            assertThat(bidderBid.getBid()).isEqualTo(bid);
            assertThat(bidderBid.getType()).isEqualTo(BidType.video);
            assertThat(bidderBid.getBidCurrency()).isEqualTo("PLN");
        });
    }

    @Test
    public void makeBidsShouldReturnErrorWhenThereIsNoBid() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(null);

        final BidderCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Empty SeatBid array"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseCanNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("{");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).allSatisfy(bidderError -> {
            assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(bidderError.getMessage()).startsWith("Bad Response,");
        });
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        return bidRequestCustomizer.apply(
                BidRequest.builder().imp(List.of(Imp.builder().id("IMP_ID").build()))).build();
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(null, HttpResponse.of(204, null, body), null);
    }

}
