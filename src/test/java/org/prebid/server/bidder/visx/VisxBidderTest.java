package org.prebid.server.bidder.visx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

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
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsExactly(bidRequest);
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
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

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
    public void makeBidsShouldReturnBidWithTypeBannerIfBannerIsPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(imp -> imp.banner(Banner.builder().build())),
                mapper.writeValueAsString(givenVisxResponse(visxBidBuilder -> visxBidBuilder.impid("123"), null)));
        // when
        final Result<List<BidderBid>> result = visxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(
                BidderBid.of(Bid.builder().id("id").impid("123").build(), banner, null));
    }

    @Test
    public void makeBidsShouldReturnBidWithTypeBannerIfVideoIsPresentAndBannerIsAbsent()
            throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.video(Video.builder().build())),
                mapper.writeValueAsString(givenVisxResponse(visxBidBuilder -> visxBidBuilder.impid("123"), null)));
        // when
        final Result<List<BidderBid>> result = visxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(
                BidderBid.of(Bid.builder().id("id").impid("123").build(), video, null));
    }

    @Test
    public void makeBidsShouldReturnErrorIfVideoBannerAreAbsent()
            throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(givenVisxResponse(visxBidBuilder -> visxBidBuilder.impid("123"), null)));
        // when
        final Result<List<BidderBid>> result = visxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badServerResponse("Unknown impression type for ID: \"123\""));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresentInBidExt() throws JsonProcessingException {
        // given
        final VisxResponse visxResponse = givenVisxResponse(
                visxBidBuilder -> visxBidBuilder.impid("123"), null);

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()), mapper.writeValueAsString(visxResponse));

        // when
        final Result<List<BidderBid>> result = visxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badServerResponse("Unknown impression type for ID: \"123\""));
    }

    @Test
    public void makeBidsShouldFavourBidExtMediaTypeToImpMediaTypeWhenPresent() throws JsonProcessingException {
        // given
        final VisxResponse visxResponse = givenVisxResponse(
                visxBidBuilder -> visxBidBuilder
                        .impid("123")
                        .ext(givenBidExt("banner")),
                null);

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.video(Video.builder().build())),
                mapper.writeValueAsString(visxResponse));

        // when
        final Result<List<BidderBid>> result = visxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(givenBid(identity()), banner, null));
    }

    @Test
    public void makeBidsShouldReturnImpMediaTypeWhenBidExtMediaTypeIsAbsent() throws JsonProcessingException {
        // given
        final VisxResponse visxResponse = givenVisxResponse(
                visxBidBuilder -> visxBidBuilder.impid("123"), null);

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.video(Video.builder().build())),
                mapper.writeValueAsString(visxResponse));

        // when
        final Result<List<BidderBid>> result = visxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(
                BidderBid.of(givenBid(bidBuilder -> bidBuilder.ext(null)), video, null));
    }

    @Test
    public void makeBidsShouldReturnImpMediaTypeWhenBidExtMediaTypeIsInvalid() throws JsonProcessingException {
        // given
        final VisxResponse visxResponse = givenVisxResponse(
                visxBidBuilder -> visxBidBuilder
                        .impid("123")
                        .ext(givenBidExt("123")),
                null);

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.video(Video.builder().build())),
                mapper.writeValueAsString(visxResponse));

        // when
        final Result<List<BidderBid>> result = visxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(
                BidderBid.of(givenBid(bidBuilder -> bidBuilder.ext(givenBidExt("123"))), video, null));
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpIdsFromBidAndRequestWereNotMatchedAndImpWasNotFound()
            throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(givenVisxResponse(visxBidBuilder -> visxBidBuilder.impid("456"), null)));
        // when
        final Result<List<BidderBid>> result = visxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badServerResponse("Failed to find impression for ID: \"456\""));
    }

    @Test
    public void makeBidsShouldReturnCorrectBidderBid() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("123").video(Video.builder().build()));
        final VisxResponse visxResponse = givenVisxResponse(
                visxBidBuilder -> visxBidBuilder
                        .impid("123")
                        .price(BigDecimal.valueOf(10))
                        .crid("creativeid")
                        .dealid("dealid")
                        .adm("adm")
                        .w(200)
                        .h(100)
                        .adomain(singletonList("adomain")),
                "seat");

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
                video, null);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(expected);
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return BidRequest.builder()
                .id("id")
                .imp(singletonList(givenImp(impCustomizer))).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("123")).build();
    }

    private static VisxResponse givenVisxResponse(UnaryOperator<VisxBid.VisxBidBuilder> bidCustomizer, String seat) {
        return VisxResponse.of(singletonList(VisxSeatBid.of(
                singletonList(bidCustomizer.apply(VisxBid.builder()).build()), seat)));
    }

    private static Bid givenBid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder().id("id").impid("123").ext(givenBidExt("banner"))).build();
    }

    private static ObjectNode givenBidExt(String mediaType) {
        return mapper.createObjectNode()
                .set("prebid", mapper.createObjectNode()
                        .set("meta", mapper.createObjectNode()
                                .set("mediaType", TextNode.valueOf(mediaType))));
    }
}
