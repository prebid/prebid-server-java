package org.prebid.server.bidder.ix;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.request.ntv.EventTrackingMethod;
import com.iab.openrtb.request.ntv.EventType;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.EventTracker;
import com.iab.openrtb.response.Response;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.ix.model.response.NativeV11Wrapper;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ix.ExtImpIx;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class IxBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://exchange.org/";
    private static final int REQUEST_LIMIT = 20;
    private static final String SITE_ID = "site id";

    private IxBidder ixBidder;

    @Before
    public void setUp() {
        ixBidder = new IxBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new IxBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ixBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnWhenImpHasNoBanner() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(null).video(Video.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ixBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetSitePublisherIdFromImpExtWhenSitePresent() {
        // given
        final Banner banner = Banner.builder().w(100).h(200).build();
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.site(Site.builder().build()),
                impBuilder -> impBuilder.banner(banner));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ixBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getId)
                .containsOnly(SITE_ID);
    }

    @Test
    public void makeHttpRequestsShouldCreateBannerFormatIfOnlyBannerHeightAndWidthArePresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder()
                        .w(300)
                        .h(200)
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ixBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .containsOnly(Format.builder().w(300).h(200).build());
    }

    @Test
    public void makeHttpRequestsShouldSetBannerHeightAndWidthFromBannerFormat() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder()
                        .format(singletonList(Format.builder().w(300).h(200).build()))
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ixBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .containsOnly(Banner.builder()
                        .format(singletonList(Format.builder().w(300).h(200).build()))
                        .w(300).h(200)
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldCreateOneRequestPerImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(impBuilder -> impBuilder
                                .id("123")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(300).h(200).build())).build())),
                        givenImp(impBuilder -> impBuilder
                                .id("321")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(600).h(400).build())).build()))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ixBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsOnly("123", "321");
    }

    @Test
    public void makeHttpRequestsShouldCreateOneRequestPerBannerFormat() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder()
                        .format(asList(
                                Format.builder().w(300).h(200).build(),
                                Format.builder().w(600).h(400).build()))
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ixBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .hasSize(2);
    }

    @Test
    public void makeHttpRequestsShouldLimitBannerFormatsAmount() {
        // given
        final List<Format> formats = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            formats.add(Format.builder().w(i + 10).h(i + 5).build());
        }
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder()
                        .format(formats)
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ixBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(REQUEST_LIMIT);
    }

    @Test
    public void makeHttpRequestsShouldLimitImpsAmount() {
        // given
        final List<Imp> imps = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            int value = i;
            imps.add(givenImp(impBuilder -> impBuilder.banner(Banner.builder()
                    .format(singletonList(Format.builder().w(value).h(value).build())).build())));
        }
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder.imp(imps), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ixBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(REQUEST_LIMIT);
    }

    @Test
    public void makeHttpRequestsShouldLimitTotalAmountOfRequests() {
        // given
        final List<Imp> imps = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            int value = i;
            imps.add(givenImp(impBuilder -> impBuilder.banner(Banner.builder()
                    .format(asList(
                            Format.builder().w(value).h(value).build(),
                            Format.builder().w(value + 1).h(value).build()))
                    .build())));
        }
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder.imp(imps),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ixBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(REQUEST_LIMIT);
    }

    @Test
    public void makeHttpRequestsShouldPrioritizeFirstFormatPerImpOverOtherFormats() {
        // given
        final List<Imp> imps = new ArrayList<>();
        for (int i = 0; i <= REQUEST_LIMIT; i++) {
            int priority = i;
            int other = i + 25;
            imps.add(givenImp(impBuilder -> impBuilder.banner(Banner.builder()
                    .format(asList(
                            Format.builder().w(priority).h(priority).build(),
                            Format.builder().w(other).h(other).build()))
                    .build())));
        }
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder.imp(imps), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = ixBidder.makeHttpRequests(bidRequest);

        // then
        final List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < REQUEST_LIMIT; i++) {
            expected.add(i);
        }

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(REQUEST_LIMIT)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .extracting(Format::getW)
                .isEqualTo(expected);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = ixBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = ixBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = ixBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = ixBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), BidType.banner, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfNativeIsPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = ixBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), BidType.xNative, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnAudioBidIfAudioIsPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").audio(Audio.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = ixBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), BidType.audio, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpNotMatched() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("489"))));

        // when
        final Result<List<BidderBid>> result = ixBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Unmatched impression id 489"));
    }

    @Test
    public void makeBidsShouldReturnBidWithVideoExt() throws JsonProcessingException {
        // given
        final Video video = Video.builder().build();
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").video(video).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .impid("123")
                                        .ext(mapper.valueToTree(ExtBidPrebid.builder()
                                                .video(ExtBidPrebidVideo.of(1, "cat"))
                                                .build())))));

        // when
        final Result<List<BidderBid>> result = ixBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .extracting(node -> mapper.treeToValue(node, ExtBidPrebid.class))
                .extracting(ExtBidPrebid::getVideo)
                .extracting(ExtBidPrebidVideo::getDuration, ExtBidPrebidVideo::getPrimaryCategory)
                .containsExactly(tuple(1, null));
    }

    @Test
    public void makeBidsShouldReturnAdmContainingImageTrackersUrls() throws JsonProcessingException {
        // given
        final String adm = mapper.writeValueAsString(
                Response.builder()
                        .imptrackers(singletonList("impTracker"))
                        .eventtrackers(singletonList(
                                EventTracker.builder()
                                        .method(EventTrackingMethod.IMAGE.getValue())
                                        .url("eventUrl")
                                        .build()))
                        .build());
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .adm(adm))));

        // when
        final Result<List<BidderBid>> result = ixBidder.makeBids(httpCall, null);

        // then
        final Response expectedNativeResponse = Response.builder()
                .imptrackers(asList("eventUrl", "impTracker"))
                .eventtrackers(singletonList(EventTracker.builder()
                        .method(EventTrackingMethod.IMAGE.getValue())
                        .url("eventUrl")
                        .build()))
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly(mapper.writeValueAsString(expectedNativeResponse));
    }

    @Test
    public void makeBidsShouldReturnAdmContainingImpTrackersAndEventImpTrackersUrls() throws JsonProcessingException {
        // given
        final String adm = mapper.writeValueAsString(
                Response.builder()
                        .imptrackers(singletonList("impTracker"))
                        .eventtrackers(singletonList(
                                EventTracker.builder()
                                        .event(EventType.IMPRESSION.getValue())
                                        .url("eventUrl")
                                        .build()))
                        .build());
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .adm(adm))));

        // when
        final Result<List<BidderBid>> result = ixBidder.makeBids(httpCall, null);

        // then
        final Response expectedNativeResponse = Response.builder()
                .imptrackers(asList("eventUrl", "impTracker"))
                .eventtrackers(singletonList(EventTracker.builder()
                        .event(EventType.IMPRESSION.getValue())
                        .url("eventUrl")
                        .build()))
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly(mapper.writeValueAsString(expectedNativeResponse));
    }

    @Test
    public void makeBidsShouldReturnAdmContainingOnlyUniqueImpTrackersUrls() throws JsonProcessingException {
        // given
        final String adm = mapper.writeValueAsString(
                Response.builder()
                        .imptrackers(asList("impTracker", "someTracker"))
                        .eventtrackers(asList(
                                EventTracker.builder()
                                        .event(EventType.IMPRESSION.getValue())
                                        .url("eventUrl")
                                        .build(),
                                EventTracker.builder()
                                        .event(EventTrackingMethod.IMAGE.getValue())
                                        .url("someTracker")
                                        .build()))
                        .build());
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .adm(adm))));

        // when
        final Result<List<BidderBid>> result = ixBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .extracting(bidAdm -> mapper.readValue(bidAdm, Response.class))
                .flatExtracting(Response::getImptrackers)
                .containsExactlyInAnyOrder("eventUrl", "someTracker", "impTracker");
    }

    @Test
    public void makeBidsShouldReturnValidAdmIfNativeIsPresentInImpAndAdm12() throws JsonProcessingException {
        // given
        final String adm = mapper.writeValueAsString(NativeV11Wrapper.of(
                Response.builder()
                        .imptrackers(singletonList("ImpTracker"))
                        .eventtrackers(singletonList(
                                EventTracker.builder()
                                        .event(EventType.IMPRESSION.getValue())
                                        .method(EventTrackingMethod.IMAGE.getValue())
                                        .url("EventUrl")
                                        .build()))
                        .build()));
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .adm(adm))));

        // when
        final Result<List<BidderBid>> result = ixBidder.makeBids(httpCall, null);

        // then
        final NativeV11Wrapper expectedNativeResponse = NativeV11Wrapper.of(Response.builder()
                .imptrackers(asList("EventUrl", "ImpTracker"))
                .eventtrackers(singletonList(EventTracker.builder()
                        .method(EventTrackingMethod.IMAGE.getValue())
                        .event(EventType.IMPRESSION.getValue())
                        .url("EventUrl")
                        .build()))
                .build());

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly(mapper.writeValueAsString(expectedNativeResponse));
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
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpIx.of(SITE_ID, null)))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("EUR")
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
