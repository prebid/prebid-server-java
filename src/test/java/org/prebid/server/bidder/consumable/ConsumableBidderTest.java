package org.prebid.server.bidder.consumable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.CompositeBidderResponse;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.consumable.ExtImpConsumable;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class ConsumableBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://exchange.org/";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private PrebidVersionProvider prebidVersionProvider;

    private ConsumableBidder target;

    @Before
    public void setUp() {
        target = new ConsumableBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void makeHttpRequestsShouldHaveCorrectHeaders() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenSiteBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5")
                );
    }

    @Test
    public void makeHttpRequestsShouldHaveCorrectURIForSiteRequest() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenSiteBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(ENDPOINT_URL + ConsumableBidder.SITE_URI_PATH);
    }

    @Test
    public void makeHttpRequestsShouldHaveCorrectURIForAppRequest() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenAppBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(ENDPOINT_URL + ConsumableBidder.APP_URI_PATH + "0421008445828ceb46f496700a5fa65e");
    }

    @Test
    public void makeBidderResponseShouldReturnBidderBidWithNoErrors() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenSiteBidRequest(identity());
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                givenBidResponse(bidBuilder -> bidBuilder.impid("123")));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids()).isNotEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenSiteBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize value");
    }

    @Test
    public void makeHttpRequestsShouldReturnEmptyListIfRequiredSiteParametersAreNotPresent() {
        final BidRequest bidRequest = givenSiteBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpConsumable.of(11, 32,
                        0, "cnsmbl-audio-728x90-slider", null)))));

        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();

    }

    @Test
    public void makeHttpRequestsShouldReturnEmptyListIfRequiredAppParametersAreNotPresent() {
        final BidRequest bidRequest = givenAppBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpConsumable.of(null, 32,
                        null, null, null)))));
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();

    }

    @Test
    public void makeBidderResponseShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1).first().satisfies(error -> {
            assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
        });
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnBannerBidIfBannerIsPresent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                givenBidResponse(bidBuilder -> bidBuilder.impid("123")));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidderResponseShouldReturnAudioBidIfAudioIsPresent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").audio(Audio.builder().build()).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                givenBidResponse(bidBuilder -> bidBuilder.impid("123")));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.audio);
    }

    @Test
    public void makeBidderResponseShouldReturnErrorIfImpNotMatched() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                givenBidResponse(bidBuilder -> bidBuilder.impid("489")));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getBids()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Unmatched impression id 489"));
    }

    @Test
    public void makeBidderResponseShouldReturnBidWithVideoExt() throws JsonProcessingException {
        // given
        final Video video = Video.builder().build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").video(video).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                givenBidResponse(
                        bidBuilder -> bidBuilder
                                .impid("123")
                                .dur(1)
                                .mtype(2)));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getVideoInfo)
                .extracting(ExtBidPrebidVideo::getDuration)
                .containsExactly(1);
    }

    @Test
    public void makeBidderResponseShouldReturnBannerBidIfMTypeIsOne() throws JsonProcessingException {
        // given
        final Banner banner = Banner.builder().w(300).h(200).build();
        final Video video = Video.builder().build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(banner)
                        .video(video).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                givenBidResponse(
                        bidBuilder -> bidBuilder
                                .impid("123")
                                .mtype(1)));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidderResponseShouldReturnVideoBidIfMTypeIsTwo() throws JsonProcessingException {
        // given
        final Banner banner = Banner.builder().w(300).h(200).build();
        final Video video = Video.builder().build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(banner)
                        .video(video).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                givenBidResponse(
                        bidBuilder -> bidBuilder
                                .impid("123")
                                .mtype(2)));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidderResponseShouldReturnAudioBidIfMTypeIsThree() throws JsonProcessingException {
        // given
        final Banner banner = Banner.builder().w(300).h(200).build();
        final Video video = Video.builder().build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(banner)
                        .video(video).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                givenBidResponse(
                        bidBuilder -> bidBuilder
                                .impid("123")
                                .mtype(3)));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.audio);
    }

    @Test
    public void makeBidderResponseShouldReturnCorrectTypeExtPrebidTypeInResponse() throws JsonProcessingException {
        // given
        final Banner banner = Banner.builder().w(300).h(200).build();
        final Video video = Video.builder().build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(banner)
                        .video(video).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                givenBidResponse(
                        bidBuilder -> bidBuilder
                                .impid("123")
                                .ext(mapper.createObjectNode()
                                        .set("prebid", mapper.createObjectNode().put("type", "video")))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    private static BidRequest givenSiteBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return givenSiteBidRequest(bidRequestBuilder -> bidRequestBuilder.site(Site.builder()
                        .page("http://www.some.com/page-where-ad-will-be-shown").domain("www.some.com").build()),
                List.of(impCustomizers));
    }

    private static BidRequest givenSiteBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            List<UnaryOperator<Imp.ImpBuilder>> impCustomizers) {

        return bidRequestCustomizer.apply(
                BidRequest.builder()
                        .imp(impCustomizers.stream()
                                .map(ConsumableBidderTest::givenSiteImp)
                                .toList())).build();
    }

    private static BidRequest givenAppBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return givenAppBidRequest(bidRequestBuilder -> bidRequestBuilder.app(App.builder().id("1")
                .bundle("com.foo.bar").build()), List.of(impCustomizers));
    }

    private static BidRequest givenAppBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            List<UnaryOperator<Imp.ImpBuilder>> impCustomizers) {

        return bidRequestCustomizer.apply(
                        BidRequest.builder()
                                .imp(impCustomizers.stream()
                                        .map(ConsumableBidderTest::givenAppImp)
                                        .toList()))
                .build();
    }

    private static Imp givenSiteImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().w(1).h(1).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpConsumable.of(11, 32,
                                42, "cnsmbl-audio-728x90-slider", null)))))
                .build();
    }

    private static Imp givenAppImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().w(1).h(1).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpConsumable.of(null, null,
                                null, null, "0421008445828ceb46f496700a5fa65e")))))
                .build();
    }

    private static String givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer)
            throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()
                                .ext(mapper.valueToTree(ExtBidPrebid.builder().build()))).build()))
                        .build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
