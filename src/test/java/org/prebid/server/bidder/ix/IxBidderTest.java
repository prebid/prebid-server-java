package org.prebid.server.bidder.ix;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
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
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.ix.model.request.IxDiag;
import org.prebid.server.bidder.ix.model.response.NativeV11Wrapper;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ix.ExtImpIx;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.version.PrebidVersionProvider;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;

public class IxBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://exchange.org/";
    private static final String SITE_ID = "site id";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private PrebidVersionProvider prebidVersionProvider;

    private IxBidder target;

    @Before
    public void setUp() {
        target = new IxBidder(ENDPOINT_URL, prebidVersionProvider, jacksonMapper);
        given(prebidVersionProvider.getNameVersionRecord()).willReturn(null);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new IxBidder("invalid_url", prebidVersionProvider, jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
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
    public void makeHttpRequestsShouldModifyImpExtWithSid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(givenImpExt(null, "sid")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .hasSize(1)
                .allSatisfy(ext -> assertThat(ext.get("sid")).isEqualTo(TextNode.valueOf("sid")));
    }

    @Test
    public void makeHttpRequestsShouldSetImpBannerFormatsToFormatWithWidthAndHeightIfFormatsAreAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder().w(1).h(2).build())
                        .ext(givenImpExt(null, null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final Banner expectedBanner = Banner.builder()
                .w(1)
                .h(2)
                .format(singletonList(Format.builder().w(1).h(2).build()))
                .build();

        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .containsExactly(expectedBanner);
    }

    @Test
    public void makeHttpRequestsShouldSetImpBannerWidthAndHeightIfTheyAreAbsentAndBannerHasOnlyOneFormat() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder().format(singletonList(Format.builder().w(1).h(2).build())).build())
                        .ext(givenImpExt(null, null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final Banner expectedBanner = Banner.builder()
                .w(1)
                .h(2)
                .format(singletonList(Format.builder().w(1).h(2).build()))
                .build();

        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .containsExactly(expectedBanner);
    }

    @Test
    public void makeHttpRequestsShouldReturnIxDiagWithPbjsVersionIfRequestExtPrebidChannelVersionProvided() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(givenExtRequest("pbjsv")),
                singletonList(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("ixdiag"))
                .extracting(diagNode -> mapper.treeToValue(diagNode, IxDiag.class))
                .containsExactly(IxDiag.of(null, "pbjsv", null));
    }

    @Test
    public void makeHttpRequestsShouldReturnIxDiagWithPbsVersion() {
        // given
        given(prebidVersionProvider.getNameVersionRecord()).willReturn("pbsv");
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("ixdiag"))
                .extracting(diagNode -> mapper.treeToValue(diagNode, IxDiag.class))
                .containsExactly(IxDiag.of("pbsv", null, null));
    }

    @Test
    public void makeHttpRequestsShouldReturnIxDiagWithMultipleSiteIdsWhenMultipleImpExtSiteIdPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(givenImpExt("site1", null)),
                impBuilder -> impBuilder.ext(givenImpExt("site2", null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("ixdiag"))
                .extracting(diagNode -> mapper.treeToValue(diagNode, IxDiag.class))
                .containsExactly(IxDiag.of(null, null, "site1, site2"));
    }

    @Test
    public void makeHttpRequestsShouldModifyRequestSiteWithPublisherAndSetIdWhenImpExtSiteIdPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.site(Site.builder().build()),
                singletonList(impBuilder -> impBuilder.ext(givenImpExt("site1", null))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final Site exptectedSite = Site.builder()
                .publisher(Publisher.builder().id("site1").build())
                .build();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .containsExactly(exptectedSite);
    }

    @Test
    public void makeHttpRequestsShouldNotCreateRequestSiteWhenImpExtSiteIdPresentAndSiteIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(givenImpExt("site1", null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .containsOnlyNulls();
    }

    @Test
    public void makeHttpRequestsShouldModifyRequestAppWithPublisherAndSetIdWhenImpExtSiteIdPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.app(App.builder().build()),
                singletonList(impBuilder -> impBuilder.ext(givenImpExt("site1", null))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final App expectedApp = App.builder()
                .publisher(Publisher.builder().id("site1").build())
                .build();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .containsExactly(expectedApp);
    }

    @Test
    public void makeHttpRequestsShouldNotCreateRequestAppWhenImpExtSiteIdPresentAndSiteIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(givenImpExt("site1", null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .containsOnlyNulls();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
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
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
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
    public void makeBidsShouldReturnBannerBidIfBannerIsPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), BidType.banner, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfNativeIsPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), BidType.xNative, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnAudioBidIfAudioIsPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").audio(Audio.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), BidType.audio, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpNotMatched() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("489"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Unmatched impression id 489"));
    }

    @Test
    public void makeBidsShouldReturnBidWithVideoExt() throws JsonProcessingException {
        // given
        final Video video = Video.builder().build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
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
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

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
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .adm(adm))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

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
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .adm(adm))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

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
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .adm(adm))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

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
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .adm(adm))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

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

    @Test
    public void makeBidsShouldReturnBannerBidIfMTypeIsOne() throws JsonProcessingException {
        // given
        final Banner banner = Banner.builder().w(300).h(200).build();
        final Video video = Video.builder().build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .id("123")
                                .banner(banner)
                                .video(video).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .impid("123")
                                        .mtype(1))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfMTypeIsTwo() throws JsonProcessingException {
        // given
        final Banner banner = Banner.builder().w(300).h(200).build();
        final Video video = Video.builder().build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .id("123")
                                .banner(banner)
                                .video(video).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .impid("123")
                                        .mtype(2))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidsShouldReturnAudioBidIfMTypeIsThree() throws JsonProcessingException {
        // given
        final Banner banner = Banner.builder().w(300).h(200).build();
        final Video video = Video.builder().build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .id("123")
                                .banner(banner)
                                .video(video).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .impid("123")
                                        .mtype(3))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.audio);
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfMTypeIsFour() throws JsonProcessingException {
        // given
        final Banner banner = Banner.builder().w(300).h(200).build();
        final Video video = Video.builder().build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .id("123")
                                .banner(banner)
                                .video(video).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .impid("123")
                                        .mtype(4))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.xNative);
    }

    @Test
    public void makeBidsShouldReturnCorrectTypeExtPrebidTypeInResponse() throws JsonProcessingException {
        // given
        final Banner banner = Banner.builder().w(300).h(200).build();
        final Video video = Video.builder().build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .id("123")
                                .banner(banner)
                                .video(video).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .impid("123")
                                        .ext(mapper.createObjectNode()
                                                .set("prebid", mapper.createObjectNode().put("type", "video"))))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    private static ExtRequest givenExtRequest(String pbjsv) {
        return ExtRequest.of(ExtRequestPrebid.builder()
                .channel(ExtRequestPrebidChannel.of("pbjs", pbjsv))
                .build());
    }

    private static ObjectNode givenImpExt(String siteId, String sid) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpIx.of(siteId, null, sid)));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return givenBidRequest(identity(), List.of(impCustomizers));
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            List<UnaryOperator<Imp.ImpBuilder>> impCustomizers) {

        return bidRequestCustomizer.apply(
                        BidRequest.builder()
                                .imp(impCustomizers.stream()
                                        .map(IxBidderTest::givenImp)
                                        .toList()))
                .build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().w(1).h(1).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpIx.of(SITE_ID, null, null)))))
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

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
