package org.prebid.server.bidder.rubicon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Imp.ImpBuilder;
import com.iab.openrtb.request.Metric;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Segment;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import io.vertx.core.http.HttpMethod;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.rubicon.proto.request.RubiconAppExt;
import org.prebid.server.bidder.rubicon.proto.request.RubiconBannerExt;
import org.prebid.server.bidder.rubicon.proto.request.RubiconBannerExtRp;
import org.prebid.server.bidder.rubicon.proto.request.RubiconImpExt;
import org.prebid.server.bidder.rubicon.proto.request.RubiconImpExtRp;
import org.prebid.server.bidder.rubicon.proto.request.RubiconImpExtRpTrack;
import org.prebid.server.bidder.rubicon.proto.request.RubiconPubExt;
import org.prebid.server.bidder.rubicon.proto.request.RubiconPubExtRp;
import org.prebid.server.bidder.rubicon.proto.request.RubiconSiteExt;
import org.prebid.server.bidder.rubicon.proto.request.RubiconSiteExtRp;
import org.prebid.server.bidder.rubicon.proto.request.RubiconTargeting;
import org.prebid.server.bidder.rubicon.proto.request.RubiconTargetingExt;
import org.prebid.server.bidder.rubicon.proto.request.RubiconTargetingExtRp;
import org.prebid.server.bidder.rubicon.proto.request.RubiconUserExt;
import org.prebid.server.bidder.rubicon.proto.request.RubiconUserExtRp;
import org.prebid.server.bidder.rubicon.proto.request.RubiconVideoExt;
import org.prebid.server.bidder.rubicon.proto.request.RubiconVideoExtRp;
import org.prebid.server.bidder.rubicon.proto.response.RubiconBidResponse;
import org.prebid.server.bidder.rubicon.proto.response.RubiconSeatBid;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.ExtPrebidBidders;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtDeal;
import org.prebid.server.proto.openrtb.ext.request.ExtDealLine;
import org.prebid.server.proto.openrtb.ext.request.ExtImpContext;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidMultiBid;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEid;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEidUid;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEidUidExt;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtImpRubicon;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtImpRubicon.ExtImpRubiconBuilder;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtImpRubiconDebug;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtUserTpIdRubicon;
import org.prebid.server.proto.openrtb.ext.request.rubicon.RubiconVideoParams;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.HttpUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class RubiconBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://rubiconproject.com/exchange.json?tk_xint=prebid";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final List<String> SUPPORTED_VENDORS = Arrays.asList("activeview", "comscore",
            "doubleverify", "integralads", "moat", "sizmek", "whiteops");

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private RubiconBidder rubiconBidder;

    @Mock
    private CurrencyConversionService currencyConversionService;

    @Before
    public void setUp() {
        rubiconBidder = new RubiconBidder(ENDPOINT_URL, USERNAME, PASSWORD, SUPPORTED_VENDORS, false,
                currencyConversionService, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new RubiconBidder("invalid_url", USERNAME, PASSWORD, SUPPORTED_VENDORS, false,
                        currencyConversionService, jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldFillMethodAndUrlAndExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.banner(
                Banner.builder().format(singletonList(Format.builder().w(300).h(250).build())).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).element(0).isNotNull()
                .returns(HttpMethod.POST, HttpRequest::getMethod)
                .returns(ENDPOINT_URL, HttpRequest::getUri);
        assertThat(result.getValue().get(0).getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpUtil.AUTHORIZATION_HEADER.toString(), "Basic dXNlcm5hbWU6cGFzc3dvcmQ="),
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), "application/json;charset=utf-8"),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), "application/json"),
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "prebid-server/1.0"));
    }

    @Test
    public void makeHttpRequestsShouldFilterImpressionsWithInvalidTypes() {
        // given
        final Imp imp1 = givenImp(builder -> builder.video(Video.builder().build()));
        final Imp imp2 = givenImp(builder -> builder.id("2").xNative(Native.builder().build()));
        final Imp imp3 = givenImp(builder -> builder.id("3").audio(Audio.builder().build()));
        final BidRequest bidRequest = BidRequest.builder().imp(asList(imp1, imp2, imp3)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2)
                .containsOnly(
                        BidderError.of("Impression with id 2 rejected with invalid type `xNative`."
                                + " Allowed types are banner and video.", BidderError.Type.bad_input),
                        BidderError.of("Impression with id 3 rejected with invalid type `audio`."
                                + " Allowed types are banner and video.", BidderError.Type.bad_input));
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldFilterAllImpressionsAndReturnErrorMeessagesWithoutRequests() {
        // given
        final Imp imp1 = givenImp(builder -> builder.id("1").xNative(Native.builder().build()));
        final Imp imp2 = givenImp(builder -> builder.id("2").audio(Audio.builder().build()));
        final BidRequest bidRequest = BidRequest.builder().imp(asList(imp1, imp2)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(3)
                .containsOnly(
                        BidderError.of("Impression with id 1 rejected with invalid type `xNative`."
                                + " Allowed types are banner and video.", BidderError.Type.bad_input),
                        BidderError.of("Impression with id 2 rejected with invalid type `audio`."
                                + " Allowed types are banner and video.", BidderError.Type.bad_input),
                        BidderError.of("There are no valid impressions to create bid request to rubicon bidder",
                                BidderError.Type.bad_input));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReplaceDefaultParametersWithExtPrebidBiddersBidder() {
        // given
        final ExtRequest extRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .integration("test")
                .build());

        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.ext(extRequest),
                builder -> builder.banner(Banner.builder().format(singletonList(Format.builder().w(300).h(250).build()))
                        .build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        final String expectedUrl = "http://rubiconproject.com/exchange.json?tk_xint=test";
        assertThat(result.getValue()).hasSize(1).element(0).isNotNull()
                .returns(HttpMethod.POST, HttpRequest::getMethod)
                .returns(expectedUrl, HttpRequest::getUri);
    }

    @Test
    public void makeHttpRequestsShouldAddMaxbidsAttributeFromExtPrebidMultibid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .multibid(singletonList(ExtRequestPrebidMultiBid.of("rubicon", null, 5, "prefix")))
                        .build())),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getExt).doesNotContainNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconImpExt.class))
                .extracting(RubiconImpExt::getMaxbids)
                .containsExactly(5);
    }

    @Test
    public void makeHttpRequestsShouldAddMaxbidsAttributeAsOneIfExtPrebidMultibidMaxBidsIsNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .multibid(singletonList(
                                ExtRequestPrebidMultiBid.of("rubicon", null, null, "prefix")))
                        .build())),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getExt).doesNotContainNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconImpExt.class))
                .extracting(RubiconImpExt::getMaxbids)
                .containsExactly(1);
    }

    @Test
    public void makeHttpRequestsShouldFillImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.video(Video.builder().build()),
                builder -> builder
                        .zoneId(4001)
                        .inventory(mapper.valueToTree(Inventory.of(singletonList("5-star"), singletonList("tech")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getExt).doesNotContainNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconImpExt.class))
                .containsOnly(RubiconImpExt.of(RubiconImpExtRp.of(4001,
                        mapper.valueToTree(Inventory.of(singletonList("5-star"), singletonList("tech"))),
                        RubiconImpExtRpTrack.of("", "")), null, 1, null));
    }

    @Test
    public void makeHttpRequestsShouldFillBannerExtWithAltSizeIdsIfMoreThanOneSize() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.banner(Banner.builder()
                .format(asList(
                        Format.builder().w(250).h(360).build(),
                        Format.builder().w(300).h(250).build(),
                        Format.builder().w(300).h(600).build()))
                .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getBanner).doesNotContainNull()
                .extracting(Banner::getExt).doesNotContainNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconBannerExt.class))
                .extracting(RubiconBannerExt::getRp).doesNotContainNull()
                .extracting(RubiconBannerExtRp::getSizeId, RubiconBannerExtRp::getAltSizeIds)
                .containsOnly(tuple(15, asList(10, 32)));
    }

    @Test
    public void makeHttpRequestsShouldTolerateInvalidSizes() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.banner(Banner.builder()
                .format(asList(
                        Format.builder().w(123).h(456).build(),
                        Format.builder().w(789).h(123).build(),
                        Format.builder().w(300).h(250).build()))
                .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getBanner).doesNotContainNull()
                .extracting(Banner::getExt).doesNotContainNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconBannerExt.class))
                .extracting(RubiconBannerExt::getRp).doesNotContainNull()
                .extracting(RubiconBannerExtRp::getSizeId)
                .containsOnly(15);
    }

    @Test
    public void makeHttpRequestsShouldOverrideBannerFormatWithRubiconSizes() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder()
                                .format(asList(
                                        Format.builder().w(300).h(250).build(),
                                        Format.builder().w(300).h(600).build()))
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpRubicon.builder()
                                .sizes(singletonList(15)).build())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getBanner).doesNotContainNull()
                .flatExtracting(Banner::getFormat).hasSize(1)
                .containsOnly(Format.builder().w(300).h(250).build());
    }

    @Test
    public void makeHttpRequestsShouldSetMobilePortrait67SizeIdFotInterstitialNotValidSize() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .instl(1)
                        .banner(Banner.builder()
                                .format(singletonList(
                                        Format.builder().w(360).h(616).build()))
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpRubicon.builder().build())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getBanner).doesNotContainNull()
                .containsOnly(Banner.builder()
                        .format(singletonList(
                                Format.builder().w(360).h(616).build()))
                        .ext(mapper.valueToTree(RubiconBannerExt.of(RubiconBannerExtRp.of(67, null, "text/html"))))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldSetMobileLandscape101SizeIdFotInterstitialNotValidSize() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .instl(1)
                        .banner(Banner.builder()
                                .format(singletonList(
                                        Format.builder().w(616).h(360).build()))
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpRubicon.builder().build())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getBanner).doesNotContainNull()
                .containsOnly(Banner.builder()
                        .format(singletonList(Format.builder().w(616).h(360).build()))
                        .ext(mapper.valueToTree(RubiconBannerExt.of(RubiconBannerExtRp.of(101, null, "text/html"))))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldCreateBannerRequestIfImpHasBannerAndVideoButNoRequiredVideoFieldsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(250).build())).build())
                        .video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getBanner, Imp::getVideo)
                .containsOnly(tuple(
                        Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(250).build()))
                                .ext(mapper.valueToTree(
                                        RubiconBannerExt.of(RubiconBannerExtRp.of(15, null, "text/html"))))
                                .build(),
                        null)); // video is removed
    }

    @Test
    public void makeHttpRequestsShouldCreateVideoRequestIfImpHasBannerAndVideoButAllRequiredVideoFieldsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(250).build())).build())
                        .video(Video.builder().mimes(singletonList("mime1")).protocols(singletonList(1))
                                .maxduration(60).linearity(2).api(singletonList(3)).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getBanner, Imp::getVideo)
                .containsOnly(tuple(
                        null, // banner is removed
                        Video.builder().mimes(singletonList("mime1")).protocols(singletonList(1))
                                .maxduration(60).linearity(2).api(singletonList(3)).build()));
    }

    @Test
    public void makeHttpRequestsShouldResolveImpBidFloorCurrencyIfNotUSDAndCallCurrencyService() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(250).build())).build())
                        .bidfloor(BigDecimal.ONE).bidfloorcur("EUR"),
                identity());

        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willReturn(BigDecimal.TEN);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        verify(currencyConversionService).convertCurrency(eq(BigDecimal.ONE), any(), eq("EUR"), eq("USD"));
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsOnly(tuple(BigDecimal.TEN, "USD"));
    }

    @Test
    public void makeHttpRequestsShouldNotSetBidFloorCurrencyToUSDIfNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder().debug(1).build())),
                builder -> builder
                        .id("impId")
                        .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(250).build())).build())
                        .bidfloor(BigDecimal.ONE).bidfloorcur(null),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        verifyNoInteractions(currencyConversionService);
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.of("Imp `impId` floor provided with no currency, assuming USD",
                        BidderError.Type.bad_input));
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsOnly(tuple(BigDecimal.ONE, null));
    }

    @Test
    public void makeHttpRequestsShouldIgnoreBidRequestIfCurrencyServiceThrowsAnException() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .id("impId")
                        .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(250).build())).build())
                        .bidfloor(BigDecimal.ONE).bidfloorcur("EUR"),
                identity());

        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willThrow(new PreBidException("failed"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.of("Unable to convert provided bid floor currency from EUR to USD"
                        + " for imp `impId` with a reason: failed", BidderError.Type.bad_input));
    }

    @Test
    public void shouldSetSizeIdTo201IfPlacementIs1AndSizeIdIsNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.instl(1).video(Video.builder().placement(1).build()),
                builder -> builder.video(RubiconVideoParams.builder().sizeId(null).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo).doesNotContainNull()
                .extracting(Video::getExt).doesNotContainNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconVideoExt.class))
                .extracting(RubiconVideoExt::getRp)
                .extracting(RubiconVideoExtRp::getSizeId)
                .containsOnly(201);
    }

    @Test
    public void shouldSetSizeIdTo203IfPlacementIs3AndSizeIdIsNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.instl(1).video(Video.builder().placement(3).build()),
                builder -> builder.video(RubiconVideoParams.builder().sizeId(null).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo).doesNotContainNull()
                .extracting(Video::getExt).doesNotContainNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconVideoExt.class))
                .extracting(RubiconVideoExt::getRp)
                .extracting(RubiconVideoExtRp::getSizeId)
                .containsOnly(203);
    }

    @Test
    public void shouldSetSizeIdTo202UsingInstlFlagIfPlacementAndSizeIdAreNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.instl(1).video(Video.builder().placement(null).build()),
                builder -> builder.video(RubiconVideoParams.builder().sizeId(null).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo).doesNotContainNull()
                .extracting(Video::getExt).doesNotContainNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconVideoExt.class))
                .extracting(RubiconVideoExt::getRp)
                .extracting(RubiconVideoExtRp::getSizeId)
                .containsOnly(202);
    }

    @Test
    public void makeHttpRequestsShouldFillVideoExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.video(Video.builder().build()),
                builder -> builder.video(RubiconVideoParams.builder().skip(5).skipdelay(10).sizeId(14).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getVideo).doesNotContainNull()
                .extracting(Video::getExt).doesNotContainNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconVideoExt.class))
                .containsOnly(RubiconVideoExt.of(5, 10, RubiconVideoExtRp.of(14), null));
    }

    @Test
    public void makeHttpRequestsShouldTransferRewardedVideoFlagIntoRewardedVideoObject() {
        // given
        final ExtImpPrebid prebid =
                ExtImpPrebid.builder().isRewardedInventory(1).build();
        final ExtImpRubicon rubicon = ExtImpRubicon.builder()
                .video(RubiconVideoParams.builder().skip(5).skipdelay(10).sizeId(14).build())
                .build();

        final ExtPrebid<ExtImpPrebid, ExtImpRubicon> ext = ExtPrebid.of(prebid, rubicon);

        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.video(Video.builder().build())
                .ext(mapper.valueToTree(ext)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getVideo).doesNotContainNull()
                .extracting(Video::getExt).doesNotContainNull()
                .extracting(ex -> mapper.treeToValue(ex, RubiconVideoExt.class))
                .containsOnly(RubiconVideoExt.of(5, 10, RubiconVideoExtRp.of(14), "rewarded"));
    }

    @Test
    public void makeHttpRequestsShouldIgnoreRewardedVideoLogic() {
        // given
        final ExtImpPrebid prebid = ExtImpPrebid.builder().build();
        final ExtImpRubicon rubicon = ExtImpRubicon.builder()
                .video(RubiconVideoParams.builder().skip(5).skipdelay(10).sizeId(14).build())
                .build();

        final ExtPrebid<ExtImpPrebid, ExtImpRubicon> ext = ExtPrebid.of(prebid, rubicon);

        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.video(Video.builder().build())
                .ext(mapper.valueToTree(ext)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getVideo).doesNotContainNull()
                .extracting(Video::getExt).doesNotContainNull()
                .extracting(ex -> mapper.treeToValue(ex, RubiconVideoExt.class))
                .containsOnly(RubiconVideoExt.of(5, 10, RubiconVideoExtRp.of(14), null));
    }

    @Test
    public void makeHttpRequestsShouldIgnoreRewardedVideoLogicIfRewardedInventoryIsNotOne() {
        // given
        final ExtImpPrebid prebid = ExtImpPrebid.builder().isRewardedInventory(2).build();
        final ExtImpRubicon rubicon = ExtImpRubicon.builder()
                .video(RubiconVideoParams.builder().skip(5).skipdelay(10).sizeId(14).build())
                .build();

        final ExtPrebid<ExtImpPrebid, ExtImpRubicon> ext = ExtPrebid.of(prebid, rubicon);

        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.video(Video.builder().build())
                .ext(mapper.valueToTree(ext)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getVideo).doesNotContainNull()
                .extracting(Video::getExt).doesNotContainNull()
                .extracting(ex -> mapper.treeToValue(ex, RubiconVideoExt.class))
                .containsOnly(RubiconVideoExt.of(5, 10, RubiconVideoExtRp.of(14), null));
    }

    @Test
    public void makeHttpRequestsShouldNotFailIfVideoParamIsNull() {
        // given
        final ExtImpPrebid prebid = ExtImpPrebid
                .builder().build();
        final ExtImpRubicon rubicon = ExtImpRubicon.builder()
                .video(null)
                .build();

        final ExtPrebid<ExtImpPrebid, ExtImpRubicon> ext = ExtPrebid.of(prebid, rubicon);

        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.video(Video.builder().build())
                .ext(mapper.valueToTree(ext)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldIgnoreRewardedVideoFlag() {
        // given
        final ExtImpPrebid prebid = ExtImpPrebid.builder().isRewardedInventory(0).build();
        final ExtImpRubicon rubicon = ExtImpRubicon.builder()
                .video(RubiconVideoParams.builder().skip(5).skipdelay(10).sizeId(14).build())
                .build();

        final ExtPrebid<ExtImpPrebid, ExtImpRubicon> ext = ExtPrebid.of(prebid, rubicon);

        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.video(Video.builder().build())
                .ext(mapper.valueToTree(ext)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getVideo).doesNotContainNull()
                .extracting(Video::getExt).doesNotContainNull()
                .extracting(ex -> mapper.treeToValue(ex, RubiconVideoExt.class))
                .containsOnly(RubiconVideoExt.of(5, 10, RubiconVideoExtRp.of(14), null));
    }

    @Test
    public void makeHttpRequestsShouldFillUserExtIfUserAndVisitorPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder().build()),
                builder -> builder.video(Video.builder().build()),
                builder -> builder.visitor(mapper.valueToTree(
                        Visitor.of(singletonList("new"), singletonList("iphone")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser).doesNotContainNull()
                .containsOnly(User.builder()
                        .ext(jacksonMapper.fillExtension(
                                ExtUser.builder().build(),
                                RubiconUserExt.builder()
                                        .rp(RubiconUserExtRp.of(mapper.valueToTree(
                                                Visitor.of(singletonList("new"), singletonList("iphone")))))
                                        .build()))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldFillUserExtRpWithIabAttributeIfSegtaxEqualsFour() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder()
                        .data(singletonList(
                                givenDataWithSegmentEntry(4, "segmentId")
                        )).build()),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedTarget = mapper.createObjectNode();
        final ArrayNode expectedIabAttribute = expectedTarget.putArray("iab");
        expectedIabAttribute.add("segmentId");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser).doesNotContainNull()
                .extracting(User::getExt)
                .containsOnly(jacksonMapper.fillExtension(
                        ExtUser.builder().build(),
                        RubiconUserExt.builder()
                                .rp(RubiconUserExtRp.of(expectedTarget))
                                .build()));
    }

    @Test
    public void makeHttpRequestsShouldFillUserExtRpWithIabAttributeOnlyIfSegtaxIsEqualFour() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder()
                        .data(asList(
                                givenDataWithSegmentEntry(4, "segmentId"),
                                givenDataWithSegmentEntry(2, "secondSegmentId")))
                        .build()),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedTarget = mapper.createObjectNode();
        final ArrayNode expectedIabAttribute = expectedTarget.putArray("iab");
        expectedIabAttribute.add("segmentId");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser).doesNotContainNull()
                .extracting(User::getExt)
                .containsOnly(jacksonMapper.fillExtension(
                        ExtUser.builder().build(),
                        RubiconUserExt.builder()
                                .rp(RubiconUserExtRp.of(expectedTarget))
                                .build()));
    }

    @Test
    public void makeHttpRequestsShouldFillSiteExtRpWithIabAttributeIfSegtaxEqualsOneOrTwoOrFiveOrSix() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.site(Site.builder()
                        .content(Content.builder()
                                .data(asList(
                                        givenDataWithSegmentEntry(1, "firstSegmentId"),
                                        givenDataWithSegmentEntry(2, "secondSegmentId"),
                                        givenDataWithSegmentEntry(3, "thirdSegmentId"),
                                        givenDataWithSegmentEntry(5, "fifthSegmentId"),
                                        givenDataWithSegmentEntry(6, "sixthSegmentId")))
                                .build())
                        .build()),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedTarget = mapper.createObjectNode();
        final ArrayNode expectedIabAttribute = expectedTarget.putArray("iab");
        expectedIabAttribute.add("firstSegmentId");
        expectedIabAttribute.add("secondSegmentId");
        expectedIabAttribute.add("fifthSegmentId");
        expectedIabAttribute.add("sixthSegmentId");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite).doesNotContainNull()
                .extracting(Site::getExt)
                .extracting(ext -> ext.getProperty("rp"))
                .extracting(rp -> rp.get("target"))
                .containsExactly(expectedTarget);
    }

    @Test
    public void makeHttpRequestsShouldIgnoreNotIntSegtaxProperty() {
        // given
        final ObjectNode userNode = mapper.createObjectNode();
        userNode.put("segtax", "3");
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder().data(singletonList(Data.builder()
                        .segment(singletonList(Segment.builder().id("segmentId")
                                .build()))
                        .ext(userNode).build())).build()),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser).doesNotContainNull()
                .extracting(User::getExt)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldNotFillUserExtRpWhenVisitorAndInventoryIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder().id("id").build()),
                builder -> builder.video(Video.builder().build()),
                builder -> builder
                        .visitor(mapper.createObjectNode())
                        .inventory(mapper.createObjectNode()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser).doesNotContainNull()
                .containsOnly(User.builder().id("id").build());
    }

    @Test
    public void makeHttpRequestsShouldFillUserIfUserAndConsentArePresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder()
                        .ext(ExtUser.builder().consent("consent").build())
                        .build()),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser).doesNotContainNull()
                .containsOnly(User.builder()
                        .ext(ExtUser.builder().consent("consent").build())
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldCopyUserKeywords() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder.user(User.builder().keywords("user keyword").build()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser)
                .extracting(User::getKeywords)
                .containsOnly("user keyword");
    }

    @Test
    public void makeHttpRequestsShouldRemoveUserGenderYobGeoExtData() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder()
                        .buyeruid("buyeruid")
                        .gender("M")
                        .yob(2000)
                        .geo(Geo.builder().country("US").build())
                        .ext(ExtUser.builder()
                                .consent("consent")
                                .data(mapper.createObjectNode())
                                .build())
                        .build()),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser)
                .containsOnly(User.builder()
                        .buyeruid("buyeruid")
                        .ext(ExtUser.builder()
                                .consent("consent")
                                .build())
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldMergeUserExtDataFieldsToUserExtRp() {
        // given
        final ExtUser userExt = jacksonMapper.fillExtension(
                ExtUser.builder()
                        .data(mapper.createObjectNode()
                                .set("property", mapper.createArrayNode().add("valueFromExtData")))
                        .build(),
                RubiconUserExt.builder()
                        .rp(RubiconUserExtRp.of(mapper.createObjectNode()
                                .set("property", mapper.createArrayNode().add("valueFromExtRpTarget"))))
                        .build());

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder().ext(userExt).build()),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser).doesNotContainNull()
                .extracting(User::getExt)
                .containsOnly(jacksonMapper.fillExtension(
                        ExtUser.builder().build(),
                        RubiconUserExt.builder()
                                .rp(RubiconUserExtRp.of(mapper.createObjectNode()
                                        .set("property", mapper.createArrayNode()
                                                .add("valueFromExtRpTarget")
                                                .add("valueFromExtData"))))
                                .build()));
    }

    @Test
    public void makeHttpRequestsShouldCreateUserIfVisitorPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                builder -> builder.video(Video.builder().build()),
                builder -> builder.visitor(mapper.valueToTree(
                        Visitor.of(singletonList("new"), singletonList("iphone")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser).doesNotContainNull()
                .containsOnly(User.builder()
                        .ext(jacksonMapper.fillExtension(
                                ExtUser.builder().build(),
                                RubiconUserExt.builder()
                                        .rp(RubiconUserExtRp.of(mapper.valueToTree(
                                                Visitor.of(singletonList("new"), singletonList("iphone")))))
                                        .build()))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldNormalizeAndCopyUserExtDataFieldsToUserExtRp() {
        // given
        final ObjectNode userExtDataNode = mapper.createObjectNode()
                // will be copied verbatim
                .<ObjectNode>set("property1", mapper.createArrayNode().add("value1").add("value2"))
                // will be normalized to array of strings
                .put("property2", "value1")
                .put("property3", 123)
                .put("property4", false)
                .<ObjectNode>set("property5", mapper.createArrayNode().add(true).add(false))
                // remnants will be discarded
                .<ObjectNode>set("property6", mapper.createArrayNode().add("value1").add(123))
                .<ObjectNode>set("property7", mapper.createObjectNode().put("sub-property1", "value1"))
                .put("property8", 123.456d);

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .user(User.builder()
                                .ext(ExtUser.builder().data(userExtDataNode).build())
                                .build()),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser).doesNotContainNull()
                .extracting(User::getExt)
                .containsOnly(jacksonMapper.fillExtension(
                        ExtUser.builder().build(),
                        RubiconUserExt.builder()
                                .rp(RubiconUserExtRp.of(mapper.createObjectNode()
                                        .<ObjectNode>set("property1", mapper.createArrayNode()
                                                .add("value1")
                                                .add("value2"))
                                        .<ObjectNode>set("property2", mapper.createArrayNode().add("value1"))
                                        .<ObjectNode>set("property3", mapper.createArrayNode().add("123"))
                                        .<ObjectNode>set("property4", mapper.createArrayNode().add("false"))
                                        .<ObjectNode>set("property5", mapper.createArrayNode()
                                                .add("true")
                                                .add("false"))))
                                .build()));
    }

    @Test
    public void makeHttpRequestsShouldNotCreateUserIfVisitorAndConsentNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser)
                .containsOnly((User) null);
    }

    @Test
    public void makeHttpRequestsShouldUseUserBuyeruidIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder()
                        .buyeruid("buyeruid")
                        .ext(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of(
                                        "rubiconproject.com",
                                        null,
                                        singletonList(ExtUserEidUid.of(
                                                "extUserEidUidId",
                                                null,
                                                ExtUserEidUidExt.of(null, "ppuid")
                                        )),
                                        null)))
                                .build())
                        .build()),
                builder -> builder.video(Video.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getUser)
                .extracting(User::getBuyeruid)
                .containsExactly("buyeruid");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUseUidIdIfUserBuyeruidAbsentAndSpecialEidSourceAndStypeIsPpuid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder()
                        .ext(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of(
                                        "rubiconproject.com",
                                        null,
                                        singletonList(ExtUserEidUid.of(
                                                "extUserEidUidId",
                                                null,
                                                ExtUserEidUidExt.of(null, "ppuid")
                                        )),
                                        null)))
                                .build())
                        .build()),
                builder -> builder.video(Video.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getUser)
                .extracting(User::getBuyeruid)
                .containsExactly("extUserEidUidId");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUseUidIdIfUserBuyeruidAbsentAndSpecialEidSourceAndStypeIsOther() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder()
                        .ext(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of(
                                        "rubiconproject.com",
                                        null,
                                        singletonList(ExtUserEidUid.of(
                                                "extUserEidUidId",
                                                null,
                                                ExtUserEidUidExt.of(null, "other")
                                        )),
                                        null)))
                                .build())
                        .build()),
                builder -> builder.video(Video.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getUser)
                .extracting(User::getBuyeruid)
                .containsExactly("extUserEidUidId");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateUserExtTpIdWithAdServerEidSource() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.user(User.builder()
                        .ext(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of("adserver.org", null,
                                        singletonList(
                                                ExtUserEidUid.of("adServerUid", null,
                                                        ExtUserEidUidExt.of("TDID", null))), null)))
                                .build())
                        .build()),
                builder -> builder.video(Video.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(request -> request.getUser().getExt())
                .containsOnly(jacksonMapper.fillExtension(
                        ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of(
                                        "adserver.org",
                                        null,
                                        singletonList(ExtUserEidUid.of("adServerUid", null,
                                                ExtUserEidUidExt.of("TDID", null))),
                                        null)))
                                .build(),
                        RubiconUserExt.builder()
                                .tpid(singletonList(ExtUserTpIdRubicon.of("tdid", "adServerUid")))
                                .build()));
    }

    @Test
    public void makeHttpRequestsShouldUseGivenUserIdIfOtherExtUserFieldsPassed() {
        // given
        final ExtUser extUser = ExtUser.builder()
                .eids(singletonList(ExtUserEid.of("liveramp.com", null,
                        singletonList(ExtUserEidUid.of("firstId", null, null)), null)))
                .build();
        final BidRequest bidRequest = givenBidRequest(builder -> builder.user(User.builder()
                        .id("userId")
                        .ext(extUser)
                        .build()),
                builder -> builder.video(Video.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser)
                .extracting(User::getId)
                .containsOnly("userId");
    }

    @Test
    public void makeHttpRequestsShouldCreateUserIdIfMissingFromFirstUidStypePpuid() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.user(User.builder()
                        .ext(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of(null, null,
                                        asList(
                                                ExtUserEidUid.of("id1", null, ExtUserEidUidExt.of(null, "other")),
                                                ExtUserEidUid.of("id2", null, ExtUserEidUidExt.of(null, "ppuid")),
                                                ExtUserEidUid.of("id3", null, ExtUserEidUidExt.of(null, "ppuid"))),
                                        null)))
                                .build())
                        .build()),
                builder -> builder.video(Video.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(request -> request.getUser().getId())
                .containsOnly("id2");
    }

    @Test
    public void makeHttpRequestsShouldNotCreateUserIdIfMissingWhenNoUidWithPpuidType() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.user(User.builder()
                        .ext(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of(null, null,
                                        asList(
                                                ExtUserEidUid.of("id1", null, ExtUserEidUidExt.of(null, "other")),
                                                ExtUserEidUid.of("id2", null, ExtUserEidUidExt.of(null, "other"))),
                                        null)))
                                .build())
                        .build()),
                builder -> builder.video(Video.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(request -> request.getUser().getId()).element(0).isNull();
    }

    @Test
    public void makeHttpRequestsShouldRemoveStypesPpuidSha256emailDmp() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.user(User.builder()
                        .ext(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of("source", "id",
                                        asList(
                                                ExtUserEidUid.of("id1", null, ExtUserEidUidExt.of(null, "other")),
                                                ExtUserEidUid.of("id2", null, ExtUserEidUidExt.of(null, "ppuid")),
                                                ExtUserEidUid.of("id3", null, ExtUserEidUidExt.of(null, "sha256email")),
                                                ExtUserEidUid.of("id4", null, ExtUserEidUidExt.of(null, "dmp"))),
                                        null)))
                                .build())
                        .build()),
                builder -> builder.video(Video.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(request -> request.getUser().getExt()).hasSize(1).element(0)
                .isEqualTo(ExtUser.builder()
                        .eids(singletonList(ExtUserEid.of("source", "id",
                                asList(
                                        ExtUserEidUid.of("id1", null, ExtUserEidUidExt.of(null, "other")),
                                        ExtUserEidUid.of("id2", null, ExtUserEidUidExt.of(null, null)),
                                        ExtUserEidUid.of("id3", null, ExtUserEidUidExt.of(null, null)),
                                        ExtUserEidUid.of("id4", null, ExtUserEidUidExt.of(null, null))),
                                null)))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldCreateUserExtTpIdForFirstLiveintentAndAdserver() {
        // given
        final ObjectNode uidExt = mapper.createObjectNode();
        uidExt.putArray("segments").add("999").add("888");
        final ExtUserEid liveintentUid1 = ExtUserEid.of("liveintent.com", null,
                singletonList(ExtUserEidUid.of("liveintentUid1", null, null)), uidExt);
        final ExtUserEid liveintentUid2 = ExtUserEid.of("liveintent.com", null,
                singletonList(ExtUserEidUid.of("liveintentUid2", null, null)), null);
        final ExtUserEid adserverUid = ExtUserEid.of("adserver.org", null,
                singletonList(ExtUserEidUid.of("adServerUid", null, ExtUserEidUidExt.of("TDID", null))), null);
        final ExtUserEid notSpecialSource = ExtUserEid.of("notSpecialSource", null,
                singletonList(ExtUserEidUid.of("notSpecialSource", null, ExtUserEidUidExt.of("TDID", null))), null);
        final BidRequest bidRequest = givenBidRequest(builder -> builder.user(User.builder()
                        .ext(ExtUser.builder()
                                .eids(Arrays.asList(liveintentUid1, liveintentUid2, adserverUid, notSpecialSource))
                                .build())
                        .build()),
                builder -> builder.video(Video.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedRp = mapper.createObjectNode();
        expectedRp.putArray("LIseg").add("999").add("888");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(request -> request.getUser().getExt())
                .containsOnly(jacksonMapper.fillExtension(
                        ExtUser.builder()
                                .eids(Arrays.asList(liveintentUid1, liveintentUid2, adserverUid, notSpecialSource))
                                .build(),
                        RubiconUserExt.builder()
                                .tpid(Arrays.asList(
                                        ExtUserTpIdRubicon.of("tdid", "adServerUid"),
                                        ExtUserTpIdRubicon.of("liveintent.com", "liveintentUid1")))
                                .rp(RubiconUserExtRp.of(expectedRp))
                                .build()));
    }

    @Test
    public void makeHttpRequestsShouldCreateUserExtTpIdWithLiveintentEidSourceAndRpFromEidExtWhenMultipleEidSources() {
        // given
        final ObjectNode uidExt = mapper.createObjectNode();
        uidExt.putArray("segments").add("999").add("888");
        final BidRequest bidRequest = givenBidRequest(builder -> builder.user(User.builder()
                        .ext(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of("liveintent.com", null,
                                        singletonList(
                                                ExtUserEidUid.of("liveintentUid", null, null)),
                                        uidExt)))
                                .build())
                        .build()),
                builder -> builder.video(Video.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedRp = mapper.createObjectNode();
        expectedRp.putArray("LIseg").add("999").add("888");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(request -> request.getUser().getExt())
                .containsOnly(jacksonMapper.fillExtension(
                        ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of("liveintent.com", null,
                                        singletonList(ExtUserEidUid.of("liveintentUid", null, null)), uidExt)))
                                .build(),
                        RubiconUserExt.builder()
                                .tpid(singletonList(ExtUserTpIdRubicon.of("liveintent.com", "liveintentUid")))
                                .rp(RubiconUserExtRp.of(expectedRp))
                                .build()));
    }

    @Test
    public void makeHttpRequestsShouldNotCreateUserExtTpIdWithAdServerEidSourceIfEidUidExtMissed() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.user(User.builder()
                        .ext(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of("adserver.org", null,
                                        singletonList(ExtUserEidUid.of("id", null, null)), null)))
                                .build())
                        .build()),
                builder -> builder.video(Video.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(request -> request.getUser().getExt())
                .containsOnly(jacksonMapper.fillExtension(
                        ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of("adserver.org", null,
                                        singletonList(ExtUserEidUid.of("id", null, null)), null)))
                                .build(),
                        RubiconUserExt.builder()
                                .tpid(null)
                                .build()));
    }

    @Test
    public void makeHttpRequestsShouldNotCreateUserExtTpIdWithAdServerEidSourceIfExtRtiPartnerMissed() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.user(User.builder()
                        .ext(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of("adserver.org", null,
                                        singletonList(ExtUserEidUid.of("id", null, ExtUserEidUidExt.of(null, null))),
                                        null)))
                                .build())
                        .build()),
                builder -> builder.video(Video.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(request -> request.getUser().getExt())
                .containsOnly(ExtUser.builder()
                        .eids(singletonList(ExtUserEid.of("adserver.org", null,
                                singletonList(ExtUserEidUid.of("id", null, ExtUserEidUidExt.of(null, null))), null)))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldCreateUserExtLiverampId() {
        // given
        final ExtUser extUser = ExtUser.builder()
                .eids(asList(
                        ExtUserEid.of("liveramp.com", null,
                                asList(
                                        ExtUserEidUid.of("firstId", null, null),
                                        ExtUserEidUid.of("ignored", null, null)), null),
                        ExtUserEid.of("liveramp.com", null,
                                singletonList(ExtUserEidUid.of("ignored", null, null)), null)))
                .build();
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder().ext(extUser).build()),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser).doesNotContainNull()
                .containsOnly(User.builder()
                        .ext(jacksonMapper.fillExtension(
                                extUser,
                                RubiconUserExt.builder().liverampIdl("firstId").build()))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldIgnoreLiverampIdIfMissingEidUidId() {
        // given
        final ExtUser extUser = ExtUser.builder()
                .eids(asList(
                        ExtUserEid.of("liveramp.com", null, null, null),
                        ExtUserEid.of("liveramp.com", null, emptyList(), null)))
                .build();
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder().ext(extUser).build()),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser).doesNotContainNull()
                .containsOnly(User.builder().ext(extUser).build());
    }

    @Test
    public void makeHttpRequestsShouldNotCreateUserExtTpIdWithUnknownEidSource() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.user(User.builder()
                        .ext(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of("unknownSource", null,
                                        singletonList(ExtUserEidUid.of("id", null, ExtUserEidUidExt.of("eidUidId",
                                                null))),
                                        null)))
                                .build())
                        .build()),
                builder -> builder.video(Video.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(request -> request.getUser().getExt())
                .containsOnly(ExtUser.builder()
                        .eids(singletonList(ExtUserEid.of("unknownSource", null,
                                singletonList(ExtUserEidUid.of("id", null, ExtUserEidUidExt.of("eidUidId", null))),
                                null)))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldFillRegsIfRegsAndGdprArePresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.regs(Regs.of(null, ExtRegs.of(50, null))),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getRegs).doesNotContainNull()
                .containsOnly(Regs.of(null, ExtRegs.of(50, null)));
    }

    @Test
    public void makeHttpRequestsShouldFillDeviceExtIfDevicePresent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.device(Device.builder().pxratio(BigDecimal.valueOf(4.2)).build()),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readTree(httpRequest.getBody()))
                .extracting(request -> request.at("/device/ext/rp/pixelratio"))
                // created manually, because mapper creates Double ObjectNode instead of BigDecimal
                // for floating point numbers (affects testing only)
                .containsOnly(mapper.readTree("4.2"));
    }

    @Test
    public void makeHttpRequestsShouldFillSiteExtIfSitePresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.site(Site.builder().build()),
                builder -> builder.video(Video.builder().build()),
                builder -> builder.accountId(2001).siteId(3001));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite).doesNotContainNull()
                .containsOnly(Site.builder()
                        .publisher(Publisher.builder()
                                .ext(jacksonMapper.fillExtension(
                                        ExtPublisher.empty(),
                                        RubiconPubExt.of(RubiconPubExtRp.of(2001))))
                                .build())
                        .ext(jacksonMapper.fillExtension(
                                ExtSite.of(null, null), RubiconSiteExt.of(RubiconSiteExtRp.of(3001, null))))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldPassSiteExtAmpIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.site(Site.builder().ext(ExtSite.of(1, null)).build()),
                builder -> builder.video(Video.builder().build()),
                builder -> builder.accountId(2001).siteId(3001));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite).doesNotContainNull()
                .containsOnly(Site.builder()
                        .publisher(Publisher.builder()
                                .ext(jacksonMapper.fillExtension(
                                        ExtPublisher.empty(),
                                        RubiconPubExt.of(RubiconPubExtRp.of(2001))))
                                .build())
                        .ext(jacksonMapper.fillExtension(
                                ExtSite.of(1, null), RubiconSiteExt.of(RubiconSiteExtRp.of(3001, null))))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldRemoveSiteExtData() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.site(Site.builder().ext(ExtSite.of(null, mapper.createObjectNode())).build()),
                builder -> builder.video(Video.builder().build()),
                builder -> builder.siteId(3001));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .extracting(Site::getExt)
                .containsOnly(jacksonMapper.fillExtension(
                        ExtSite.of(null, null),
                        RubiconSiteExt.of(RubiconSiteExtRp.of(3001, null))));
    }

    @Test
    public void makeHttpRequestsShouldFillAppExtIfAppPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.app(App.builder().build()),
                builder -> builder.video(Video.builder().build()),
                builder -> builder.accountId(2001).siteId(3001));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getApp).doesNotContainNull()
                .containsOnly(App.builder()
                        .publisher(Publisher.builder()
                                .ext(jacksonMapper.fillExtension(
                                        ExtPublisher.empty(),
                                        RubiconPubExt.of(RubiconPubExtRp.of(2001))))
                                .build())
                        .ext(jacksonMapper.fillExtension(
                                ExtApp.of(null, null),
                                RubiconAppExt.of(RubiconSiteExtRp.of(3001, null))))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldRemoveAppExtData() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.app(App.builder().ext(ExtApp.of(null, mapper.createObjectNode())).build()),
                builder -> builder.video(Video.builder().build()),
                builder -> builder.siteId(3001));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getApp)
                .extracting(App::getExt)
                .containsOnly(jacksonMapper.fillExtension(
                        ExtApp.of(null, null),
                        RubiconAppExt.of(RubiconSiteExtRp.of(3001, null))));
    }

    @Test
    public void makeHttpRequestsShouldSuppressCurrenciesIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.cur(singletonList("ANY")),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getCur).hasSize(1).containsNull();
    }

    @Test
    public void makeHttpRequestsShouldSuppressExtIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder().build())),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getExt).hasSize(1).containsNull();
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestPerImp() {
        // given
        final Imp imp1 = givenImp(builder -> builder.video(Video.builder().build()));
        final Imp imp2 = givenImp(builder -> builder.id("2").video(Video.builder().build()));
        final BidRequest bidRequest = BidRequest.builder().imp(asList(imp1, imp2)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        final BidRequest expectedBidRequest1 = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .video(Video.builder().build())
                        .ext(mapper.valueToTree(RubiconImpExt.of(
                                RubiconImpExtRp.of(null, null, RubiconImpExtRpTrack.of("", "")), null, 1, null)))
                        .build()))
                .build();
        final BidRequest expectedBidRequest2 = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("2")
                        .video(Video.builder().build())
                        .ext(mapper.valueToTree(RubiconImpExt.of(
                                RubiconImpExtRp.of(null, null, RubiconImpExtRpTrack.of("", "")), null, 1, null)))
                        .build()))
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsOnly(expectedBidRequest1, expectedBidRequest2);
    }

    @Test
    public void makeHttpRequestsShouldCopyAndModifyImpExtContextDataAndDataFieldsToRubiconImpExtRpTarget() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        bidRequest.getImp().get(0).getExt()
                .<ObjectNode>set("context", mapper.createObjectNode()
                        .set("data", mapper.createObjectNode()
                                .<ObjectNode>set("property1", mapper.createArrayNode().add("value1"))))
                .set("data", mapper.createObjectNode()
                        .set("property2", mapper.createArrayNode().add("value2")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(mapper.createObjectNode()
                        .<ObjectNode>set("property1", mapper.createArrayNode().add("value1"))
                        .<ObjectNode>set("property2", mapper.createArrayNode().add("value2")));
    }

    @Test
    public void makeHttpRequestsShouldPassThroughImpExtGpid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(), impBuilder -> impBuilder.video(Video.builder().build()), identity());

        bidRequest.getImp().get(0).getExt().set("gpid", TextNode.valueOf("gpidvalue"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("gpid"))
                .containsExactly(TextNode.valueOf("gpidvalue"));
    }

    @Test
    public void makeHttpRequestsShouldNotCopyAdSlotFromAdServerWhenAdServerNameIsNotGam() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        bidRequest.getImp().get(0).getExt()
                .set("context", mapper.createObjectNode()
                        .set("data", mapper.createObjectNode()
                                .put("property", "value")
                                .set("adserver", mapper.createObjectNode()
                                        .put("name", "not-gam")
                                        .put("adslot", "/test-adserver"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(mapper.createObjectNode()
                        .<ObjectNode>set("property", mapper.createArrayNode().add("value")));
    }

    @Test
    public void makeHttpRequestsShouldNotCopyAndModifyImpExtContextDataAdslotToRubiconImpExtRpTargetDfpAdUnitCode() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        final ObjectNode impExt = bidRequest.getImp().get(0).getExt();
        final ObjectNode impExtContextDataNode = mapper.createObjectNode()
                .set("adslot", mapper.createArrayNode().add("123"));
        impExt.set("context", mapper.valueToTree(ExtImpContext.of(impExtContextDataNode)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(mapper.createObjectNode().set("adslot", mapper.createArrayNode().add("123")));
    }

    @Test
    public void makeHttpRequestsShouldCopySiteExtDataFieldsToRubiconImpExtRpTarget() {
        // given
        final ObjectNode siteExtDataNode = mapper.createObjectNode()
                .set("property", mapper.createArrayNode().add("value"));
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .site(Site.builder().ext(ExtSite.of(0, siteExtDataNode)).build()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(mapper.createObjectNode().set("property", mapper.createArrayNode().add("value")));
    }

    @Test
    public void makeHttpRequestsShouldCopyAppExtDataFieldsToRubiconImpExtRpTarget() {
        // given
        final ObjectNode appExtDataNode = mapper.createObjectNode()
                .set("property", mapper.createArrayNode().add("value"));
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .app(App.builder().ext(ExtApp.of(null, appExtDataNode)).build()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(mapper.createObjectNode().set("property", mapper.createArrayNode().add("value")));
    }

    @Test
    public void makeHttpRequestsShouldCopySiteExtDataAndImpExtContextDataFieldsToRubiconImpExtRpTarget()
            throws IOException {
        // given
        final ObjectNode siteExtDataNode = mapper.createObjectNode()
                .set("site", mapper.createArrayNode().add("value1"));
        final ObjectNode impExtContextDataNode = mapper.createObjectNode()
                .set("imp_ext", mapper.createArrayNode().add("value2"));

        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .site(Site.builder().ext(ExtSite.of(0, siteExtDataNode)).build()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        final ObjectNode impExt = bidRequest.getImp().get(0).getExt();
        impExt.set("context", mapper.valueToTree(ExtImpContext.of(impExtContextDataNode)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(mapper.readTree("{\"imp_ext\":[\"value2\"],\"site\":[\"value1\"]}"));
    }

    @Test
    public void makeHttpRequestsShouldCopyAppExtDataAndImpExtContextDataFieldsToRubiconImpExtRpTarget()
            throws IOException {
        // given
        final ObjectNode appExtDataNode = mapper.createObjectNode()
                .set("app", mapper.createArrayNode().add("value1"));
        final ObjectNode impExtContextDataNode = mapper.createObjectNode()
                .set("imp_ext", mapper.createArrayNode().add("value2"));

        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .app(App.builder().ext(ExtApp.of(null, appExtDataNode)).build()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        final ObjectNode impExt = bidRequest.getImp().get(0).getExt();
        impExt.set("context", mapper.valueToTree(ExtImpContext.of(impExtContextDataNode)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(mapper.readTree("{\"imp_ext\":[\"value2\"],\"app\":[\"value1\"]}"));
    }

    @Test
    public void makeHttpRequestsShouldMergeImpExtRubiconAndContextAndDataKeywordsToRubiconImpExtRpTargetKeywords()
            throws IOException {

        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        final ObjectNode impExt = bidRequest.getImp().get(0).getExt();
        final ExtImpContext extImpContext = ExtImpContext.of(null);
        extImpContext.addProperty("keywords", new TextNode("imp,ext,context,keywords"));
        impExt.set("context", mapper.valueToTree(extImpContext));
        impExt.set("data", mapper.createObjectNode()
                .put("keywords", "imp,ext,data,keywords"));
        impExt.set(
                "bidder",
                mapper.valueToTree(ExtImpRubicon.builder()
                        .keywords(asList("imp", "ext", "rubicon", "keywords"))
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(mapper.readTree("{\"keywords\":["
                        + "\"imp,ext,data,keywords\","
                        + " \"imp\","
                        + " \"ext\","
                        + " \"context\","
                        + " \"keywords\","
                        + " \"data\","
                        + " \"rubicon\""
                        + "]}"));
    }

    @Test
    public void makeHttpRequestsShouldCopyImpExtContextAndDataSearchToRubiconImpExtRpTargetSearch() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        final ObjectNode impExt = bidRequest.getImp().get(0).getExt();
        final ExtImpContext extImpContext = ExtImpContext.of(null);
        extImpContext.addProperty("search", new TextNode("imp ext context search"));
        impExt.set("context", mapper.valueToTree(extImpContext));
        impExt.set("data", mapper.createObjectNode().put("search", "imp ext data search"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(mapper.readTree("{\"search\":[\"imp ext data search\", \"imp ext context search\"]}"));
    }

    @Test
    public void makeHttpRequestsShouldCopyImpExtVideoLanguageToSiteContentLanguage() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.video(Video.builder().build()),
                extImp -> extImp.video(RubiconVideoParams.builder().language("ua").build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .extracting(Site::getContent)
                .extracting(Content::getLanguage)
                .containsOnly("ua");
    }

    @Test
    public void makeHttpRequestsShouldMergeImpExtContextSearchAndSiteSearchAndCopyToRubiconImpExtRpTarget()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder.site(Site.builder().search("site search").build()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        final ObjectNode impExt = bidRequest.getImp().get(0).getExt();
        final ExtImpContext extImpContext = ExtImpContext.of(null);
        extImpContext.addProperty("search", new TextNode("imp ext search"));
        impExt.set("context", mapper.valueToTree(extImpContext));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(mapper.readTree("{\"search\":[\"site search\", \"imp ext search\"]}"));
    }

    @Test
    public void makeHttpRequestsShouldMergeImpExtContextDataAndSiteAttributesAndCopyToRubiconImpExtRpTarget() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder.site(Site.builder()
                        .sectioncat(singletonList("site sectioncat"))
                        .pagecat(singletonList("site pagecat"))
                        .page("site page")
                        .ref("site ref")
                        .search("site search")
                        .build()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        final ObjectNode impExtContextData = mapper.createObjectNode()
                .<ObjectNode>set("sectioncat", mapper.createArrayNode().add("imp ext sectioncat"))
                .<ObjectNode>set("pagecat", mapper.createArrayNode().add("imp ext pagecat"))
                .put("page", "imp ext page")
                .put("ref", "imp ext ref")
                .put("search", "imp ext search");

        final ObjectNode impExt = bidRequest.getImp().get(0).getExt();
        impExt.set("context", mapper.valueToTree(ExtImpContext.of(impExtContextData)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(mapper.createObjectNode()
                        .<ObjectNode>set("sectioncat", mapper.createArrayNode()
                                .add("site sectioncat")
                                .add("imp ext sectioncat"))
                        .<ObjectNode>set("pagecat", mapper.createArrayNode()
                                .add("site pagecat")
                                .add("imp ext pagecat"))
                        .<ObjectNode>set("page", mapper.createArrayNode()
                                .add("site page")
                                .add("imp ext page"))
                        .<ObjectNode>set("ref", mapper.createArrayNode()
                                .add("site ref")
                                .add("imp ext ref"))
                        .set("search", mapper.createArrayNode()
                                .add("site search")
                                .add("imp ext search")));
    }

    @Test
    public void makeHttpRequestsShouldOverridePbadslotIfPresentInRequestImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(), impBuilder -> impBuilder.video(Video.builder().build()), identity());

        final ObjectNode dataNode = mapper.createObjectNode()
                .set("pbadslot", TextNode.valueOf("pbadslotvalue"));

        bidRequest.getImp().get(0).getExt().set("data", dataNode);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(ext -> ext.at("/rp/target/pbadslot"))
                .containsExactly(TextNode.valueOf("pbadslotvalue"));
    }

    @Test
    public void makeHttpRequestsShouldOverrideDfpAdunitCodeIfAdslotPresentInImpExtDataAndAndAdserverNameIsGam() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(), impBuilder -> impBuilder.video(Video.builder().build()), identity());

        final ObjectNode dataNode =
                mapper.createObjectNode()
                        .set("adserver", mapper.createObjectNode()
                                .put("adslot", "adslotvalue")
                                .put("name", "gam"));

        bidRequest.getImp().get(0).getExt().set("data", dataNode);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(ext -> ext.at("/rp/target/dfp_ad_unit_code"))
                .containsExactly(TextNode.valueOf("adslotvalue"));
    }

    @Test
    public void makeHttpRequestsShouldOverrideDfpAdunitCodeIfAdslotPresentInImpExtContextDataAndAndAdserverNameIsGam() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(), impBuilder -> impBuilder.video(Video.builder().build()), identity());

        final ObjectNode dataNode =
                mapper.createObjectNode()
                        .set("adserver", mapper.createObjectNode()
                                .put("adslot", "adslotvalue")
                                .put("name", "gam"));

        bidRequest.getImp().get(0).getExt()
                .set("context", mapper.createObjectNode().set("data", dataNode));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(ext -> ext.at("/rp/target/dfp_ad_unit_code"))
                .containsExactly(TextNode.valueOf("adslotvalue"));
    }

    @Test
    public void makeHttpRequestsShouldMergeImpExtContextDataAndAppAttributesAndCopyToRubiconImpExtRpTarget() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder.app(App.builder()
                        .sectioncat(singletonList("app sectioncat"))
                        .pagecat(singletonList("app pagecat"))
                        .build()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        final ObjectNode impExtContextData = mapper.createObjectNode()
                .<ObjectNode>set("sectioncat", mapper.createArrayNode().add("imp ext sectioncat"))
                .set("pagecat", mapper.createArrayNode().add("imp ext pagecat"));

        final ObjectNode impExt = bidRequest.getImp().get(0).getExt();
        impExt.set("context", mapper.valueToTree(ExtImpContext.of(impExtContextData)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(mapper.createObjectNode()
                        .<ObjectNode>set("sectioncat", mapper.createArrayNode()
                                .add("app sectioncat")
                                .add("imp ext sectioncat"))
                        .<ObjectNode>set("pagecat", mapper.createArrayNode()
                                .add("app pagecat")
                                .add("imp ext pagecat")));
    }

    @Test
    public void makeHttpRequestsShouldCopySiteKeywords() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder.site(Site.builder().keywords("site keyword").build()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .extracting(Site::getKeywords)
                .containsOnly("site keyword");
    }

    @Test
    public void makeHttpRequestsShouldCopyAppKeywords() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder.app(App.builder().keywords("app keyword").build()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getApp)
                .extracting(App::getKeywords)
                .containsOnly("app keyword");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(builder -> builder.video(Video.builder().build())),
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize value");
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfNoImpFormat() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(builder -> builder.video(Video.builder().build())),
                        givenImp(builder -> builder.banner(Banner.builder()
                                .format(null).w(300).h(250)
                                .build()))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors())
                .containsOnly(BidderError.badInput("rubicon imps must have at least one imp.format element"));
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfNoValidSizes() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(builder -> builder.video(Video.builder().build())),
                        givenImp(builder -> builder.banner(Banner.builder()
                                .format(singletonList(Format.builder().w(123).h(456).build()))
                                .build()))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).containsOnly(BidderError.badInput("No valid sizes"));
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfSizeIdsNotFound() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpRubicon.builder()
                                .sizes(singletonList(3)).build())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Bad request.imp[].ext.rubicon.sizes"));
    }

    @Test
    public void makeHttpRequestsShouldProcessMetricsAndFillViewabilityVendor() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.video(Video.builder().build())
                        .metric(asList(Metric.builder().vendor("somebody").type("viewability").value(0.9f).build(),
                                Metric.builder().vendor("moat").type("viewability").value(0.3f).build(),
                                Metric.builder().vendor("comscore").type("unsupported").value(0.5f).build(),
                                Metric.builder().vendor("activeview").type("viewability").value(0.6f).build(),
                                Metric.builder().vendor("somebody").type("unsupported").value(0.7f).build())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .flatExtracting(Imp::getMetric).doesNotContainNull()
                .containsOnly(Metric.builder().type("viewability").value(0.9f).vendor("somebody").build(),
                        Metric.builder().type("viewability").value(0.3f).vendor("seller-declared").build(),
                        Metric.builder().type("unsupported").value(0.5f).vendor("comscore").build(),
                        Metric.builder().type("viewability").value(0.6f).vendor("seller-declared").build(),
                        Metric.builder().type("unsupported").value(0.7f).vendor("somebody").build());

        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getExt).doesNotContainNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconImpExt.class))
                .containsOnly(RubiconImpExt.of(RubiconImpExtRp.of(null, null,
                        RubiconImpExtRpTrack.of("", "")), asList("moat.com", "doubleclickbygoogle.com"), 1, null));
    }

    @Test
    public void makeHttpRequestsShouldCreateSourceWithPchainIfDefinedInImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.video(Video.builder().build()),
                builder -> builder.pchain("pchain"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSource).doesNotContainNull()
                .extracting(Source::getPchain)
                .containsOnly("pchain");
    }

    @Test
    public void makeHttpRequestsShouldUpdateSourceWithPchainIfDefinedInImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.source(Source.builder().tid("tid").build()),
                builder -> builder.video(Video.builder().build()),
                builder -> builder.pchain("pchain"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSource).doesNotContainNull()
                .extracting(Source::getPchain)
                .containsOnly("pchain");
    }

    @Test
    public void makeBidsShouldTolerateMismatchedBidImpId() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.id("impId")),
                givenBidResponse(bidBuilder -> bidBuilder.price(ONE).impid("mismatched_impId")));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnOnlyLineItemRequestsWithExpectedFieldsWhenImpPmpDealsArePresent()
            throws IOException {
        // given
        final List<Deal> dealsList = asList(
                Deal.builder().ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of(null, "123",
                                singletonList(Format.builder().w(120).h(600).build()), null))))
                        .build(),
                Deal.builder().ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of(null, "234",
                                singletonList(Format.builder().w(300).h(250).build()), null))))
                        .build());

        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        // request for banner should be ignored, should make two requests for deals instead
                        .banner(Banner.builder().format(singletonList(Format.builder().w(468).h(60).build())).build())
                        .pmp(Pmp.builder().deals(dealsList).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        assertThat(result.getValue()).hasSize(2)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(2)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .containsOnly(Format.builder().w(120).h(600).build(), Format.builder().w(300).h(250).build());

        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(imp -> mapper.treeToValue(imp.getExt(), RubiconImpExt.class).getRp().getTarget())
                .containsOnly(
                        mapper.readTree("{\"line_item\":\"123\"}"),
                        mapper.readTree("{\"line_item\":\"234\"}"));
    }

    @Test
    public void makeBidsShouldSetSeatBuyerToMetaNetworkId() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(RubiconBidResponse.builder()
                        .cur("USD")
                        .seatbid(singletonList(RubiconSeatBid.builder()
                                .buyer("123")
                                .bid(singletonList(givenBid(bid -> bid.price(ONE))))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        final ObjectNode expectedBidExt = mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder()
                        .meta(mapper.createObjectNode().set("networkId", IntNode.valueOf(123)))
                        .build(), null));
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .containsExactly(expectedBidExt);
    }

    @Test
    public void makeBidsShouldNotSetNegativeSeatBuyerToMetaNetworkId() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(RubiconBidResponse.builder()
                        .cur("USD")
                        .seatbid(singletonList(RubiconSeatBid.builder()
                                .buyer("-123")
                                .bid(singletonList(givenBid(bid -> bid.price(ONE))))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .containsNull();
    }

    @Test
    public void makeBidsShouldNotSetZeroSeatBuyerToMetaNetworkId() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(RubiconBidResponse.builder()
                        .cur("USD")
                        .seatbid(singletonList(RubiconSeatBid.builder()
                                .buyer("0")
                                .bid(singletonList(givenBid(bid -> bid.price(ONE))))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .containsNull();
    }

    @Test
    public void makeBidsShouldTolerateWithNotParsibleBuyerValue() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(RubiconBidResponse.builder()
                        .cur("USD")
                        .seatbid(singletonList(RubiconSeatBid.builder()
                                .buyer("canNotBeParsed")
                                .bid(singletonList(givenBid(bid -> bid.price(ONE))))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .containsNull();
    }

    @Test
    public void makeBidsShouldTolerateWithNotIntegerBuyerValue() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(RubiconBidResponse.builder()
                        .cur("USD")
                        .seatbid(singletonList(RubiconSeatBid.builder()
                                .buyer("123.2")
                                .bid(singletonList(givenBid(bid -> bid.price(ONE))))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .containsNull();
    }

    @Test
    public void makeBidsShouldRemoveBidsWithNotParsedExtAndAddError() throws JsonProcessingException {
        // given
        final ObjectNode invalidBidExt = mapper.createObjectNode();
        invalidBidExt.set("prebid", mapper.createArrayNode());
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(RubiconBidResponse.builder()
                        .cur("USD")
                        .seatbid(singletonList(RubiconSeatBid.builder()
                                .buyer("123")
                                .bid(singletonList(givenBid(bid -> bid.id("789").price(ONE).ext(invalidBidExt))))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Invalid ext passed in bid with id: 789"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfRequestImpHasNoVideo() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), givenBidResponse(ONE));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().price(ONE).build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfRequestImpHasBannerAndVideoButNoRequiredVideoFieldsPresent()
            throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(builder -> builder
                        .banner(Banner.builder().build())
                        .video(Video.builder().build())),
                givenBidResponse(ONE));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().price(ONE).build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfRequestImpHasBannerAndVideoButAllRequiredVideoFieldsPresent()
            throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(builder -> builder
                        .banner(Banner.builder().build())
                        .video(Video.builder().mimes(singletonList("mime1")).protocols(singletonList(1))
                                .maxduration(60).linearity(2).api(singletonList(3)).build())),
                givenBidResponse(ONE));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().price(ONE).build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfRequestImpHasVideo() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(builder -> builder.video(Video.builder().build())),
                givenBidResponse(ONE));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().price(ONE).build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldNotReduceBidderAmountForBidsWithSameImpId() throws JsonProcessingException {
        // given
        final Bid firstBid = Bid.builder()
                .id("firstBidId")
                .impid("impId")
                .price(ONE)
                .build();

        final Bid secondBid = Bid.builder()
                .id("secondBidId")
                .impid("impId")
                .price(ONE)
                .build();

        final String bidResponse = mapper.writeValueAsString(RubiconBidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(RubiconSeatBid.builder()
                        .bid(Arrays.asList(firstBid, secondBid))
                        .build()))
                .build());

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(builder -> builder.video(Video.builder().build())),
                bidResponse);
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("impId").video(Video.builder().build()));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsExactlyInAnyOrder("firstBidId", "secondBidId");
    }

    @Test
    public void makeBidsShouldReturnBidWithOverriddenCpmFromRequest() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                givenBidResponse(TEN));

        final ExtRequest extBidRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .bidders(mapper.valueToTree(ExtPrebidBidders.of(
                        mapper.createObjectNode().set("debug",
                                mapper.createObjectNode().put("cpmoverride", 5.55)))))
                .build());

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(
                builder -> builder.ext(extBidRequest),
                identity(),
                identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsOnly(BigDecimal.valueOf(5.55));
    }

    @Test
    public void makeBidsShouldReturnBidWithOverriddenCpmFromImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                givenBidResponse(TEN));

        final ExtRequest extBidRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .bidders(mapper.valueToTree(ExtPrebidBidders.of(
                        mapper.createObjectNode().set("debug",
                                mapper.createObjectNode().put("cpmoverride", 5.55))))) // will be ignored
                .build());

        final ExtPrebid<Void, ExtImpRubicon> extImp = ExtPrebid.of(
                null,
                ExtImpRubicon.builder()
                        .debug(ExtImpRubiconDebug.of(4.44f))
                        .build());

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(
                builder -> builder.ext(extBidRequest),
                builder -> builder.ext(mapper.valueToTree(extImp)),
                identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsOnly(BigDecimal.valueOf(4.44));
    }

    @Test
    public void makeBidsShouldReturnBidWithBidIdFieldFromBidResponseIfZero() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(RubiconBidResponse.builder()
                        .bidid("bidid1") // returned bidid from XAPI
                        .seatbid(singletonList(RubiconSeatBid.builder()
                                .bid(singletonList(Bid.builder().id("0").price(ONE).build()))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().id("bidid1").price(ONE).build(), banner, null));
    }

    @Test
    public void makeBidsShouldReturnBidWithOriginalBidIdFieldFromBidResponseIfNotZero() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(RubiconBidResponse.builder()
                        .bidid("bidid1") // returned bidid from XAPI
                        .seatbid(singletonList(RubiconSeatBid.builder()
                                .bid(singletonList(Bid.builder().id("non-zero").price(ONE).build()))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().id("non-zero").price(ONE).build(), banner, null));
    }

    @Test
    public void makeBidsShouldReturnBidWithRandomlyGeneratedId() throws JsonProcessingException {
        // given
        rubiconBidder = new RubiconBidder(
                ENDPOINT_URL, USERNAME, PASSWORD, SUPPORTED_VENDORS, true, currencyConversionService, jacksonMapper);

        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(RubiconBidResponse.builder()
                        .seatbid(singletonList(RubiconSeatBid.builder()
                                .bid(singletonList(Bid.builder().id("bidid1").price(ONE).build()))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .doesNotContainNull()
                .doesNotContain("bidid1");
    }

    @Test
    public void makeBidsShouldReturnBidWithCurrencyFromBidResponse() throws JsonProcessingException {
        // given
        rubiconBidder = new RubiconBidder(
                ENDPOINT_URL, USERNAME, PASSWORD, SUPPORTED_VENDORS, true, currencyConversionService, jacksonMapper);

        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                mapper.writeValueAsString(RubiconBidResponse.builder()
                        .cur("EUR")
                        .seatbid(singletonList(RubiconSeatBid.builder()
                                .bid(singletonList(Bid.builder().id("bidid1").price(ONE).build()))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBidCurrency)
                .containsOnly("EUR");
    }

    @Test
    public void makeBidsShouldReturnBidWithDchainFromRequest() throws JsonProcessingException {
        // given
        final ObjectNode requestNode = mapper.valueToTree(ExtBidPrebid.builder()
                .meta(mapper.createObjectNode().set("dChain", TextNode.valueOf("dChain")))
                .build());
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(RubiconBidResponse.builder()
                        .seatbid(singletonList(RubiconSeatBid.builder()
                                .bid(singletonList(givenBid(bid -> bid.ext(requestNode).price(ONE))))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .containsExactly(requestNode);
    }

    @Test
    public void extractTargetingShouldReturnEmptyMapForEmptyExtension() {
        // when and then
        assertThat(rubiconBidder.extractTargeting(mapper.createObjectNode())).isEmpty();
    }

    @Test
    public void extractTargetingShouldReturnEmptyMapForInvalidExtension() {
        // when and then
        assertThat(rubiconBidder.extractTargeting(mapper.createObjectNode().put("rp", 1))).isEmpty();
        assertThat(rubiconBidder.extractTargeting(mapper.createObjectNode().putObject("rp").put("targeting", 1)))
                .isEmpty();
    }

    @Test
    public void extractTargetingShouldReturnEmptyMapForNullRp() {
        // when and then
        assertThat(rubiconBidder.extractTargeting(mapper.createObjectNode().putObject("rp"))).isEmpty();
    }

    @Test
    public void extractTargetingShouldReturnEmptyMapForNullTargeting() {
        // when and then
        assertThat(rubiconBidder.extractTargeting(mapper.createObjectNode().putObject("rp").putObject("targeting")))
                .isEmpty();
    }

    @Test
    public void extractTargetingShouldIgnoreEmptyTargetingValuesList() {
        // given
        final ObjectNode extBidBidder = mapper.valueToTree(RubiconTargetingExt.of(
                RubiconTargetingExtRp.of(singletonList(RubiconTargeting.of("rpfl_1001", emptyList())))));

        // when and then
        assertThat(rubiconBidder.extractTargeting(extBidBidder)).isEmpty();
    }

    @Test
    public void extractTargetingShouldReturnNotEmptyTargetingMap() {
        // given
        final ObjectNode extBidBidder = mapper.valueToTree(RubiconTargetingExt.of(
                RubiconTargetingExtRp.of(singletonList(
                        RubiconTargeting.of("rpfl_1001", asList("2_tier0100", "3_tier0100"))))));

        // when and then
        assertThat(rubiconBidder.extractTargeting(extBidBidder)).containsOnly(entry("rpfl_1001", "2_tier0100"));
    }

    private static BidRequest givenBidRequest(Function<BidRequestBuilder, BidRequestBuilder> bidRequestCustomizer,
                                              Function<ImpBuilder, ImpBuilder> impCustomizer,
                                              Function<ExtImpRubiconBuilder, ExtImpRubiconBuilder> extCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer, extCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<ImpBuilder, ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer, identity());
    }

    private static BidRequest givenBidRequest(Function<ImpBuilder, ImpBuilder> impCustomizer,
                                              Function<ExtImpRubiconBuilder, ExtImpRubiconBuilder> extCustomizer) {
        return givenBidRequest(identity(), impCustomizer, extCustomizer);
    }

    private static Imp givenImp(Function<ImpBuilder, ImpBuilder> impCustomizer,
                                Function<ExtImpRubiconBuilder, ExtImpRubiconBuilder> extCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(
                                null, extCustomizer.apply(ExtImpRubicon.builder()).build()))))
                .build();
    }

    private static Imp givenImp(Function<ImpBuilder, ImpBuilder> impCustomizer) {
        return givenImp(impCustomizer, identity());
    }

    private static Data givenDataWithSegmentEntry(Integer segtax, String segmentId) {
        return Data.builder()
                .segment(singletonList(Segment.builder().id(segmentId).build()))
                .ext(mapper.createObjectNode().put("segtax", segtax))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static String givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer)
            throws JsonProcessingException {
        return mapper.writeValueAsString(RubiconBidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(RubiconSeatBid.builder()
                        .bid(singletonList(givenBid(bidCustomizer)))
                        .build()))
                .build());
    }

    private static String givenBidResponse(BigDecimal price) throws JsonProcessingException {
        return givenBidResponse(bidBuilder -> bidBuilder.price(price));
    }

    private static Bid givenBid(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder()).build();
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class Inventory {

        List<String> rating;

        List<String> prodtype;
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class Visitor {

        List<String> ucat;

        List<String> search;
    }
}
