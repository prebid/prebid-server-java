package org.prebid.server.bidder.yieldone;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
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
import org.prebid.server.proto.openrtb.ext.request.yieldone.ExtImpYieldone;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class YieldoneBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private YieldoneBidder yieldoneBidder;

    @Before
    public void setUp() {
        yieldoneBidder = new YieldoneBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new YieldoneBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(500).build())).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))).build()))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = yieldoneBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize value");
    }

    @Test
    public void makeHttpRequestsShouldNotChangeBannerWidthAndHeightIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(500).build()))
                                .w(200)
                                .h(150)
                                .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = yieldoneBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getW, Banner::getH)
                .containsOnly(tuple(200, 150));
    }

    @Test
    public void makeHttpRequestsShouldSetBannerWidthAndHeightFromFirstFormatIfEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .format(Arrays.asList(Format.builder().w(300).h(500).build(),
                                        Format.builder().w(450).h(150).build()))
                                .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = yieldoneBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getW, Banner::getH)
                .containsOnly(tuple(300, 500));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall("false");

        // when
        final Result<List<BidderBid>> result = yieldoneBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_input);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = yieldoneBidder.makeBids(httpCall,
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), BidType.banner, "USD"));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedBidRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = yieldoneBidder.makeHttpRequests(bidRequest);

        // then
        final BidRequest expectedRequest = bidRequest.toBuilder()
                .imp(singletonList(bidRequest.getImp().get(0).toBuilder().build()))
                .build();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsOnly(expectedRequest);
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfVideoIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = yieldoneBidder.makeBids(httpCall,
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").video(Video.builder().build()).build()))
                        .build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorWithUnknownBidTypeIfNotSupportedBidType() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = yieldoneBidder.makeBids(httpCall,
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").audio(Audio.builder().build()).build()))
                        .build());

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Unknown impression type with id 123"));
        assertThat(result.getValue()).isEmpty();
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
                        ExtImpYieldone.of("placementId")))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
