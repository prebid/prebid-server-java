package org.prebid.server.bidder.appnexus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExt;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExtAppnexus;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExtCreative;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExtVideo;
import org.prebid.server.bidder.appnexus.proto.AppnexusImpExtAppnexus;
import org.prebid.server.bidder.appnexus.proto.AppnexusKeyVal;
import org.prebid.server.bidder.appnexus.proto.AppnexusReqExtAppnexus;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidServer;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.appnexus.ExtImpAppnexus;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.auction.model.Endpoint.openrtb2_amp;
import static org.prebid.server.auction.model.Endpoint.openrtb2_video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class AppnexusBidderTest extends VertxTest {

    private final AppnexusBidder target = new AppnexusBidder(
            "https://endpoint.com/",
            null,
            Map.of(10, "IAB4-5"),
            jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> new AppnexusBidder(
                "invalid_url",
                null,
                Collections.emptyMap(),
                jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldAddErrorsOnInvalidImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))),
                givenImp(givenExt(ext -> ext.placementId(null))),
                givenImp(givenExt(ext -> ext.placementId(0))),
                givenImp(givenExt(ext -> ext.placementId(0).invCode("validInvCode"))),
                givenImp(givenExt(ext -> ext.placementId(0).member("validMember"))),
                // valid imps
                givenImp(givenExt(ext -> ext.placementId(0).invCode("validInvCode").member("validMember"))),
                givenImp(givenExt(ext -> ext.placementId(1))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(2);
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .hasSize(5)
                .satisfies(errors -> {
                    assertThat(errors.get(0)).startsWith("Cannot deserialize value of type");
                    assertThat(errors.get(1)).isEqualTo("No placement or member+invcode provided");
                    assertThat(errors.get(1)).isEqualTo("No placement or member+invcode provided");
                    assertThat(errors.get(1)).isEqualTo("No placement or member+invcode provided");
                    assertThat(errors.get(1)).isEqualTo("No placement or member+invcode provided");
                });
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorOnInvalidMembers() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(givenExt(ext -> ext.member("validMember"))),
                givenImp(givenExt(ext -> ext.member("invalidMember"))),
                givenImp(givenExt(ext -> ext.member("validMember"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("all request.imp[i].ext.prebid.bidder.appnexus.member params must match."
                        + " Request contained member IDs validMember and invalidMember");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorOnInvalidGenerateAdPodIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(givenExt(identity())),
                givenImp(givenExt(ext -> ext.generateAdPodId(false))),
                givenImp(givenExt(ext -> ext.generateAdPodId(true))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("generate ad pod option should be same for all pods in request");
    }

    @Test
    public void makeHttpRequestsShouldUpdateImpTagIdOnValidInvCode() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(givenExt(ext -> ext.invCode("invCode"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("invCode");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUpdateImpBidFloorOnValidReserve() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(givenExt(ext -> ext.reserve(BigDecimal.ONE))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.ONE);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldNotUpdateImpBidFloorOnValidImpBidFloor() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(
                        imp -> imp.bidfloor(BigDecimal.ONE),
                        givenExt(ext -> ext.reserve(BigDecimal.TEN))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.ONE);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUpdateBanner() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(
                        imp -> imp.banner(Banner.builder()
                                .format(singletonList(Format.builder().w(1).h(1).build()))
                                .build()),
                        givenExt(identity())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .containsExactly(Banner.builder()
                        .w(1)
                        .h(1)
                        .format(singletonList(Format.builder().w(1).h(1).build()))
                        .build());
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUpdateBannerWithAbovePosition() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(
                        imp -> imp.banner(Banner.builder().build()),
                        givenExt(ext -> ext.position("above"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getPos)
                .containsExactly(1);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUpdateBannerWithBelowPosition() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(
                        imp -> imp.banner(Banner.builder().build()),
                        givenExt(ext -> ext.position("below"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getPos)
                .containsExactly(3);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUpdateDisplayManagerVer() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.app(App.builder()
                        .ext(ExtApp.of(ExtAppPrebid.of("source", "version"), null))
                        .build()),
                givenImp(givenExt(identity())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getDisplaymanagerver)
                .containsExactly("source-version");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldNotUpdateDisplayManagerVer() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.app(App.builder()
                        .ext(ExtApp.of(ExtAppPrebid.of("source", "version"), null))
                        .build()),
                givenImp(imp -> imp.displaymanagerver("original"), givenExt(identity())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getDisplaymanagerver)
                .containsExactly("original");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUpdateImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(givenExt(ext -> ext
                        .placementId(1)
                        .trafficSourceCode("2")
                        .usePaymentRule(true)
                        .privateSizes(TextNode.valueOf("4"))
                        .extInvCode("5")
                        .externalImpId("6"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("appnexus"))
                .map(appnexus -> mapper.convertValue(appnexus, AppnexusImpExtAppnexus.class))
                .containsExactly(AppnexusImpExtAppnexus.builder()
                        .placementId(1)
                        .trafficSourceCode("2")
                        .usePmtRule(true)
                        .privateSizes(TextNode.valueOf("4"))
                        .extInvCode("5")
                        .externalImpId("6")
                        .build());
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldForwardImpExtGpid() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(givenExt(identity()), "gpidValue"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("gpid").asText())
                .containsExactly("gpidValue");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUpdateImpExtWithStringKeywords() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(givenExt(ext -> ext.keywords(TextNode.valueOf("string")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("appnexus"))
                .map(appnexus -> mapper.convertValue(appnexus, AppnexusImpExtAppnexus.class))
                .extracting(AppnexusImpExtAppnexus::getKeywords)
                .containsExactly("string");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUpdateImpExtWithObjectKeywords() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(givenExt(ext -> ext.keywords(mapper.createObjectNode()
                        .putPOJO("field1", asList("value1", "value2"))
                        .putPOJO("field2", emptyList())
                        .putPOJO("field3", singletonList(null))))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("appnexus"))
                .map(appnexus -> mapper.convertValue(appnexus, AppnexusImpExtAppnexus.class))
                .extracting(AppnexusImpExtAppnexus::getKeywords)
                .containsExactly("field1=value1,field1=value2,field2,field3=");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUpdateImpExtWithArrayKeywords() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(givenExt(ext -> ext.keywords(mapper.valueToTree(asList(
                        AppnexusKeyVal.of("key1", asList("value1", "value2")),
                        AppnexusKeyVal.of("key2", emptyList()),
                        AppnexusKeyVal.of("key3", null),
                        AppnexusKeyVal.of("key4", singletonList(null))))))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("appnexus"))
                .map(appnexus -> mapper.convertValue(appnexus, AppnexusImpExtAppnexus.class))
                .extracting(AppnexusImpExtAppnexus::getKeywords)
                .containsExactly("key1=value1,key1=value2,key2,key3,key4=");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(givenExt(identity())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://endpoint.com/");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedUrlWithMemberId() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(givenExt(ext -> ext.member("me mber"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://endpoint.com/?member_id=me%20mber");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldMoveSupplyChain() {
        // given
        final ExtSource extSource = ExtSource.of(SupplyChain.of(1, null, null, null));
        extSource.addProperty("field", TextNode.valueOf("value"));
        final BidRequest bidRequest = givenBidRequest(
                request -> request.source(Source.builder().ext(extSource).build()),
                givenImp(givenExt(identity())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .allSatisfy(request -> {
                    final ExtSource expectedExtSource = ExtSource.of(null);
                    expectedExtSource.addProperty("field", TextNode.valueOf("value"));
                    assertThat(request.getSource())
                            .extracting(Source::getExt)
                            .isEqualTo(expectedExtSource);

                    assertThat(request.getExt())
                            .extracting(ext -> ext.getProperty("schain"))
                            .isEqualTo(mapper.valueToTree(Map.of("complete", 1)));
                });
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUpdateExtAppnexus() {
        // given
        final AppnexusBidder target = new AppnexusBidder(
                "https://endpoint.com/",
                1,
                Map.of(10, "IAB4-5"),
                jacksonMapper);

        final BidRequest bidRequest = givenBidRequest(givenImp(givenExt(identity())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("appnexus"))
                .map(appnexus -> mapper.convertValue(appnexus, AppnexusReqExtAppnexus.class))
                .containsExactly(AppnexusReqExtAppnexus.builder()
                        .isAmp(0)
                        .headerBiddingSource(1)
                        .build());
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUpdateExtAppnexusIfBrandCategoryPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .includebrandcategory(ExtIncludeBrandCategory.of(null, null, null, null))
                                .build())
                        .build())),
                givenImp(givenExt(identity())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("appnexus"))
                .map(appnexus -> mapper.convertValue(appnexus, AppnexusReqExtAppnexus.class))
                .containsExactly(AppnexusReqExtAppnexus.builder()
                        .brandCategoryUniqueness(true)
                        .includeBrandCategory(true)
                        .isAmp(0)
                        .headerBiddingSource(5)
                        .build());
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUpdateExtAppnexusIfAmpRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .server(ExtRequestPrebidServer.of(null, null, null, openrtb2_amp.value()))
                        .build())),
                givenImp(givenExt(identity())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("appnexus"))
                .map(appnexus -> mapper.convertValue(appnexus, AppnexusReqExtAppnexus.class))
                .containsExactly(AppnexusReqExtAppnexus.builder()
                        .isAmp(1)
                        .headerBiddingSource(5)
                        .build());
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUpdateExtAppnexusIfVideoRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .server(ExtRequestPrebidServer.of(null, null, null, openrtb2_video.value()))
                        .build())),
                givenImp(givenExt(identity())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("appnexus"))
                .map(appnexus -> mapper.convertValue(appnexus, AppnexusReqExtAppnexus.class))
                .containsExactly(AppnexusReqExtAppnexus.builder()
                        .isAmp(0)
                        .headerBiddingSource(6)
                        .build());
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSplitImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                IntStream.range(0, 42)
                        .mapToObj(i -> givenImp(givenExt(identity())))
                        .toArray(Imp[]::new));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .hasSize(5)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getImp)
                .extracting(Collection::size)
                .containsExactly(10, 10, 10, 10, 2);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSplitImpsByPods() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .server(ExtRequestPrebidServer.of(null, null, null, openrtb2_video.value()))
                        .build())),
                IntStream.range(0, 42)
                        .mapToObj(i -> givenImp(
                                imp -> imp.id(i % 2 + "_random"),
                                givenExt(ext -> ext.generateAdPodId(true))))
                        .toArray(Imp[]::new));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .hasSize(6)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getImp)
                .extracting(Collection::size)
                .containsExactly(10, 10, 1, 10, 10, 1);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldGenerateProperAdPodIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .server(ExtRequestPrebidServer.of(null, null, null, openrtb2_video.value()))
                        .build())),
                givenImp(imp -> imp.id("1_random"), givenExt(ext -> ext.generateAdPodId(true))),
                givenImp(imp -> imp.id("1_random"), givenExt(ext -> ext.generateAdPodId(true))),
                givenImp(imp -> imp.id("2_random"), givenExt(ext -> ext.generateAdPodId(true))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .satisfies(exts -> assertThat(exts.getFirst()).isNotSameAs(exts.get(1)))
                .extracting(ext -> ext.getProperty("appnexus"))
                .extracting(appnexus -> appnexus.get("adpod_id"))
                .hasSize(2)
                .satisfies(ids -> assertThat(ids.getFirst()).isNotSameAs(ids.get(1)));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorOnInvalidBody() {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to decode");
        });
    }

    @Test
    public void makeBidsShouldReturnEmptyResponseOnNullBody() {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall("null");

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResponseOnEmptyBody() {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall("{}");

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldCollectErrors() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall(givenBidResponse(
                givenBid(identity()),
                givenBid(bid -> bid.ext(mapper.createObjectNode().putPOJO("appnexus", emptyList()))),
                givenBid(null, givenBidExt(ext -> ext.bidAdType(2))),
                // valid bid
                givenBid(null, givenBidExt(identity()))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getValue())
                .flatExtracting(BidderBid::getBid)
                .hasSize(1);
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .satisfies(errors -> {
                    assertThat(errors.get(0)).isEqualTo("bidResponse.bid.ext should be defined for appnexus");
                    assertThat(errors.get(1)).startsWith("Cannot deserialize value of type");
                    assertThat(errors.get(2)).isEqualTo("Unrecognized bid_ad_type in response from appnexus: 2");
                });
    }

    @Test
    public void makeBidsShouldReturnExpectedBidTypes() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall(givenBidResponse(
                givenBid(null, givenBidExt(identity())),
                givenBid(null, givenBidExt(ext -> ext.bidAdType(0))),
                givenBid(null, givenBidExt(ext -> ext.bidAdType(1))),
                givenBid(null, givenBidExt(ext -> ext.bidAdType(3)))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(banner, banner, video, xNative);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnExpectedCur() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall(givenBidResponse(
                givenBid(null, givenBidExt(identity()))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getBidCurrency)
                .containsExactly("CUR");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnExpectedDealPriority() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall(givenBidResponse(
                givenBid(null, givenBidExt(ext -> ext.dealPriority(2)))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getDealPriority)
                .containsExactly(2);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnDefaultDealPriority() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall(givenBidResponse(
                givenBid(null, givenBidExt(identity()))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getDealPriority)
                .containsExactly(0);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnIabCategory() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall(givenBidResponse(
                givenBid(null, givenBidExt(ext -> ext.brandCategoryId(10)))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .flatExtracting(Bid::getCat)
                .containsExactly("IAB4-5");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyCatOnNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall(givenBidResponse(
                givenBid(null, givenBidExt(identity()))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getCat)
                .containsExactly(Collections.emptyList());
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyCatIfSizeMoreThen1() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall(givenBidResponse(
                givenBid(asList("1", "2"), givenBidExt(identity()))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getCat)
                .containsExactly(Collections.emptyList());
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldAddVideoInfo() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> bidderCall = givenBidderCall(givenBidResponse(
                givenBid(null, givenBidExt(ext -> ext
                        .creativeInfo(AppnexusBidExtCreative.of(AppnexusBidExtVideo.of(10)))))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(bidderCall, null);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getVideoInfo)
                .extracting(ExtBidPrebidVideo::getDuration)
                .containsExactly(10);
        assertThat(result.getErrors()).isEmpty();
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Imp... imps) {

        return bidRequestCustomizer.apply(BidRequest.builder().imp(asList(imps))).build();
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(identity(), imps);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private static Imp givenImp(ExtImpAppnexus extImpAppnexus) {
        return givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, extImpAppnexus))));
    }

    private static Imp givenImp(ExtImpAppnexus extImpAppnexus, String gpid) {
        final ObjectNode ext = mapper.createObjectNode()
                .put("gpid", gpid)
                .set("bidder", mapper.valueToTree(extImpAppnexus));

        return givenImp(imp -> imp.ext(ext));
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer, ExtImpAppnexus extImpAppnexus) {
        return impCustomizer.apply(Imp.builder().ext(mapper.valueToTree(ExtPrebid.of(null, extImpAppnexus)))).build();
    }

    private static ExtImpAppnexus givenExt(UnaryOperator<ExtImpAppnexus.ExtImpAppnexusBuilder> extCustomizer) {
        return extCustomizer.apply(ExtImpAppnexus.builder().placementId(1)).build();
    }

    private static BidderCall<BidRequest> givenBidderCall(String bidResponse) {
        return BidderCall.succeededHttp(null, HttpResponse.of(200, null, bidResponse), null);
    }

    private static String givenBidResponse(Bid... bid) throws JsonProcessingException {
        return mapper.writeValueAsString(
                BidResponse.builder()
                        .seatbid(singletonList(SeatBid.builder().bid(asList(bid)).build()))
                        .cur("CUR")
                        .build());
    }

    private static Bid givenBid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder()).build();
    }

    private static Bid givenBid(List<String> cat, AppnexusBidExtAppnexus appnexus) {
        return givenBid(bid -> bid.cat(cat).ext(mapper.valueToTree(AppnexusBidExt.of(appnexus))));
    }

    private static AppnexusBidExtAppnexus givenBidExt(
            UnaryOperator<AppnexusBidExtAppnexus.AppnexusBidExtAppnexusBuilder> extCustomizer) {

        return extCustomizer.apply(AppnexusBidExtAppnexus.builder()).build();
    }
}
