package org.prebid.server.bidder.appnexus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Imp.ImpBuilder;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import lombok.Value;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.Endpoint;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExt;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExtAppnexus;
import org.prebid.server.bidder.appnexus.proto.AppnexusImpExt;
import org.prebid.server.bidder.appnexus.proto.AppnexusImpExtAppnexus;
import org.prebid.server.bidder.appnexus.proto.AppnexusKeyVal;
import org.prebid.server.bidder.appnexus.proto.AppnexusReqExtAppnexus;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidPbs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.appnexus.ExtImpAppnexus;
import org.prebid.server.proto.openrtb.ext.request.appnexus.ExtImpAppnexus.ExtImpAppnexusBuilder;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
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
        appnexusBidder = new AppnexusBidder(ENDPOINT_URL, null, Map.of(10, "IAB4-5"), jacksonMapper);
    }

    @Test
    public void makeHttpRequestsShouldSetImpDisplaymanagerverFromAppExtPrebidIfAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .app(App.builder()
                                .ext(ExtApp.of(ExtAppPrebid.of("some source", "any version"), null))
                                .build()),
                impBuilder -> impBuilder.banner(Banner.builder().build()),
                extImpAppnexusBuilder -> extImpAppnexusBuilder.placementId(20));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getDisplaymanagerver)
                .containsExactly("some source-any version");
    }

    @Test
    public void makeHttpRequestsShouldNotModifyImpDisplaymanagerverIfExtAppPrebidIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .app(App.builder()
                                .ext(ExtApp.of(null, null))
                                .build()),
                impBuilder -> impBuilder.banner(Banner.builder().build()),
                extImpAppnexusBuilder -> extImpAppnexusBuilder.placementId(20));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
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
                                .ext(ExtApp.of(ExtAppPrebid.of(null, "version"), null))
                                .build()),
                impBuilder -> impBuilder.banner(Banner.builder().build()),
                extImpAppnexusBuilder -> extImpAppnexusBuilder.placementId(20));

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
                                .ext(ExtApp.of(ExtAppPrebid.of("source", null), null))
                                .build()),
                impBuilder -> impBuilder.banner(Banner.builder().build()),
                extImpAppnexusBuilder -> extImpAppnexusBuilder.placementId(20));

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
                                .ext(ExtApp.of(ExtAppPrebid.of("some source", "any version"), null))
                                .build()),
                impBuilder -> impBuilder.banner(Banner.builder().build()).displaymanagerver("string exists"),
                extImpAppnexusBuilder -> extImpAppnexusBuilder.placementId(20));

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
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Cannot deserialize value");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                });
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtImpAppnexusPlacementIdAndBothInvCodeAndMemberIdAreEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                identity(),
                extImpAppnexusBuilder -> extImpAppnexusBuilder.placementId(null).member(null).invCode(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("No placement or member+invcode provided"));
    }

    @Test
    public void makeHttpRequestsShouldSetBannerIfRequestImpIsBanner() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                impBuilder -> impBuilder.banner(Banner.builder().build()),
                extImpAppnexusBuilder -> extImpAppnexusBuilder.placementId(20).invCode("tagid"));

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
                impBuilder -> impBuilder.banner(Banner.builder()
                        .format(singletonList(Format.builder().w(100).h(200).build()))
                        .build()),
                extImpAppnexusBuilder -> extImpAppnexusBuilder
                        .placementId(20)
                        .invCode("tagid")
                        .reserve(BigDecimal.TEN));

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
                impBuilder -> impBuilder.banner(Banner.builder().build()),
                extImpAppnexusBuilder -> extImpAppnexusBuilder.placementId(20).position("above"));

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
                impBuilder -> impBuilder.banner(Banner.builder().build()),
                extImpAppnexusBuilder -> extImpAppnexusBuilder.placementId(20).position("below"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getImp).hasSize(1)
                .extracting(imps -> imps.iterator().next().getBanner()).containsOnly(Banner.builder().pos(3).build());
    }

    @Test
    public void makeHttpRequestsShouldSetAppnexusImpExtParamsIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                impBuilder -> impBuilder.banner(Banner.builder().build()),
                extImpAppnexusBuilder -> extImpAppnexusBuilder
                        .placementId(20)
                        .trafficSourceCode("tsc")
                        .keywords(List.of(AppnexusKeyVal.of("key1", List.of("val1"))))
                        .usePmtRule(true)
                        .privateSizes(mapper.createObjectNode()));

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
                        AppnexusImpExtAppnexus::getKeywords,
                        AppnexusImpExtAppnexus::getUsePmtRule,
                        AppnexusImpExtAppnexus::getPrivateSizes)
                .containsOnly(Tuple.tuple(20, "tsc", "key1=val1", true, mapper.createObjectNode()));
    }

    @Test
    public void makeHttpRequestsShouldUpdateImpExtAppnexusWithKeywords() {
        // given
        final List<AppnexusKeyVal> keywords = List.of(
                AppnexusKeyVal.of("key1", null),
                AppnexusKeyVal.of("key2", List.of("value1", "value2")),
                AppnexusKeyVal.of(null, null));

        final BidRequest bidRequest = givenBidRequest(
                identity(),
                identity(),
                extImpAppnexusBuilder -> extImpAppnexusBuilder.placementId(1).keywords(keywords));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final AppnexusImpExt expectedImpExt = AppnexusImpExt.of(
                AppnexusImpExtAppnexus.builder()
                        .placementId(1)
                        .keywords("key1,key2=value1,key2=value2")
                        .build());

        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.valueToTree(expectedImpExt));
    }

    @Test
    public void makeHttpRequestsShouldHonorLegacyParams() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                impBuilder -> impBuilder.banner(Banner.builder().build()),
                extImpAppnexusBuilder -> extImpAppnexusBuilder
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
    public void makeHttpRequestsShouldSetImpTagidAndImpBidFloorIfExtImpAppnexusHasInvCodeAndReserve() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                identity(),
                extImpAppnexusBuilder -> extImpAppnexusBuilder
                        .placementId(20)
                        .invCode("tagid")
                        .reserve(BigDecimal.TEN));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(res -> mapper.readValue(res.getBody(), BidRequest.class))
                .element(0).extracting(BidRequest::getImp)
                .isEqualTo(singletonList(Imp.builder()
                        .bidfloor(BigDecimal.valueOf(10))
                        .tagid("tagid")
                        .ext(mapper.valueToTree(
                                AppnexusImpExt.of(AppnexusImpExtAppnexus.of(20, null, null, null, null))))
                        .build()));
    }

    @Test
    public void makeHttpRequestsShouldSetReserveIfImpBidFloorIsNotSet() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                identity(),
                extImpAppnexusBuilder -> extImpAppnexusBuilder
                        .placementId(20)
                        .reserve(BigDecimal.valueOf(123)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.valueOf(123));
    }

    @Test
    public void makeHttpRequestsShouldSetReserveIfImpBidFloorIsZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                impBuilder -> impBuilder.bidfloor(BigDecimal.ZERO),
                extImpAppnexusBuilder -> extImpAppnexusBuilder
                        .placementId(20)
                        .reserve(BigDecimal.valueOf(123)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.valueOf(123));
    }

    @Test
    public void makeHttpRequestsShouldSetReserveIfImpBidFloorIsNegative() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                impBuilder -> impBuilder.bidfloor(BigDecimal.ZERO.subtract(BigDecimal.ONE)),
                extImpAppnexusBuilder -> extImpAppnexusBuilder
                        .placementId(20)
                        .reserve(BigDecimal.valueOf(123)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.valueOf(123));
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
        final BidRequest bidRequest = BidRequest.builder().imp(List.of(imp1, imp2)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("All request.imp[i].ext.appnexus.member params must match. "
                        + "Request contained: member2, member1"));
    }

    @Test
    public void makeHttpRequestsShouldUpdateRequestExtAppnexus() {
        // given
        final ExtRequestPrebid requestPrebid = ExtRequestPrebid.builder()
                .targeting(ExtRequestTargeting.builder()
                        .includebrandcategory(ExtIncludeBrandCategory.of(null, null, null, null))
                        .build())
                .build();

        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .ext(ExtRequest.of(requestPrebid)),
                impBuilder -> impBuilder.banner(Banner.builder().build()),
                extImpAppnexusBuilder -> extImpAppnexusBuilder.placementId(20));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getExt)
                .extracting(extRequest -> extRequest.getProperty("appnexus"))
                .containsExactly(mapper.valueToTree(
                        AppnexusReqExtAppnexus.builder()
                                .includeBrandCategory(true)
                                .brandCategoryUniqueness(true)
                                .isAmp(0)
                                .headerBiddingSource(5)
                                .build()));
    }

    @Test
    public void makeHttpRequestsShouldSetRequestUrlWithMemberIdParam() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                identity(),
                extImpAppnexusBuilder -> extImpAppnexusBuilder.placementId(20).invCode("tagid").member("member_param"));

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
                extImpAppnexusBuilder -> extImpAppnexusBuilder.placementId(20).invCode("tagid"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .hasSize(1)
                .element(0).returns(ENDPOINT_URL, HttpRequest::getUri);
    }

    @Test
    public void makeHttpRequestsShouldSetNativeIfRequestImpIsNative() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                impBuilder -> impBuilder.xNative(Native.builder().build()),
                extImpAppnexusBuilder -> extImpAppnexusBuilder.placementId(20).invCode("tagid"));

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
                impBuilder -> impBuilder.video(Video.builder().build()),
                extImpAppnexusBuilder -> extImpAppnexusBuilder.placementId(20).invCode("tagid"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(1)
                .extracting(Imp::getVideo).doesNotContainNull();
    }

    @Test
    public void makeHttpRequestShouldReturnSplitedHttpRequestWhenImpMoreThanMaxImpPerRequest() {
        // given
        final List<Imp> imps = IntStream.rangeClosed(0, 35)
                .mapToObj(ignore -> Imp.builder()
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAppnexus.builder().placementId(10).build())))
                        .build())
                .collect(Collectors.toList());
        final BidRequest bidRequest = BidRequest.builder().imp(imps).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(4)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getImp)
                .extracting(Collection::size)
                .containsOnly(10, 10, 10, 6);
    }

    @Test
    public void makeHttpRequestShouldReturnSingleRequestWhenOnePod() {
        // given
        final List<Imp> imps = IntStream.rangeClosed(0, 2)
                .mapToObj(impIdSuffix -> givenImp(
                        imp -> imp
                                .id(String.format("1_%d", impIdSuffix))
                                .banner(Banner.builder().build()),
                        ext -> ext.placementId(10).generateAdPodId(true)))
                .collect(Collectors.toList());
        final BidRequest bidRequest = BidRequest.builder()
                .imp(imps)
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .pbs(ExtRequestPrebidPbs.of(Endpoint.openrtb2_video.value()))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getExt).isNotNull()
                .extracting(ext -> mapper.convertValue(ext.getProperties(), AppnexusReqExt.class))
                .extracting(AppnexusReqExt::getAppnexus).doesNotContainNull()
                .extracting(AppnexusReqExtAppnexus::getAdpodId).doesNotContainNull()
                .allMatch(adPodId -> Pattern.matches("\\d+", adPodId));
    }

    @Test
    public void makeHttpRequestShouldReturnMultipleRequestsWhenOnePodAndManyImps() {
        // given
        final List<Imp> imps = IntStream.rangeClosed(0, 15)
                .mapToObj(impIdSuffix -> givenImp(
                        imp -> imp
                                .id(String.format("1_%d", impIdSuffix))
                                .banner(Banner.builder().build()),
                        ext -> ext.placementId(10).generateAdPodId(true)))
                .collect(Collectors.toList());
        final BidRequest bidRequest = BidRequest.builder()
                .imp(imps)
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .pbs(ExtRequestPrebidPbs.of(Endpoint.openrtb2_video.value()))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(2)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getExt).isNotNull()
                .extracting(ext -> mapper.convertValue(ext.getProperties(), AppnexusReqExt.class))
                .extracting(AppnexusReqExt::getAppnexus).doesNotContainNull()
                .extracting(AppnexusReqExtAppnexus::getAdpodId).doesNotContainNull()
                .matches(adPodIds -> new HashSet<>(adPodIds).size() == 1); // adPodIds should be the same
    }

    @Test
    public void makeHttpRequestShouldReturnMultipleRequestsWhenTwoPods() {
        // given
        final List<Imp> imps = IntStream.rangeClosed(1, 2)
                .boxed()
                .flatMap(impIdPrefix -> IntStream.rangeClosed(0, 2)
                        .mapToObj(impIdSuffix -> givenImp(
                                imp -> imp
                                        .id(String.format("%d_%d", impIdPrefix, impIdSuffix))
                                        .banner(Banner.builder().build()),
                                ext -> ext.placementId(10).generateAdPodId(true))))
                .collect(Collectors.toList());
        final BidRequest bidRequest = BidRequest.builder()
                .imp(imps)
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .pbs(ExtRequestPrebidPbs.of(Endpoint.openrtb2_video.value()))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(2)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getExt).isNotNull()
                .extracting(ext -> mapper.convertValue(ext.getProperties(), AppnexusReqExt.class))
                .extracting(AppnexusReqExt::getAppnexus).doesNotContainNull()
                .extracting(AppnexusReqExtAppnexus::getAdpodId).doesNotContainNull()
                .matches(adPodIds -> new HashSet<>(adPodIds).size() == 2); // adPodIds should be different
    }

    @Test
    public void makeHttpRequestShouldReturnMultipleRequestsWhenTwoPodsAndManyImps() {
        // given
        final List<Imp> imps = IntStream.rangeClosed(1, 2)
                .boxed()
                .flatMap(impIdPrefix -> IntStream.rangeClosed(0, 15)
                        .mapToObj(impIdSuffix -> givenImp(
                                imp -> imp
                                        .id(String.format("%d_%d", impIdPrefix, impIdSuffix))
                                        .banner(Banner.builder().build()),
                                ext -> ext.placementId(10).generateAdPodId(true))))
                .collect(Collectors.toList());
        final BidRequest bidRequest = BidRequest.builder()
                .imp(imps)
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .pbs(ExtRequestPrebidPbs.of(Endpoint.openrtb2_video.value()))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(4)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getExt).isNotNull()
                .extracting(ext -> mapper.convertValue(ext.getProperties(), AppnexusReqExt.class))
                .extracting(AppnexusReqExt::getAppnexus).doesNotContainNull()
                .extracting(AppnexusReqExtAppnexus::getAdpodId).doesNotContainNull()
                .matches(adPodIds -> new HashSet<>(adPodIds).size() == 2); // adPodIds should be different
    }

    @Test
    public void makeHttpRequestShouldReturnErrorIfRequestContainsMultipleGenerateAdPodIdsValues() {
        // given
        final List<Imp> imps = IntStream.rangeClosed(1, 2)
                .boxed()
                .flatMap(impIdPrefix -> IntStream.rangeClosed(0, 15)
                        .mapToObj(impIdSuffix -> givenImp(
                                imp -> imp
                                        .id(String.format("%d_%d", impIdPrefix, impIdSuffix))
                                        .banner(Banner.builder().build()),
                                ext -> ext.placementId(10).generateAdPodId(impIdSuffix % 2 == 0))))
                .collect(Collectors.toList());
        final BidRequest bidRequest = BidRequest.builder()
                .imp(imps)
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .pbs(ExtRequestPrebidPbs.of(Endpoint.openrtb2_video.value()))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Generate ad pod option should be same for all pods in request"));
    }

    @Test
    public void makeHttpRequestShouldNotGenerateAdPodIdWhenFlagIsNotSetInRequestImpExt() {
        // given
        final List<Imp> imps = IntStream.rangeClosed(1, 2)
                .boxed()
                .flatMap(impIdPrefix -> IntStream.rangeClosed(0, 15)
                        .mapToObj(impIdSuffix -> givenImp(
                                imp -> imp
                                        .id(String.format("%d_%d", impIdPrefix, impIdSuffix))
                                        .banner(Banner.builder().build()),
                                ext -> ext.placementId(10).generateAdPodId(false))))
                .collect(Collectors.toList());
        final BidRequest bidRequest = BidRequest.builder()
                .imp(imps)
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .includebrandcategory(ExtIncludeBrandCategory.of(null, null, null, null))
                                .build())
                        .pbs(ExtRequestPrebidPbs.of(Endpoint.openrtb2_video.value()))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = appnexusBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.convertValue(ext.getProperties(), AppnexusReqExt.class))
                .extracting(AppnexusReqExt::getAppnexus).doesNotContainNull()
                .extracting(AppnexusReqExtAppnexus::getAdpodId)
                .matches(adPodIds -> adPodIds.stream().allMatch(Objects::isNull));
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
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.id("impId"));
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(BANNER_TYPE));

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final AppnexusBidExtAppnexus expectedExtAppnexus =
                AppnexusBidExtAppnexus.builder().bidAdType(BANNER_TYPE).build();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder()
                .ext(mapper.valueToTree(AppnexusBidExt.of(
                        expectedExtAppnexus))).impid("impId").build(), BidType.banner, null));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfBidTypeFromResponseIsVideo() throws JsonProcessingException {
        // given
        final BidRequest bidRequest =
                givenBidRequest(impBuilder -> impBuilder.id("impId").video(Video.builder().build()));
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(VIDEO_TYPE));

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final AppnexusBidExtAppnexus expectedExtAppnexus =
                AppnexusBidExtAppnexus.builder().bidAdType(VIDEO_TYPE).build();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder()
                .ext(mapper.valueToTree(AppnexusBidExt.of(
                        expectedExtAppnexus))).impid("impId").build(), BidType.video, null));
    }

    @Test
    public void makeBidsShouldReturnAudioBidIfBidTypeFromResponseIsAudio() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.id("impId"));
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(AUDIO_TYPE));

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final AppnexusBidExtAppnexus expectedExtAppnexus =
                AppnexusBidExtAppnexus.builder().bidAdType(AUDIO_TYPE).build();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder()
                .ext(mapper.valueToTree(AppnexusBidExt.of(
                        expectedExtAppnexus))).impid("impId").build(), BidType.audio, null));
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfBidTypeFromResponseBidExtIsNative() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.id("impId"));
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(NATIVE_TYPE));

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final AppnexusBidExtAppnexus expectedExtAppnexus =
                AppnexusBidExtAppnexus.builder().bidAdType(NATIVE_TYPE).build();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder()
                .ext(mapper.valueToTree(AppnexusBidExt.of(
                        expectedExtAppnexus))).impid("impId").build(), BidType.xNative, null));
    }

    @Test
    public void makeBidsShouldSetBidCatWhenBrandCategoryIdIsMatch() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.id("impId"));
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bidExtCustomizer -> bidExtCustomizer.brandCategoryId(10).bidAdType(1)));

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .flatExtracting(Bid::getCat)
                .containsOnly("IAB4-5");
    }

    @Test
    public void makeBidsShouldClearBidCatWhenBrandCategoryIdIsNotMatchAndBidCatIsNotEmpty()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.id("impId"));
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(
                bidBuilder -> bidBuilder.cat(singletonList("CLEAR")),
                bidExtCustomizer -> bidExtCustomizer.brandCategoryId(350).bidAdType(1)));

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .flatExtracting(Bid::getCat)
                .isEmpty();
    }

    @Test
    public void makeBidsShouldSetBidCurrencyFromResponseBid() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.id("impId"));

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(builder -> builder.cur("JPY"), identity(), identity()));

        // when
        final Result<List<BidderBid>> result = appnexusBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBidCurrency)
                .containsOnly("JPY");
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
                                .ext(mapper.valueToTree(AppnexusBidExt.of(
                                        AppnexusBidExtAppnexus.builder().bidAdType(null).build())))
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

    private static BidRequest givenBidRequest(UnaryOperator<BidRequestBuilder> bidRequestCustomizer,
                                              UnaryOperator<ImpBuilder> impCustomizer,
                                              UnaryOperator<ExtImpAppnexusBuilder> extCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer, extCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer, identity());
    }

    private static Imp givenImp(UnaryOperator<ImpBuilder> impCustomizer,
                                UnaryOperator<ExtImpAppnexusBuilder> extCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .ext(givenExt(extCustomizer)))
                .build();
    }

    private static Imp givenImp(UnaryOperator<ImpBuilder> impCustomizer) {
        return givenImp(impCustomizer, identity());
    }

    private static ObjectNode givenExt(UnaryOperator<ExtImpAppnexusBuilder> extCustomizer) {
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
                                .ext(mapper.valueToTree(AppnexusBidExt.of(
                                        AppnexusBidExtAppnexus.builder().bidAdType(bidType).build())))
                                .build()))
                        .build()))
                .build());
    }

    private static String givenBidResponse(
            UnaryOperator<BidResponse.BidResponseBuilder> bidResponseCustomizer,
            UnaryOperator<Bid.BidBuilder> bidCustomizer,
            UnaryOperator<AppnexusBidExtAppnexus.AppnexusBidExtAppnexusBuilder> bidExtCustomizer)
            throws JsonProcessingException {

        return mapper.writeValueAsString(bidResponseCustomizer.apply(
                        BidResponse.builder()
                                .seatbid(singletonList(SeatBid.builder()
                                        .bid(singletonList(bidCustomizer.apply(Bid.builder()
                                                .impid("impId")
                                                .ext(mapper.valueToTree(AppnexusBidExt.of(bidExtCustomizer.apply(
                                                                AppnexusBidExtAppnexus.builder()
                                                                        .bidAdType(BANNER_TYPE))
                                                        .build())))).build()))
                                        .build())))
                .build());
    }

    private static String givenBidResponse(
            UnaryOperator<Bid.BidBuilder> bidCustomizer,
            UnaryOperator<AppnexusBidExtAppnexus.AppnexusBidExtAppnexusBuilder> bidExtCustomizer)
            throws JsonProcessingException {

        return givenBidResponse(identity(), bidCustomizer, bidExtCustomizer);
    }

    private static String givenBidResponse(
            UnaryOperator<AppnexusBidExtAppnexus.AppnexusBidExtAppnexusBuilder> bidExtCustomizer)
            throws JsonProcessingException {

        return givenBidResponse(identity(), bidExtCustomizer);
    }

    @Value(staticConstructor = "of")
    private static class AppnexusReqExt {

        AppnexusReqExtAppnexus appnexus;
    }
}
