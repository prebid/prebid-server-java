package org.prebid.server.bidder.appnexus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Imp.ImpBuilder;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.assertj.core.api.Assertions;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.adapter.appnexus.model.AppnexusImpExt;
import org.prebid.server.adapter.appnexus.model.AppnexusImpExtAppnexus;
import org.prebid.server.adapter.appnexus.model.AppnexusKeyVal;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.model.openrtb.ext.ExtPrebid;
import org.prebid.server.model.openrtb.ext.request.appnexus.ExtImpAppnexus;
import org.prebid.server.model.openrtb.ext.request.appnexus.ExtImpAppnexus.ExtImpAppnexusBuilder;
import org.prebid.server.model.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;

public class AppnexusBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://appnexus.com/openrtb2d";

    private AppnexusBidder appnexusBidder;

    @Before
    public void setUp() {
        appnexusBidder = new AppnexusBidder(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldReturnNullIfBidequesImpsIsNull() {
        Assertions.assertThat(appnexusBidder.makeHttpRequests(BidRequest.builder().build()))
                .isEqualTo(Result.of(emptyList(), emptyList()));
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
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getImp)
                .containsOnly(Collections.EMPTY_LIST);
        assertThat(result.getErrors()).hasSize(1)
                .element(0).extracting(BidderError::getMessage)
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
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
        assertThat(result.getValue()).hasSize(1);
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
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
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
        final BidRequest bidRequest = BidRequest.builder().imp(asList(imp1, imp2)).build();

        // when
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
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
        assertThat(result.getValue())
                .hasSize(1)
                .element(0).returns("http://appnexus.com/openrtb2d?member_id=member_param", HttpRequest::getUri);
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
        assertThat(result.getValue())
                .hasSize(1)
                .element(0).returns("http://appnexus.com/openrtb2d", HttpRequest::getUri);
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
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
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
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .isEmpty();

        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("No placement or member+invcode provided");
    }

    @Test
    public void makeHttpRequestsShouldSetImpTagidAndImpBidFloorIfExtImpAppnexusHasInvCodeAndReserve() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                Function.identity(),
                Function.identity(),
                builder -> builder.placementId(20).invCode("tagid").reserve(BigDecimal.TEN));

        // when
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(res -> mapper.readValue(res.getBody(), BidRequest.class))
                .element(0).extracting(BidRequest::getImp).hasSize(1)
                .containsOnly(singletonList(Imp.builder()
                        .bidfloor(10f)
                        .tagid("tagid")
                        .ext(mapper.valueToTree(AppnexusImpExt.of(AppnexusImpExtAppnexus.of(20, null, null))))
                        .build()));
    }

    @Test
    public void makeHttpRequestsShouldSetBannerSizesFromExistingFirstFormatElement() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                Function.identity(),
                builder -> builder.banner(Banner.builder().format(singletonList(Format.builder().w(100).h(200).build()))
                        .build()),
                builder -> builder.placementId(20).invCode("tagid").reserve(BigDecimal.TEN));

        // when
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(imp -> imp.getBanner())
                .containsOnly(Banner.builder().w(100).h(200)
                        .format(singletonList(Format.builder().w(100).h(200).build())).build());
    }

    @Test
    public void makeHttpRequestsShouldSetBannerPosAbove() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                Function.identity(),
                builder -> builder.banner(Banner.builder().build()),
                builder -> builder.placementId(20).position("above"));

        // when
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

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
                Function.identity(),
                builder -> builder.banner(Banner.builder().build()),
                builder -> builder.placementId(20).position("below"));

        // when
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

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
                Function.identity(),
                builder -> builder.banner(Banner.builder().build()),
                builder -> builder.placementId(20).trafficSourceCode("tsc").keywords(asList(
                        AppnexusKeyVal.of("key1", asList("abc", "def")),
                        AppnexusKeyVal.of("key2", asList("123", "456")))));

        // when
        final Result<List<HttpRequest>> result = appnexusBidder.makeHttpRequests(bidRequest);

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
    public void makeBidsShouldReturnEmptyResultIfResponseStatusIs204() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall httpCall = givenHttpCall(204, null);

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseStatusIsNot200Or204() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall httpCall = givenHttpCall(302, null);

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsOnly("Unexpected status code: 302. Run with request.test = 1 for more info");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall httpCall = givenHttpCall(200, "invalid");

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).extracting(BidderError::getMessage).containsOnly(
                "Unrecognized token 'invalid': was expecting ('true', 'false' or 'null')\n" +
                        " at [Source: (String)\"invalid\"; line: 1, column: 15]");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfNoMatchingImp() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall httpCall = givenHttpCall(200, givenBidResponse("impId1"));

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder().impid("impId1").build(), BidType.banner));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfMatchingImpHasNoVideo() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.id("impId"));
        final HttpCall httpCall = givenHttpCall(200, givenBidResponse("impId"));

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder().impid("impId").build(), BidType.banner));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfMatchingImpHasVideo() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.id("impId").video(Video.builder().build()));
        final HttpCall httpCall = givenHttpCall(200, givenBidResponse("impId"));

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder().impid("impId").build(), BidType.video));
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