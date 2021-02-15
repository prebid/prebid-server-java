package org.prebid.server.bidder.mobilefuse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
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
import org.prebid.server.proto.openrtb.ext.request.mobilefuse.ExtImpMobilefuse;

import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class MobilefuseBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/openrtb?pub_id=";

    private MobilefuseBidder mobilefuseBidder;

    @Before
    public void setUp() {
        mobilefuseBidder = new MobilefuseBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(()
                -> new MobilefuseBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURLWhenTagidSrcEqualsExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(500).build()))
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpMobilefuse.of(1, 2, "ext")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = mobilefuseBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com/openrtb?pub_id=2&tagid_src=ext");
    }

    @Test
    public void makeHttpRequestsShouldSetPubIdToZeroIfPublisherIdNotPresentInRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(500).build()))
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpMobilefuse.of(1, null, null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = mobilefuseBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com/openrtb?pub_id=0");
    }

    @Test
    public void makeHttpRequestsShouldModifyImpWithEmptyVideoWhenBannerAndVideoAreNotEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(500).build()))
                                .build())
                        .video(Video.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpMobilefuse.of(1, 2, "ext")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = mobilefuseBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestWithOnlyFirstValidImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(givenImp(identity()),
                        givenImp(impBuilder -> impBuilder.id("456"))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = mobilefuseBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("123");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfNoValidExtFound() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .id("456")
                .banner(null)
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = mobilefuseBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Invalid ExtImpMobilefuse value"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfNoValidImpsFound() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(impBuilder -> impBuilder
                                .id("456")
                                .banner(null)
                                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))),
                        givenImp(impBuilder -> impBuilder.banner(null).xNative(Native.builder().build()))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = mobilefuseBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("No valid imps"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestUsingParamsFromFirstValidExt() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(impBuilder -> impBuilder
                                .id("456")
                                .banner(null)
                                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))),
                        givenImp(impBuilder -> impBuilder.banner(null).xNative(Native.builder().build())),
                        givenImp(impBuilder -> impBuilder
                                .id("789")
                                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = mobilefuseBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId, Imp::getTagid)
                .containsExactly(tuple("789", "1"));
    }

    @Test
    public void makeHttpRequestsShouldModifyImpWhenBannerOrVideoAreNotEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(500).build()))
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpMobilefuse.of(1, 2, "ext")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = mobilefuseBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt, Imp::getTagid)
                .containsExactly(tuple(null, "1"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = mobilefuseBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = mobilefuseBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = mobilefuseBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = mobilefuseBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").video(Video.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = mobilefuseBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
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
                        ExtImpMobilefuse.of(1, 2, "tagidSrc")))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
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
