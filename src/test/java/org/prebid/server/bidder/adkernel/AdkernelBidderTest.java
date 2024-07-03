package org.prebid.server.bidder.adkernel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adkernel.ExtImpAdkernel;
import org.prebid.server.util.HttpUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class AdkernelBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.com?zone=%s";

    private final AdkernelBidder target = new AdkernelBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new AdkernelBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpHasNoBannerOrVideo() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").audio(Audio.builder().build()).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badInput("Invalid imp id=123. Expected imp.banner / imp.video / imp.native"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).allMatch(error -> error.getType() == BidderError.Type.bad_input
                && error.getMessage().startsWith("Cannot deserialize value"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtZoneIdisEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(), impBuilder -> impBuilder.ext(givenImpExt(null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Invalid zoneId value: null. Ignoring imp id=123"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtZoneIdIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(), impBuilder -> impBuilder.ext(givenImpExt(0)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Invalid zoneId value: 0. Ignoring imp id=123"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetExpectedMethodUrlAndHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).element(0).isNotNull()
                .returns(HttpMethod.POST, HttpRequest::getMethod)
                .returns("https://test.com?zone=3426", HttpRequest::getUri);
        assertThat(result.getValue().getFirst().getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), "application/json;charset=utf-8"),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), "application/json"),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5"));
    }

    @Test
    public void makeHttpRequestShouldAlwaysSetImpExtNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.site(Site.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt).hasSize(1)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldSplitImpsByMediaTypeAndAddSuffixToImpId() {
        // give
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                impBuilder -> impBuilder
                        .id("impId")
                        .banner(Banner.builder().build())
                        .video(Video.builder().build())
                        .xNative(Native.builder().build())
                        .audio(Audio.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final Imp expectedBannerImp = Imp.builder().id("impIdb__mf").banner(Banner.builder().build()).build();
        final Imp expectedVideoImp = Imp.builder().id("impIdv__mf").video(Video.builder().build()).build();
        final Imp expectedAudioImp = Imp.builder().id("impIda__mf").audio(Audio.builder().build()).build();
        final Imp expectedNativeImp = Imp.builder().id("impIdn__mf").xNative(Native.builder().build()).build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .containsExactlyInAnyOrder(expectedBannerImp, expectedVideoImp, expectedAudioImp, expectedNativeImp);
    }

    @Test
    public void makeHttpRequestShouldModifySite() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.site(Site.builder().publisher(Publisher.builder().build()).build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .containsExactly(Site.builder().publisher(null).build());
    }

    @Test
    public void makeHttpRequestShouldModifyApp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.site(null)
                        .app(App.builder().publisher(Publisher.builder().build()).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getApp)
                .containsExactly(App.builder().publisher(null).build());
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                && error.getMessage().startsWith("Failed to decode: Unrecognized token"));
        assertThat(result.getErrors().getFirst().getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseisEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidisEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenSeatBidsCountIsNotOne() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().seatbid(emptyList()).build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Invalid SeatBids count: 0"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldResolveBidTypeFromMfSuffixWhenPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(
                        identity(),
                        impBuilder -> impBuilder.id("123").banner(Banner.builder().build())
                                .video(Video.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder.impid("123b__mf"),
                                bidBuilder -> bidBuilder.impid("123v__mf"),
                                bidBuilder -> bidBuilder.impid("123a__mf"),
                                bidBuilder -> bidBuilder.impid("123n__mf"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactlyInAnyOrder(
                        BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"),
                        BidderBid.of(Bid.builder().impid("123").build(), video, "USD"),
                        BidderBid.of(Bid.builder().impid("123").build(), audio, "USD"),
                        BidderBid.of(Bid.builder().impid("123").build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldResolveVideoBidTypeFromImpWhenMfSuffixIsAbsentAndVideoPresentInRequestImp()
            throws JsonProcessingException {

        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(
                        identity(),
                        impBuilder -> impBuilder
                                .id("123")
                                .video(Video.builder().build())
                                .banner(null)
                                .audio(null)
                                .xNative(null)),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(
                BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldResolveBannerBidTypeFromImpWhenMfSuffixIsAbsentAndBannerPresentInRequestImp()
            throws JsonProcessingException {

        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(
                        identity(),
                        impBuilder -> impBuilder
                                .id("123")
                                .video(null)
                                .banner(Banner.builder().build())
                                .audio(null)
                                .xNative(null)),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(
                BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldResolveAudioBidTypeFromImpWhenMfSuffixIsAbsentAndAudioPresentInRequestImp()
            throws JsonProcessingException {

        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(
                        identity(),
                        impBuilder -> impBuilder
                                .id("123")
                                .video(null)
                                .banner(null)
                                .audio(Audio.builder().build())
                                .xNative(null)),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(
                BidderBid.of(Bid.builder().impid("123").build(), audio, "USD"));
    }

    @Test
    public void makeBidsShouldResolveNativeBidTypeFromImpWhenMfSuffixIsAbsentAndNativePresentInRequestImp()
            throws JsonProcessingException {

        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(
                        identity(),
                        impBuilder -> impBuilder
                                .id("123")
                                .video(null)
                                .banner(null)
                                .audio(null)
                                .xNative(Native.builder().build())),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(
                BidderBid.of(Bid.builder().impid("123").build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(
                        identity(),
                        impBuilder -> impBuilder.id("123")
                                .video(Video.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfRequestImpHasBanner() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(
                        identity(),
                        builder -> builder.id("123")
                                .video(Video.builder().build())
                                .banner(Banner.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @SafeVarargs
    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              UnaryOperator<Imp.ImpBuilder>... impCustomizers) {

        return bidRequestCustomizer.apply(
                        BidRequest.builder()
                                .site(Site.builder().build())
                                .imp(Arrays.stream(impCustomizers).map(AdkernelBidderTest::givenImp).toList()))
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .video(Video.builder().build())
                        .ext(givenImpExt(3426)))
                .build();
    }

    private static ObjectNode givenImpExt(Integer zoneId) {
        return mapper.valueToTree(ExtPrebid.of(null,
                ExtImpAdkernel.of(zoneId)));
    }

    @SafeVarargs
    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder>... bidCustomizers) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(Arrays.stream(bidCustomizers)
                                .map(bidCustomizer -> bidCustomizer.apply(Bid.builder()).build())
                                .toList())
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
