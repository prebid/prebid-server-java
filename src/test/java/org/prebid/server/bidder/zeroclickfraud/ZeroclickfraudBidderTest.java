package org.prebid.server.bidder.zeroclickfraud;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.proto.openrtb.ext.request.zeroclickfraud.ExtImpZeroclickfraud;

import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class ZeroclickfraudBidderTest extends VertxTest {

    private static final String ENDPOINT_TEMPLATE = "http://{{Host}}/openrtb2?sid={{SourceId}}";

    private ZeroclickfraudBidder zeroclickfraudBidder;

    @Before
    public void setUp() {
        zeroclickfraudBidder = new ZeroclickfraudBidder(ENDPOINT_TEMPLATE, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ZeroclickfraudBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURL() {
        // given
        final BidRequest bidRequest = givenBidRequest(ExtImpZeroclickfraud.of(10, "host"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = zeroclickfraudBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri()).isEqualTo("http://host/openrtb2?sid=10");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(mapper.createArrayNode());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = zeroclickfraudBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenSourceIdIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(ExtImpZeroclickfraud.of(null, "host"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = zeroclickfraudBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid/Missing SourceId"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenSourceIdIsLessThanOne() {
        // given
        final BidRequest bidRequest = givenBidRequest(ExtImpZeroclickfraud.of(0, "host"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = zeroclickfraudBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid/Missing SourceId"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenHostIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(ExtImpZeroclickfraud.of(2, null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = zeroclickfraudBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid/Missing Host"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenHostIsBlank() {
        // given
        final BidRequest bidRequest = givenBidRequest(ExtImpZeroclickfraud.of(2, "  "));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = zeroclickfraudBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid/Missing Host"));
    }

    @Test
    public void makeHttpRequestsShouldNotModifyIncomingRequestAndSetExpectedHttpRequestUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(ExtImpZeroclickfraud.of(2, "host"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = zeroclickfraudBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsOnly(bidRequest);
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsOnly("http://host/openrtb2?sid=2");
    }

    @Test
    public void makeHttpRequestsShouldMakeOneHttpRequestPerEachImpExtWithReplacedImps() {
        // given
        final Imp firstImp = Imp.builder().id("imp1")
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpZeroclickfraud.of(2, "host")))).build();
        final Imp secondImp = Imp.builder().id("imp2")
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpZeroclickfraud.of(3, "host1")))).build();
        final Imp thirdImp = Imp.builder().id("imp3")
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpZeroclickfraud.of(2, "host")))).build();

        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(firstImp, secondImp, thirdImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = zeroclickfraudBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsOnly(
                        BidRequest.builder().imp(asList(firstImp, thirdImp)).build(),
                        BidRequest.builder().imp(singletonList(secondImp)).build());
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsOnly("http://host/openrtb2?sid=2", "http://host1/openrtb2?sid=3");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = zeroclickfraudBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = zeroclickfraudBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = zeroclickfraudBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidByDeault() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = zeroclickfraudBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .video(Video.builder().build())
                                .id("123")
                                .build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = zeroclickfraudBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfNativeIsPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .xNative(Native.builder().build())
                                .id("123")
                                .build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = zeroclickfraudBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), xNative, "USD"));
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(zeroclickfraudBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap());
    }

    private static BidRequest givenBidRequest(Object extImpDatablocks) {
        return BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, extImpDatablocks)))
                        .build()))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
