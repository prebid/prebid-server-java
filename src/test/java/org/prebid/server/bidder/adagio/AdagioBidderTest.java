package org.prebid.server.bidder.adagio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.bidder.model.BidderError.badServerResponse;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

@ExtendWith(MockitoExtension.class)
public class AdagioBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private final AdagioBidder target = new AdagioBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdagioBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCreateExpectedUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com");
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedBody() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("imp"));

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).hasSize(1)
                .extracting(HttpRequest::getBody, HttpRequest::getPayload)
                .containsExactly(tuple(jacksonMapper.encodeToBytes(bidRequest), bidRequest));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid_json");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token 'invalid_json'");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidResponseOrSeatBidAreNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> invalidHttpCall = givenHttpCall(mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(invalidHttpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1).containsExactly(badServerResponse("empty seatbid array"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidWhenMtypeIs1() throws JsonProcessingException {
        // given
        final Bid bannerBid = givenBid(1);
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(bannerBid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidWhenMtypeIs2() throws JsonProcessingException {
        // given
        final Bid videoBid = givenBid(2, ext -> ext.video(ExtBidPrebidVideo.of(10, "cat")));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(videoBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.builder()
                        .type(BidType.video)
                        .bidCurrency("USD")
                        .bid(videoBid)
                        .videoInfo(ExtBidPrebidVideo.of(10, "cat"))
                        .build());
    }

    @Test
    public void makeBidsShouldReturnNativeBidWhenMtypeIs4() throws JsonProcessingException {
        // given
        final Bid nativeBid = givenBid(4);
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(nativeBid, BidType.xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMtypeIsMissing() throws JsonProcessingException {
        // given
        final Bid bidWithMissingMtype = givenBid(null);
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bidWithMissingMtype));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badServerResponse("Could not define media type for impression: impId"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMtypeIsUnsupported() throws JsonProcessingException {
        // given
        final Bid bidWithUnsupportedMtype = givenBid(3);
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bidWithUnsupportedMtype));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badServerResponse("Could not define media type for impression: impId"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return BidRequest.builder().imp(singletonList(givenImp(impCustomizer))).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("impId")).build();
    }

    private static Bid givenBid(Integer mtype) {
        return givenBid(mtype, identity());
    }

    private static Bid givenBid(Integer mtype, UnaryOperator<ExtBidPrebid.ExtBidPrebidBuilder> extCustomizer) {
        final ExtBidPrebid extBidPrebid = extCustomizer.apply(ExtBidPrebid.builder()).build();
        return Bid.builder()
                .id("bidId")
                .impid("impId")
                .price(BigDecimal.ONE)
                .mtype(mtype)
                .ext(mapper.valueToTree(extBidPrebid))
                .build();
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
