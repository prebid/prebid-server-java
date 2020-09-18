package org.prebid.server.bidder.pubnative;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
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
import org.prebid.server.proto.openrtb.ext.request.pubnative.ExtImpPubnative;

import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class PubnativeBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private PubnativeBidder pubnativeBidder;

    @Before
    public void setUp() {
        pubnativeBidder = new PubnativeBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PubnativeBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfDeviceIsNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubnativeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Impression is missing device OS information"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfDeviceOsIsBlank() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().os(" ").build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubnativeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Impression is missing device OS information"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpHasNoBannerOrVideoOrNative() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.banner(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubnativeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Pubnative only supports banner, video or native ads."));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubnativeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpBannerHasNoSizeAndFormats() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.banner(Banner.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubnativeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Size information missing for banner"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestPerEachImpAndSkipOnesWithErrors() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                requestBuilder -> requestBuilder
                        .imp(asList(
                                givenImp(impBuilder -> impBuilder.id("imp1")),
                                givenImp(impBuilder -> impBuilder.id("imp2")),
                                givenImp(impBuilder -> impBuilder.banner(null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubnativeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getValue()).hasSize(2)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsOnly("imp1", "imp2");
    }

    @Test
    public void makeHttpRequestsShouldSetBannerHeightAndWidthFromFirstFormatWhenHeightIsNullOrZero() {
        // given
        final Format format = Format.builder().h(2).w(3).build();
        final BidRequest bidRequest = givenBidRequest(identity(),
                requestBuilder -> requestBuilder
                        .imp(asList(
                                givenImp(impBuilder -> impBuilder.banner(Banner.builder()
                                        .w(1)
                                        .format(singletonList(format))
                                        .build())),
                                givenImp(impBuilder -> impBuilder.banner(Banner.builder()
                                        .h(0)
                                        .w(1)
                                        .format(singletonList(format))
                                        .build())))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubnativeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getH, Banner::getW)
                .containsOnly(tuple(2, 3));
    }

    @Test
    public void makeHttpRequestsShouldSetBannerHeightAndWidthFromFirstFormatWhenWidthIsNullOrZero() {
        // given
        final Format format = Format.builder().h(2).w(3).build();
        final BidRequest bidRequest = givenBidRequest(identity(),
                requestBuilder -> requestBuilder
                        .imp(asList(
                                givenImp(impBuilder -> impBuilder.banner(Banner.builder()
                                        .h(1)
                                        .format(singletonList(format))
                                        .build())),
                                givenImp(impBuilder -> impBuilder.banner(Banner.builder()
                                        .w(0)
                                        .h(1)
                                        .format(singletonList(format))
                                        .build())))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubnativeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getH, Banner::getW)
                .containsOnly(tuple(2, 3));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = pubnativeBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = pubnativeBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = pubnativeBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = pubnativeBidder.makeBids(httpCall, null);

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
                                .id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = pubnativeBidder.makeBids(httpCall, null);

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
                                .id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = pubnativeBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), xNative, "USD"));
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(pubnativeBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap());
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impModifier,
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> requestModifier) {

        return requestModifier.apply(BidRequest.builder()
                .device(Device.builder().os("OS").build())
                .imp(singletonList(givenImp(impModifier))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impModifier) {
        return givenBidRequest(impModifier, identity());
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impModifier) {
        return impModifier.apply(Imp.builder()
                .banner(Banner.builder().h(1).w(1).build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpPubnative.of(1, "auth")))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
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
