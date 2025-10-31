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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.ix.model.response.AuctionConfigExtBidResponse;
import org.prebid.server.bidder.ix.model.response.IxBidResponse;
import org.prebid.server.bidder.ix.model.response.IxExtBidResponse;
import org.prebid.server.bidder.ix.model.response.NativeV11Wrapper;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.CompositeBidderResponse;
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
import org.prebid.server.proto.openrtb.ext.response.ExtIgi;
import org.prebid.server.proto.openrtb.ext.response.ExtIgiIgs;
import org.prebid.server.version.PrebidVersionProvider;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

@ExtendWith(MockitoExtension.class)
public class IxBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://exchange.org/?s=${IX_ACCOUNT_ID}";
    private static final String DEFAULT_ACCOUNT_ID = "123456";
    private static final String SITE_ID = "site id";

    @Mock(strictness = LENIENT)
    private PrebidVersionProvider prebidVersionProvider;

    private IxBidder target;

    @BeforeEach
    public void setUp() {
        target = new IxBidder(ENDPOINT_URL, DEFAULT_ACCOUNT_ID, prebidVersionProvider, jacksonMapper);
        given(prebidVersionProvider.getNameVersionRecord()).willReturn(null);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new IxBidder("invalid_url", DEFAULT_ACCOUNT_ID, prebidVersionProvider, jacksonMapper));
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
        assertThat(result.getErrors().getFirst().getMessage()).startsWith("Cannot deserialize value");
    }

    @Test
    public void makeHttpRequestsShouldModifyImpExtWithSid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(givenImpExt(null, "sid", null)));

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
                        .ext(givenImpExt(null, null, null)));

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
                        .ext(givenImpExt(null, null, null)));

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
                .containsExactly(mapper.createObjectNode()
                        .put("pbsv", "unknown")
                        .put("pbjsv", "pbjsv")
                        .put("pbsp", "java"));
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
                .containsExactly(mapper.createObjectNode()
                        .put("pbsv", "pbsv")
                        .put("pbsp", "java"));
    }

    @Test
    public void makeHttpRequestsShouldReturnIxDiagWithMultipleSiteIdsWhenMultipleImpExtSiteIdPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(givenImpExt("site1", null, null)),
                impBuilder -> impBuilder.ext(givenImpExt("site2", null, null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("ixdiag"))
                .containsExactly(mapper.createObjectNode()
                        .put("pbsv", "unknown")
                        .put("pbsp", "java")
                        .put("multipleSiteIds", "site1, site2"));
    }

    @Test
    public void makeHttpRequestsShouldPassThroughProvidedIxDiagFields() {
        // given
        given(prebidVersionProvider.getNameVersionRecord()).willReturn("pbsv1");
        final ObjectNode givenIxDiag = mapper.createObjectNode();
        givenIxDiag.put("pbsv", "pbsv2");
        givenIxDiag.put("pbjsv", "pbjsv2");
        givenIxDiag.put("pbsp", "go");
        givenIxDiag.put("multipleSiteIds", "site1");
        givenIxDiag.put("property1", "value");
        givenIxDiag.put("property2", 1);

        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(givenExtRequestWithIxDiag(
                        "pbjsv1",
                        givenIxDiag)),
                List.of(
                        impBuilder -> impBuilder.ext(givenImpExt("site1", null, null)),
                        impBuilder -> impBuilder.ext(givenImpExt("site2", null, null))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final ObjectNode expectedIxDiag = mapper.createObjectNode();
        expectedIxDiag.put("pbsv", "pbsv1");
        expectedIxDiag.put("pbjsv", "pbjsv1");
        expectedIxDiag.put("pbsp", "java");
        expectedIxDiag.put("multipleSiteIds", "site1, site2");
        expectedIxDiag.put("property1", "value");
        expectedIxDiag.put("property2", 1);

        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("ixdiag"))
                .containsExactly(expectedIxDiag);
    }

    @Test
    public void makeHttpRequestsShouldModifyRequestSiteWithPublisherAndSetIdWhenImpExtSiteIdPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.site(Site.builder().build()),
                singletonList(impBuilder -> impBuilder.ext(givenImpExt("site1", null, null))));

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
                impBuilder -> impBuilder.ext(givenImpExt("site1", null, null)));

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
                singletonList(impBuilder -> impBuilder.ext(givenImpExt("site1", null, null))));

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
                impBuilder -> impBuilder.ext(givenImpExt("site1", null, null)));

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
    public void makeHttpRequestsShouldReplaceAccountIdMacroInEndpointUrlWithEXTAccountId() {
        // given
        final String accountId = "account123";
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(givenImpExt("site1", "sid", accountId)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri())
            .doesNotContain("${IX_ACCOUNT_ID}")
                .contains(accountId);
    }

    @Test
    public void makeHttpRequestsShouldReplaceAccountIdMacroInEndpointUrlWithDefaultAccountId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(givenImpExt("site1", "sid", null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri())
            .doesNotContain("${IX_ACCOUNT_ID}")
                .contains(DEFAULT_ACCOUNT_ID);
    }

    @Test
    public void makeBidderResponseShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().getFirst().getType()).isEqualTo(BidderError.Type.bad_server_response);
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
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), BidType.banner, "EUR"));
    }

    @Test
    public void makeBidderResponseShouldReturnNativeBidIfNativeIsPresent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), BidType.xNative, "EUR"));
    }

    @Test
    public void makeBidderResponseShouldReturnAudioBidIfAudioIsPresent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").audio(Audio.builder().build()).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), BidType.audio, "EUR"));
    }

    @Test
    public void makeBidderResponseShouldReturnErrorIfImpNotMatched() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("489"))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getBids()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Unmatched impression id 489"));
    }

    @Test
    public void makeBidderResponseShouldReturnBidWithVideoInfo() throws JsonProcessingException {
        // given
        final Video video = Video.builder().build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").video(video).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .impid("123")
                                        .ext(mapper.createObjectNode().putPOJO("prebid", ExtBidPrebid.builder()
                                                .video(ExtBidPrebidVideo.of(1, "cat"))
                                                .build())))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getVideoInfo)
                .extracting(ExtBidPrebidVideo::getDuration, ExtBidPrebidVideo::getPrimaryCategory)
                .containsExactly(tuple(1, null));
    }

    @Test
    public void makeBidderResponseShouldReturnAdmContainingImageTrackersUrls() throws JsonProcessingException {
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
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .adm(adm))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        final Response expectedNativeResponse = Response.builder()
                .imptrackers(asList("eventUrl", "impTracker"))
                .eventtrackers(singletonList(EventTracker.builder()
                        .method(EventTrackingMethod.IMAGE.getValue())
                        .url("eventUrl")
                        .build()))
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly(mapper.writeValueAsString(expectedNativeResponse));
    }

    @Test
    public void makeBidderResponseShouldReturnAdmContainingImpTrackersAndEventImpTrackersUrls()
            throws JsonProcessingException {
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
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .adm(adm))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        final Response expectedNativeResponse = Response.builder()
                .imptrackers(asList("eventUrl", "impTracker"))
                .eventtrackers(singletonList(EventTracker.builder()
                        .event(EventType.IMPRESSION.getValue())
                        .url("eventUrl")
                        .build()))
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly(mapper.writeValueAsString(expectedNativeResponse));
    }

    @Test
    public void makeBidderResponseShouldReturnAdmContainingEventImpTrackersUrlsWhenImpTrackersAreNull()
            throws JsonProcessingException {
        // given
        final String adm = mapper.writeValueAsString(
                Response.builder()
                        .eventtrackers(singletonList(
                                EventTracker.builder()
                                        .event(EventType.IMPRESSION.getValue())
                                        .url("eventUrl")
                                        .build()))
                        .build());
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .adm(adm))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        final Response expectedNativeResponse = Response.builder()
                .imptrackers(asList("eventUrl"))
                .eventtrackers(singletonList(EventTracker.builder()
                        .event(EventType.IMPRESSION.getValue())
                        .url("eventUrl")
                        .build()))
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly(mapper.writeValueAsString(expectedNativeResponse));
    }

    @Test
    public void makeBidderResponseShouldReturnAdmWithoutImpTrackers()
            throws JsonProcessingException {
        // given
        final String adm = mapper.writeValueAsString(
                Response.builder()
                        .eventtrackers(singletonList(
                                EventTracker.builder()
                                        .event(EventType.VIEWABLE_VIDEO50.getValue())
                                        .url("eventUrl")
                                        .build()))
                        .build());
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .adm(adm))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        final Response expectedNativeResponse = Response.builder()
                .imptrackers(emptyList())
                .eventtrackers(singletonList(EventTracker.builder()
                        .event(EventType.VIEWABLE_VIDEO50.getValue())
                        .url("eventUrl")
                        .build()))
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly(mapper.writeValueAsString(expectedNativeResponse));
    }

    @Test
    public void makeBidderResponseShouldReturnAdmContainingOnlyUniqueImpTrackersUrls() throws JsonProcessingException {
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
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .adm(adm))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .extracting(bidAdm -> mapper.readValue(bidAdm, Response.class))
                .flatExtracting(Response::getImptrackers)
                .containsExactlyInAnyOrder("eventUrl", "someTracker", "impTracker");
    }

    @Test
    public void makeBidderResponseShouldReturnValidAdmIfNativeIsPresentInImpAndAdm12() throws JsonProcessingException {
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
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .adm(adm))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

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
        assertThat(result.getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly(mapper.writeValueAsString(expectedNativeResponse));
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
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .impid("123")
                                        .mtype(1))));

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
                        .video(video)
                        .build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .impid("123")
                                        .mtype(2))));

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
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .impid("123")
                                        .mtype(3))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.audio);
    }

    @Test
    public void makeBidderResponseShouldReturnNativeBidIfMTypeIsFour() throws JsonProcessingException {
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
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .impid("123")
                                        .mtype(4))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.xNative);
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
                mapper.writeValueAsString(
                        givenBidResponse(
                                bidBuilder -> bidBuilder
                                        .impid("123")
                                        .ext(mapper.createObjectNode()
                                                .set("prebid", mapper.createObjectNode().put("type", "video"))))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidderResponseShouldReturnFledgeAuctionConfig() throws JsonProcessingException {
        // given
        final String impId = "imp_id";
        final BidResponse bidResponse = givenBidResponse(bidBuilder -> bidBuilder.impid(impId).mtype(1));
        final ObjectNode auctionConfig = mapper.createObjectNode();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(Imp.builder().id(impId).build()))
                .build();
        final IxBidResponse bidResponseWithFledge = IxBidResponse.builder()
                .cur(bidResponse.getCur())
                .seatbid(bidResponse.getSeatbid())
                .ext(IxExtBidResponse.of(List.of(AuctionConfigExtBidResponse.of(impId, auctionConfig))))
                .build();
        final BidderCall<BidRequest> httpCall =
                givenHttpCall(bidRequest, mapper.writeValueAsString(bidResponseWithFledge));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, bidRequest);

        // then
        final ExtIgiIgs igs = ExtIgiIgs.builder()
                .impId(impId)
                .config(auctionConfig)
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .containsOnly(BidderBid.of(Bid.builder().impid(impId).mtype(1).build(), banner, bidResponse.getCur()));
        assertThat(result.getIgi()).containsExactly(ExtIgi.builder().igs(singletonList(igs)).build());
    }

    private static ExtRequest givenExtRequest(String pbjsv) {
        return ExtRequest.of(ExtRequestPrebid.builder()
                .channel(ExtRequestPrebidChannel.of("pbjs", pbjsv))
                .build());
    }

    private static ExtRequest givenExtRequestWithIxDiag(String pbjsv, ObjectNode ixDiag) {
        final ExtRequest extRequest = givenExtRequest(pbjsv);
        extRequest.addProperty("ixdiag", ixDiag);
        return extRequest;
    }

    private static ObjectNode givenImpExt(String siteId, String sid, String accountId) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpIx.of(siteId, null, sid, accountId)));
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
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpIx.of(SITE_ID, null, null, null)))))
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
