package org.prebid.server.bidder.appnexus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Imp.ImpBuilder;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExt;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExtAppnexus;
import org.prebid.server.bidder.appnexus.proto.AppnexusImpExt;
import org.prebid.server.bidder.appnexus.proto.AppnexusImpExtAppnexus;
import org.prebid.server.bidder.appnexus.proto.AppnexusKeyVal;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.appnexus.ExtImpAppnexus;
import org.prebid.server.proto.openrtb.ext.request.appnexus.ExtImpAppnexus.ExtImpAppnexusBuilder;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class AppnexusBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://test/auction";

    private static final Integer BANNER_TYPE = 0;
    private static final Integer VIDEO_TYPE = 1;
    private static final Integer AUDIO_TYPE = 2;
    private static final Integer NATIVE_TYPE = 3;

    private AppnexusBidder appnexusBidder;

    @Before
    public void setUp() {
        appnexusBidder = new AppnexusBidder(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldSkipImpAndAddErrorIfRequestContainsNotSupportedAudioMediaType() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(Collections.singletonList(Imp.builder().id("23").audio(Audio.builder().build())
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("Appnexus doesn't support audio Imps. Ignoring Imp ID=23"));
    }

    @Test
    public void makeHttpRequestsShouldAddErrorIfAppExtPrebidCouldNotbeParsed() {
        // given
        final ObjectNode badAppExtPrebid = mapper.createObjectNode();
        badAppExtPrebid.put("prebid", "bad value");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.app(App.builder().ext(badAppExtPrebid).build()),
                builder -> builder.video(Video.builder().build()),
                builder -> builder.placementId(20).invCode("invCode"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot construct instance of");
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder()
                                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtImpAppnexusDoesNotContainPlacementIdAndMemberId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                builder -> builder.video(Video.builder().build()),
                builder -> builder.placementId(null).member(null).invCode("invCode"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("No placement or member+invcode provided"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpsContainDifferentMemberIds() {
        // given
        final Imp imp1 = givenImp(impBuilder ->
                impBuilder.ext(givenExt(extBuilder -> extBuilder.placementId(12).member("member1")))
                        .video(Video.builder().build()));
        final Imp imp2 = givenImp(impBuilder ->
                impBuilder.ext(givenExt(builder -> builder.placementId(12).member("member2")))
                        .banner(Banner.builder().build()));
        final BidRequest bidRequest = BidRequest.builder().imp(asList(imp1, imp2)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("All request.imp[i].ext.appnexus.member params must match. "
                        + "Request contained: member2, member1"));
    }

    @Test
    public void makeHttpRequestsShouldSetImpDisplaymanagerverFromAppExtPrebid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .app(App.builder()
                                .ext(mapper.valueToTree(ExtApp.of(
                                        ExtAppPrebid.of("some source", "any version"), null)))
                                .build()),
                builder -> builder.banner(Banner.builder().build()),
                builder -> builder.placementId(20));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getDisplaymanagerver)
                .containsOnly("some source-any version");
    }

    @Test
    public void makeHttpRequestsShouldNotModifyImpDisplaymanagerverIfExtAppPrebidIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .app(App.builder()
                                .ext(mapper.valueToTree(ExtApp.of(null, null)))
                                .build()),
                builder -> builder.banner(Banner.builder().build()),
                builder -> builder.placementId(20));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getDisplaymanagerver)
                .hasSize(1).containsNull();
    }

    @Test
    public void makeHttpRequestsShouldNotModifyImpDisplaymanagerverIfExtAppPrebidSourceIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .app(App.builder()
                                .ext(mapper.valueToTree(ExtApp.of(
                                        ExtAppPrebid.of(null, "version"), null)))
                                .build()),
                builder -> builder.banner(Banner.builder().build()),
                builder -> builder.placementId(20));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getDisplaymanagerver)
                .hasSize(1).containsNull();
    }

    @Test
    public void makeHttpRequestsShouldNotModifyImpDisplaymanagerverIfExtAppPrebidVersionIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .app(App.builder()
                                .ext(mapper.valueToTree(ExtApp.of(
                                        ExtAppPrebid.of("source", null), null)))
                                .build()),
                builder -> builder.banner(Banner.builder().build()),
                builder -> builder.placementId(20));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getDisplaymanagerver)
                .hasSize(1).containsNull();
    }

    @Test
    public void makeHttpRequestsShouldNotModifyImpDisplaymanagerverIfItExists() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .app(App.builder()
                                .ext(mapper.valueToTree(ExtApp.of(
                                        ExtAppPrebid.of("some source", "any version"), null)))
                                .build()),
                builder -> builder.banner(Banner.builder().build()).displaymanagerver("string exists"),
                builder -> builder.placementId(20));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getDisplaymanagerver)
                .containsOnly("string exists");
    }

    @Test
    public void makeHttpRequestsShouldSetRequestUrlWithMemberIdParam() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                identity(),
                builder -> builder.placementId(20).invCode("tagid").member("member_param"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .hasSize(1)
                .element(0).returns("http://test/auction?member_id=member_param", HttpRequest::getUri);
    }

    @Test
    public void makeHttpRequestsShouldSetRequestUrlWithoutMemberIdIfItMissedRequestBodyImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                identity(),
                builder -> builder.placementId(20).invCode("tagid"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .hasSize(1)
                .element(0).returns(ENDPOINT_URL, HttpRequest::getUri);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtImpAppnexusDoesNotContainPlacementIdAndInvCode() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                builder -> builder.video(Video.builder().build()),
                builder -> builder.placementId(null).member("member").invCode(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("No placement or member+invcode provided"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtImpAppnexusPlacementIdAndBothInvCodeAndMemberIdAreEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                identity(),
                builder -> builder.placementId(null).member(null).invCode(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .isEmpty();

        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("No placement or member+invcode provided"));
    }

    @Test
    public void makeHttpRequestsShouldSetImpTagidAndImpBidFloorIfExtImpAppnexusHasInvCodeAndReserve() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                identity(),
                builder -> builder.placementId(20).invCode("tagid").reserve(BigDecimal.TEN));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(res -> mapper.readValue(res.getBody(), BidRequest.class))
                .element(0).extracting(BidRequest::getImp).hasSize(1)
                .containsOnly(singletonList(Imp.builder()
                        .bidfloor(BigDecimal.valueOf(10))
                        .tagid("tagid")
                        .ext(mapper.valueToTree(
                                AppnexusImpExt.of(AppnexusImpExtAppnexus.of(20, null, null, null, null))))
                        .build()));
    }

    @Test
    public void makeHttpRequestsShouldSetNativeIfRequestImpIsNative() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                builder -> builder.xNative(Native.builder().build()),
                builder -> builder.placementId(20).invCode("tagid"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(1)
                .extracting(Imp::getXNative).doesNotContainNull();
    }

    @Test
    public void makeHttpRequestsShouldSetVideoTypeIfRequestImpIsVideo() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                builder -> builder.video(Video.builder().build()),
                builder -> builder.placementId(20).invCode("tagid"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(1)
                .extracting(Imp::getVideo).doesNotContainNull();
    }

    @Test
    public void makeHttpRequestsShouldSetBannerIfRequestImpIsBanner() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                builder -> builder.banner(Banner.builder().build()),
                builder -> builder.placementId(20).invCode("tagid"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(1)
                .extracting(Imp::getBanner).doesNotContainNull();
    }

    @Test
    public void makeHttpRequestsShouldSetBannerSizesFromExistingFirstFormatElement() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                builder -> builder.banner(Banner.builder().format(singletonList(Format.builder().w(100).h(200).build()))
                        .build()),
                builder -> builder.placementId(20).invCode("tagid").reserve(BigDecimal.TEN));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .containsOnly(Banner.builder().w(100).h(200)
                        .format(singletonList(Format.builder().w(100).h(200).build())).build());
    }

    @Test
    public void makeHttpRequestsShouldSetBannerPosAbove() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                builder -> builder.banner(Banner.builder().build()),
                builder -> builder.placementId(20).position("above"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getImp).hasSize(1)
                .extracting(imps -> imps.iterator().next().getBanner()).containsOnly(Banner.builder().pos(1).build());
    }

    @Test
    public void makeHttpRequestsShouldSetBannerPosBelow() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                builder -> builder.banner(Banner.builder().build()),
                builder -> builder.placementId(20).position("below"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getImp).hasSize(1)
                .extracting(imps -> imps.iterator().next().getBanner()).containsOnly(Banner.builder().pos(3).build());
    }

    @Test
    public void makeHttpRequestsShouldSetPlacementIdAndTrafficSourceCodeIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                builder -> builder.banner(Banner.builder().build()),
                builder -> builder.placementId(20).trafficSourceCode("tsc").keywords(asList(
                        AppnexusKeyVal.of("key1", asList("abc", "def")),
                        AppnexusKeyVal.of("key2", asList("123", "456")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getImp)
                .extracting(imps -> imps.get(0).getExt())
                .extracting(jsonNodes -> mapper.treeToValue(jsonNodes, AppnexusImpExt.class))
                .extracting(AppnexusImpExt::getAppnexus)
                .extracting(
                        AppnexusImpExtAppnexus::getPlacementId,
                        AppnexusImpExtAppnexus::getTrafficSourceCode,
                        AppnexusImpExtAppnexus::getKeywords)
                .containsOnly(Tuple.tuple(20, "tsc", "key1=abc,key1=def,key2=123,key2=456"));
    }

    @Test
    public void makeHttpRequestShouldReturnHttpRequestWithExtUserConsentField() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .user(User.builder()
                                .ext(mapper.valueToTree(ExtUser.builder().consent("consent").build()))
                                .build()),
                builder -> builder.banner(Banner.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser)
                .extracting(User::getExt)
                .containsOnly(mapper.valueToTree(ExtUser.builder().consent("consent").build()));
    }

    @Test
    public void makeHttpRequestShouldReturnHttpRequestWithExtRegsGdprField() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .regs(Regs.of(0, mapper.valueToTree(ExtRegs.of(1)))),
                builder -> builder.banner(Banner.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getRegs)
                .containsOnly(Regs.of(0, mapper.valueToTree(ExtRegs.of(1))));
    }

    @Test
    public void makeHttpRequestsShouldHonorLegacyParams() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                builder -> builder.banner(Banner.builder().build()),
                builder -> builder
                        .placementId(null)
                        .legacyPlacementId(101)
                        .invCode(null)
                        .legacyInvCode("legacyInvCode1")
                        .trafficSourceCode(null)
                        .legacyTrafficSourceCode("legacyTrafficSourceCode1"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid, Imp::getExt)
                .containsOnly(tuple(
                        "legacyInvCode1",
                        mapper.valueToTree(AppnexusImpExt.of(
                                AppnexusImpExtAppnexus.of(101, null, "legacyTrafficSourceCode1", null, null)))));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                        && error.getMessage().startsWith("Failed to decode: Unrecognized token 'invalid'"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBidTypeFromResponseIsBanner() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.id("impId"));
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(BANNER_TYPE));

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder()
                .ext(mapper.valueToTree(AppnexusBidExt.of(
                        AppnexusBidExtAppnexus.of(BANNER_TYPE)))).impid("impId").build(), BidType.banner, null));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfBidTypeFromResponseIsVideo() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.id("impId").video(Video.builder().build()));
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(VIDEO_TYPE));

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder()
                .ext(mapper.valueToTree(AppnexusBidExt.of(
                        AppnexusBidExtAppnexus.of(VIDEO_TYPE)))).impid("impId").build(), BidType.video, null));
    }

    @Test
    public void makeBidsShouldReturnAudioBidIfBidTypeFromResponseIsAudio() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.id("impId"));
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(AUDIO_TYPE));

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder()
                .ext(mapper.valueToTree(AppnexusBidExt.of(
                        AppnexusBidExtAppnexus.of(AUDIO_TYPE)))).impid("impId").build(), BidType.audio, null));
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfBidTypeFromResponseBidExtIsNative() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.id("impId"));
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(NATIVE_TYPE));

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder()
                .ext(mapper.valueToTree(AppnexusBidExt.of(
                        AppnexusBidExtAppnexus.of(NATIVE_TYPE)))).impid("impId").build(), BidType.xNative, null));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidTypeValueFromResponseIsNotValid() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(42));
        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badServerResponse(
                        "Unrecognized bid_ad_type in response from appnexus: 42"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidExtNotDefined() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .impid("impId")
                                .ext(null)
                                .build()))
                        .build()))
                .build()));
        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badServerResponse(
                        "bidResponse.bid.ext should be defined for appnexus"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidExtAppnexusNotDefined() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .impid("impId")
                                .ext(mapper.createObjectNode())
                                .build()))
                        .build()))
                .build()));
        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badServerResponse("bidResponse.bid.ext.appnexus should be defined"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidExtAppnexusBidTypeNotDefined() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .impid("impId")
                                .ext(mapper.valueToTree(AppnexusBidExt.of(AppnexusBidExtAppnexus.of(null))))
                                .build()))
                        .build()))
                .build()));
        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badServerResponse(
                        "bidResponse.bid.ext.appnexus.bid_ad_type should be defined"));
        assertThat(result.getValue()).isEmpty();
    }

    private static BidRequest givenBidRequest(Function<BidRequestBuilder, BidRequestBuilder> bidRequestCustomizer,
                                              Function<ImpBuilder, ImpBuilder> impCustomizer,
                                              Function<ExtImpAppnexusBuilder, ExtImpAppnexusBuilder> extCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer, extCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<ImpBuilder, ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer, identity());
    }

    private static Imp givenImp(Function<ImpBuilder, ImpBuilder> impCustomizer,
                                Function<ExtImpAppnexusBuilder, ExtImpAppnexusBuilder> extCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .ext(givenExt(extCustomizer)))
                .build();
    }

    private static Imp givenImp(Function<ImpBuilder, ImpBuilder> impCustomizer) {
        return givenImp(impCustomizer, identity());
    }

    private static ObjectNode givenExt(Function<ExtImpAppnexusBuilder, ExtImpAppnexusBuilder> extCustomizer) {
        return mapper.valueToTree(ExtPrebid.of(null, extCustomizer.apply(ExtImpAppnexus.builder()).build()));
    }

    private static HttpCall<BidRequest> givenHttpCall(String body) {
        return HttpCall.success(null, HttpResponse.of(200, null, body), null);
    }

    private static String givenBidResponse(Integer bidType) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .impid("impId")
                                .ext(mapper.valueToTree(AppnexusBidExt.of(AppnexusBidExtAppnexus.of(bidType))))
                                .build()))
                        .build()))
                .build());
    }
}
