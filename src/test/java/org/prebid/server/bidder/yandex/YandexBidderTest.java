package org.prebid.server.bidder.yandex;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.yandex.ExtImpYandex;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class YandexBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/";
    private final YandexBidder target = new YandexBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new YandexBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("blockA")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("imp #blockA: Cannot deserialize value");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenPageIdIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("blockA")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpYandex.of(0, 1)))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("imp #blockA: wrong value for page_id param"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpIdIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("blockA").ext(givenImpExt(0)),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("imp #blockA: wrong value for imp_id param"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateARequestForEachImpAndSkipImpsWithErrors() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(impBuilder -> impBuilder.id("blockA")),
                        givenImp(impBuilder -> impBuilder.id("blockB").ext(givenImpExt(0))),
                        givenImp(impBuilder -> impBuilder.id("blockC"))))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("imp #blockB: wrong value for imp_id param"));
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp).hasSize(2)
                .extracting(Imp::getId)
                .containsOnly("blockA", "blockC");
    }

    @Test
    public void makeHttpRequestsShouldCreateARequestForEachImpAndSkipImpsWithNoBanner() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        Imp.builder().id("blockA").ext(givenImpExt(1)).build(),
                        Imp.builder().id("blockB")
                                .banner(Banner.builder().w(300).h(600).build())
                                .ext(givenImpExt(2)).build(),
                        Imp.builder().id("blockC")
                                .xNative(Native.builder().build())
                                .ext(givenImpExt(3)).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);
        // then
        assertThat(result.getErrors()).containsExactly(
                BidderError.badInput("Yandex only supports banner, video, and native types. Ignoring imp id #blockA")
        );
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenBannerWidthIsZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("blockA").banner(Banner.builder().w(0).h(600).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Invalid sizes provided for Banner 0x600"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenBannerHeightIsZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("blockA").banner(Banner.builder().w(300).h(0).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Invalid sizes provided for Banner 300x0"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenBannerHasNoFormats() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("blockA").banner(Banner.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Invalid sizes provided for Banner nullxnull"));
    }

    @Test
    public void makeHttpRequestsSetFirstImpressionBannerWidthAndHeightWhenFromFirstFormatIfTheyAreNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("blockA")
                        .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(600).build())).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getW, Banner::getH)
                .containsOnly(tuple(300, 600));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenVideoWidthIsZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("blockA").banner(null).video(Video.builder().w(0).h(600).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Invalid sizes provided for Video 0x600"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenVideoHeightIsZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("blockA").banner(null).video(Video.builder().w(300).h(0).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Invalid sizes provided for Video 300x0"));
    }

    @Test
    public void makeHttpRequestsShouldModifyVideoParameters() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("blockA").banner(null)
                        .video(Video.builder().w(300).h(600).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo)
                .extracting(Video::getMinduration, Video::getMaxduration, Video::getMimes, Video::getProtocols)
                .containsOnly(tuple(1, 120, singletonList("video/mp4"), singletonList(3)));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getErrors())
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall(null,
                mapper.writeValueAsString(BidResponse.builder().seatbid(emptyList()).build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidImpIdIsNotPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("blockA").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("wrongBlock"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(
                BidderError.badServerResponse(
                        "Invalid bid imp ID #wrongBlock does not match any imp IDs from the original bid request"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerAndNative() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall(
                BidRequest.builder()
                        .imp(asList(Imp.builder().id("blockA").xNative(Native.builder().build()).build(),
                                Imp.builder().id("blockB").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(BidResponse.builder()
                        .cur("USD")
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(asList(Bid.builder().impid("blockA").build(),
                                        Bid.builder().impid("blockB").build()))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("blockA").build(), xNative, "USD"),
                        BidderBid.of(Bid.builder().impid("blockB").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoAndAudio() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall(
                BidRequest.builder()
                        .imp(asList(Imp.builder().id("blockA").video(Video.builder().build()).build(),
                                Imp.builder().id("blockB").audio(Audio.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(BidResponse.builder()
                        .cur("USD")
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(asList(Bid.builder().impid("blockA").build(),
                                        Bid.builder().impid("blockB").build()))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("blockA").build(), video, "USD"),
                        BidderBid.of(Bid.builder().impid("blockB").build(), audio, "USD"));
    }

    @Test
    public void makeBidsShouldReturnError() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("blockA").build()))
                        .build(),
                mapper.writeValueAsString(BidResponse.builder()
                        .cur("USD")
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(singletonList(Bid.builder().impid("blockA").build()))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(
                BidderError.badServerResponse(
                        "Processing an invalid impression; cannot resolve impression type for imp #blockA"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                requestBuilder -> requestBuilder.site(null)
                        .device(Device.builder().ua("UA").language("EN").ip("127.0.0.1").build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue().getFirst().getHeaders())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("Accept-Language", "EN"),
                        tuple("User-Agent", "UA"),
                        tuple("X-Forwarded-For", "127.0.0.1"),
                        tuple("X-Real-Ip", "127.0.0.1"),
                        tuple("Content-Type", "application/json;charset=utf-8"),
                        tuple("Accept", "application/json"));
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURL() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(impBuilder -> impBuilder.id("blockA").ext(givenImpExt(1)))))
                .site(Site.builder().id("1").page("https://domain.com/").build())
                .cur(asList("EUR", "USD"))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com/?"
                        + "target-ref=https%3A%2F%2Fdomain.com%2F&ssp-cur=EUR");
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> requestCustomizer) {
        return requestCustomizer.apply(BidRequest.builder()
                        .site(Site.builder().id("1").build())
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .banner(Banner.builder().w(300).h(600).build())
                        .ext(givenImpExt(1)))
                .build();
    }

    private static ObjectNode givenImpExt(int impId) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpYandex.of(134001, impId)));
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenBidderCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    @Test
    public void makeHttpRequestsShouldSetDisplayManagerAndVersionForAllImpTypes() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().id("1").build())
                .imp(asList(
                        Imp.builder().id("bannerImp")
                                .banner(Banner.builder().w(300).h(600).build())
                                .ext(givenImpExt(1))
                                .build(),
                        Imp.builder().id("videoImp")
                                .video(Video.builder().w(300).h(600).build())
                                .ext(givenImpExt(2))
                                .build(),
                        Imp.builder().id("nativeImp")
                                .xNative(Native.builder().build())
                                .ext(givenImpExt(3))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(3)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getDisplaymanager, Imp::getDisplaymanagerver)
                .containsOnly(
                        tuple("prebid.java", "1.1"),
                        tuple("prebid.java", "1.1"),
                        tuple("prebid.java", "1.1"));
    }
}
