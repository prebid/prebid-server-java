package org.rtb.vexing.bidder.appnexus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Test;
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.adapter.appnexus.model.AppnexusImpExt;
import org.rtb.vexing.adapter.appnexus.model.AppnexusImpExtAppnexus;
import org.rtb.vexing.adapter.appnexus.model.AppnexusKeyVal;
import org.rtb.vexing.bidder.model.BidderBid;
import org.rtb.vexing.bidder.model.HttpCall;
import org.rtb.vexing.bidder.model.HttpRequest;
import org.rtb.vexing.bidder.model.HttpResponse;
import org.rtb.vexing.bidder.model.Result;
import org.rtb.vexing.model.openrtb.ext.ExtPrebid;
import org.rtb.vexing.model.openrtb.ext.request.appnexus.ExtImpAppnexus;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.rtb.vexing.model.openrtb.ext.response.BidType.banner;
import static org.rtb.vexing.model.openrtb.ext.response.BidType.video;

public class AppnexusBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://appnexus.com/openrtb2d";

    private AppnexusBidder appnexusBidder;

    @Before
    public void setUp() {
        appnexusBidder = new AppnexusBidder(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldReturnNullIfBidequesImpsIsNull() {
        assertThat(appnexusBidder.makeHttpRequests(BidRequest.builder().build())).isEqualTo(Result.emptyHttpRequests());
    }

    @Test
    public void makeHttpRequestsShouldSkipImpAndAddErrorIfRequestContainsNotSupportedMediaType() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(Collections.singletonList(Imp.builder().id("23").audio(Audio.builder().build())
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.value).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.body, BidRequest.class))
                .extracting(BidRequest::getImp)
                .containsOnly(Collections.EMPTY_LIST);
        assertThat(result.errors).hasSize(1)
                .containsExactly("Appnexus doesn't support audio or native Imps. Ignoring Imp ID=23");
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
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.errors).hasSize(1);
        assertThat(result.errors.get(0)).startsWith("Cannot deserialize instance");
        assertThat(result.value).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtImpAppnexusDoesNotContainPlacementIdAndMemberId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                Function.identity(),
                builder -> builder.video(Video.builder().build()),
                builder -> builder.placementId(null).member(null).invCode("invCode"));
        // when
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.value).hasSize(1);
        assertThat(result.errors).hasSize(1)
                .containsExactly("No placement or member+invcode provided");
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
        final BidRequest bidRequest = BidRequest.builder().imp(Arrays.asList(imp1, imp2)).build();

        // when
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.value).hasSize(1);
        assertThat(result.errors).hasSize(1)
                .containsExactly("All request.imp[i].ext.appnexus.member params must match. "
                        + "Request contained: member2, member1");
    }

    @Test
    public void makeHttpRequestsShouldSetRequestUrlWithMemberIdParam() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                Function.identity(),
                Function.identity(),
                builder -> builder.placementId(20).invCode("tagid").member("member_param"));

        // when
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.value)
                .hasSize(1)
                .element(0).returns("http://appnexus.com/openrtb2d?member_id=member_param", httpRequest -> httpRequest.uri);
    }

    @Test
    public void makeHttpRequestsShouldSetRequestUrlWithoutMemberIdIfItMissedRequestBodyImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                Function.identity(),
                Function.identity(),
                builder -> builder.placementId(20).invCode("tagid"));

        // when
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.value)
                .hasSize(1)
                .element(0).returns("http://appnexus.com/openrtb2d", httpRequest -> httpRequest.uri);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtImpAppnexusDoesNotContainPlacementIdAndInvCode() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                Function.identity(),
                builder -> builder.video(Video.builder().build()),
                builder -> builder.placementId(null).member("member").invCode(null));
        // when
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.value).hasSize(1);
        assertThat(result.errors).hasSize(1)
                .containsExactly("No placement or member+invcode provided");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtImpAppnexusPlacementIdAndBothInvCodeAndMemberIdAreEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                Function.identity(),
                Function.identity(),
                builder -> builder.placementId(null).member(null).invCode(null));
        // when
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

        //then
        assertThat(result.value).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.body, BidRequest.class))
                .extracting(BidRequest::getImp)
                .containsOnly(Collections.EMPTY_LIST);

        assertThat(result.errors).hasSize(1)
                .containsExactly("No placement or member+invcode provided");
    }

    @Test
    public void makeHttpRequestsShouldSetImpTagidAndImpBidFloorIfExtImpAppnexusHasInvCodeAndReserve() {
        // given
        final Imp expectedImp = givenExpectedImp(
                builder ->
                        builder.bidfloor(10f).tagid("tagid"),
                appnexusImpExtAppnexusBuilder ->
                        AppnexusImpExtAppnexus.builder().placementId(20));
        final BidRequest bidRequest = givenBidRequest(
                Function.identity(),
                Function.identity(),
                builder -> builder.placementId(20).invCode("tagid").reserve(BigDecimal.TEN));

        // when
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.value).hasSize(1)
                .extracting(res -> mapper.readValue(res.body, BidRequest.class))
                .element(0).extracting(BidRequest::getImp).hasSize(1)
                .containsOnly(singletonList(expectedImp));
    }

    @Test
    public void makeHttpRequestsShouldSetBannerSizesFromExistingFirstFormatElement() {
        // given
        final Format format = Format.builder().w(100).h(200).build();
        final Banner expectedBanner = Banner.builder().w(100).h(200).format(singletonList(format)).build();

        final BidRequest bidRequest = givenBidRequest(
                Function.identity(),
                builder -> builder.banner(Banner.builder().format(singletonList(format))
                        .build()),
                builder -> builder.placementId(20).invCode("tagid").reserve(BigDecimal.TEN));

        // when
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.value).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.body, BidRequest.class))
                .extracting(BidRequest::getImp).hasSize(1)
                .extracting(imps -> imps.iterator().next().getBanner()).containsOnly(expectedBanner);
    }

    @Test
    public void makeHttpRequestsShouldSetBannerPosAbove() {
        // given;
        final Banner expectedBanner = Banner.builder().pos(1).build();

        final BidRequest bidRequest = givenBidRequest(
                Function.identity(),
                builder -> builder.banner(Banner.builder().build()),
                builder -> builder.placementId(20).position("above"));

        // when
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.value).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.body, BidRequest.class))
                .extracting(BidRequest::getImp).hasSize(1)
                .extracting(imps -> imps.iterator().next().getBanner()).containsOnly(expectedBanner);
    }

    @Test
    public void makeHttpRequestsShouldSetBannerPosBelow() {
        // given;
        final Banner expectedBanner = Banner.builder().pos(3).build();

        final BidRequest bidRequest = givenBidRequest(
                Function.identity(),
                builder -> builder.banner(Banner.builder().build()),
                builder -> builder.placementId(20).position("below"));

        // when
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.value).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.body, BidRequest.class))
                .extracting(BidRequest::getImp).hasSize(1)
                .extracting(imps -> imps.iterator().next().getBanner()).containsOnly(expectedBanner);
    }

    @Test
    public void makeHttpRequestsShouldSetPlacementIdAndTrafficSourceCodeIfPresent() throws JsonProcessingException {
        // given;
        final BidRequest bidRequest = givenBidRequest(
                Function.identity(),
                builder -> builder.banner(Banner.builder().build()),
                builder -> builder.placementId(20).trafficSourceCode("tsc")
                            .keywords(
                                    Arrays.asList(
                                            AppnexusKeyVal.builder().key("key1")
                                                    .values(Arrays.asList("abc", "def")).build(),
                                            AppnexusKeyVal.builder().key("key2")
                                                    .values(Arrays.asList("123", "456")).build())));

        // when
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.value).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.body, BidRequest.class))
                .extracting(BidRequest::getImp)
                .extracting(imps -> imps.get(0).getExt())
                    .extracting(jsonNodes -> mapper.treeToValue(jsonNodes, AppnexusImpExt.class))
                    .extracting(
                            appnexusImpExtAppnexus -> appnexusImpExtAppnexus.appnexus.placementId,
                            appnexusImpExtAppnexus -> appnexusImpExtAppnexus.appnexus.trafficSourceCode,
                            appnexusImpExtAppnexus -> appnexusImpExtAppnexus.appnexus.keywords)
                .containsOnly(Tuple.tuple(20, "tsc", "key1=abc,key1=def,key2=123,key2=456"));
    }

    @Test
    public void makeBidsShouldReturnEmptyResultIfResponseStatusIs204() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall httpCall = givenHttpCall(204, null);

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.errors).isEmpty();
        assertThat(result.value).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseStatusIsNot200Or204() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall httpCall = givenHttpCall(302, null);

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.errors).containsOnly("Unexpected status code: 302. Run with request.test = 1 for more info");
        assertThat(result.value).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall httpCall = givenHttpCall(200, "invalid");

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.errors).hasSize(1);
        assertThat(result.errors.get(0)).startsWith("Unrecognized token");
        assertThat(result.value).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfNoMatchingImp() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall httpCall = givenHttpCall(200, givenBidResponse("impId1"));

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.errors).isEmpty();
        assertThat(result.value).containsOnly(BidderBid.of(Bid.builder().impid("impId1").build(), banner));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfMatchingImpHasNoVideo() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.id("impId"));
        final HttpCall httpCall = givenHttpCall(200, givenBidResponse("impId"));

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.errors).isEmpty();
        assertThat(result.value).containsOnly(BidderBid.of(Bid.builder().impid("impId").build(), banner));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfMatchingImpHasVideo() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.id("impId").video(Video.builder().build()));
        final HttpCall httpCall = givenHttpCall(200, givenBidResponse("impId"));

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.errors).isEmpty();
        assertThat(result.value).containsOnly(BidderBid.of(Bid.builder().impid("impId").build(), video));
    }

    private static BidRequest givenBidRequest(Function<BidRequest.BidRequestBuilder,
                                                BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
                                              Function<ExtImpAppnexus.ExtImpAppnexusBuilder,
                                                      ExtImpAppnexus.ExtImpAppnexusBuilder> extCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer, extCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer, identity());
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
                                Function<ExtImpAppnexus.ExtImpAppnexusBuilder,
                                        ExtImpAppnexus.ExtImpAppnexusBuilder> extCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .ext(givenExt(extCustomizer)))
                .build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenImp(impCustomizer, identity());
    }

    private static ObjectNode givenExt(Function<ExtImpAppnexus.ExtImpAppnexusBuilder,
            ExtImpAppnexus.ExtImpAppnexusBuilder> extCustomizer) {
        return mapper.valueToTree(ExtPrebid.of(null, extCustomizer.apply(ExtImpAppnexus.builder()).build()));
    }

    private static Imp givenExpectedImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
                                        Function<AppnexusImpExtAppnexus.AppnexusImpExtAppnexusBuilder,
                                                AppnexusImpExtAppnexus.AppnexusImpExtAppnexusBuilder> extCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .ext(mapper.valueToTree(
                        AppnexusImpExt.builder()
                                .appnexus(extCustomizer.apply(
                                    AppnexusImpExtAppnexus.builder()).build())
                                .build())))
                .build();
    }

    private static HttpCall givenHttpCall(int statusCode, String body) {
        return HttpCall.full(null, HttpResponse.of(statusCode, null, body), null);
    }

    private static String givenBidResponse(String impId) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .impid(impId)
                                .build()))
                        .build()))
                .build());
    }
}