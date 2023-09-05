package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.huaweiads.model.AdsType;
import org.prebid.server.bidder.huaweiads.model.request.AdSlot30;
import org.prebid.server.bidder.huaweiads.model.request.App;
import org.prebid.server.bidder.huaweiads.model.request.Device;
import org.prebid.server.bidder.huaweiads.model.request.Geo;
import org.prebid.server.bidder.huaweiads.model.request.HuaweiAdsRequest;
import org.prebid.server.bidder.huaweiads.model.request.Network;
import org.prebid.server.bidder.huaweiads.model.request.Regs;
import org.prebid.server.bidder.huaweiads.model.response.Ad30;
import org.prebid.server.bidder.huaweiads.model.response.Content;
import org.prebid.server.bidder.huaweiads.model.response.HuaweiAdm;
import org.prebid.server.bidder.huaweiads.model.response.HuaweiAdsResponse;
import org.prebid.server.bidder.huaweiads.model.response.Monitor;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuaweiAds;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import javax.crypto.Mac;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.prebid.server.bidder.model.BidderError.badInput;
import static org.prebid.server.bidder.model.BidderError.badServerResponse;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.AUTHORIZATION_HEADER;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.prebid.server.util.HttpUtil.USER_AGENT_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class HuaweiAdsBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test-url.org/";
    private static final String CHINESE_ENDPOINT_URL = "https://test-url.org/china";
    private static final String EUROPEAN_ENDPOINT_URL = "https://test-url.org/europe";
    private static final String RUSSIAN_ENDPOINT_URL = "https://test-url.orc/russia";
    private static final String ASIAN_ENDPOINT_URL = "https://test-url.org/asian";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();
    @Mock
    private HuaweiAdSlotBuilder adSlotBuilder;
    @Mock
    private HuaweiAppBuilder appBuilder;
    @Mock
    private HuaweiDeviceBuilder deviceBuilder;
    @Mock
    private HuaweiNetworkBuilder networkBuilder;
    @Mock
    private HuaweiAdmBuilder admBuilder;

    private HuaweiAdsBidder target;

    private App defaultApp;
    private Device defaultDevice;
    private Network defaultNetwork;

    @Before
    public void setUp() {
        target = new HuaweiAdsBidder(
                ENDPOINT_URL,
                CHINESE_ENDPOINT_URL,
                RUSSIAN_ENDPOINT_URL,
                EUROPEAN_ENDPOINT_URL,
                ASIAN_ENDPOINT_URL,
                StringUtils.EMPTY,
                jacksonMapper,
                adSlotBuilder,
                appBuilder,
                deviceBuilder,
                networkBuilder,
                admBuilder);

        given(adSlotBuilder.build(any(Imp.class), any(ExtImpHuaweiAds.class)))
                .willAnswer(invocation -> AdSlot30.builder()
                        .slotId(((ExtImpHuaweiAds) invocation.getArgument(1)).getSlotId())
                        .build());
        defaultApp = App.builder().build();
        given(appBuilder.build(any(com.iab.openrtb.request.App.class), anyString()))
                .willReturn(defaultApp);
        defaultDevice = Device.builder().build();
        given(deviceBuilder.build(any(com.iab.openrtb.request.Device.class), any(User.class), anyString()))
                .willReturn(defaultDevice);
        defaultNetwork = Network.builder().build();
        given(networkBuilder.build(any(com.iab.openrtb.request.Device.class)))
                .willReturn(defaultNetwork);
        given(admBuilder.buildBanner(any(AdsType.class), any(Content.class)))
                .willReturn(HuaweiAdm.of("banner", 100, 100));
        given(admBuilder.buildVideo(any(AdsType.class), any(Content.class), any(Video.class)))
                .willReturn(HuaweiAdm.of("video", 200, 200));
        given(admBuilder.buildNative(any(AdsType.class), any(Content.class), any(Native.class)))
                .willReturn(HuaweiAdm.of("native", 300, 300));
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HuaweiAdsBidder(
                        "invalid_url",
                        CHINESE_ENDPOINT_URL,
                        RUSSIAN_ENDPOINT_URL,
                        EUROPEAN_ENDPOINT_URL,
                        ASIAN_ENDPOINT_URL,
                        StringUtils.EMPTY,
                        jacksonMapper,
                        adSlotBuilder,
                        appBuilder,
                        deviceBuilder,
                        networkBuilder,
                        admBuilder));
    }

    @Test
    public void creationShouldFailOnInvalidChineseEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HuaweiAdsBidder(
                        ENDPOINT_URL,
                        "invalid_url",
                        RUSSIAN_ENDPOINT_URL,
                        EUROPEAN_ENDPOINT_URL,
                        ASIAN_ENDPOINT_URL,
                        StringUtils.EMPTY,
                        jacksonMapper,
                        adSlotBuilder,
                        appBuilder,
                        deviceBuilder,
                        networkBuilder,
                        admBuilder));
    }

    @Test
    public void creationShouldFailOnInvalidRussianEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HuaweiAdsBidder(
                        ENDPOINT_URL,
                        CHINESE_ENDPOINT_URL,
                        "invalid_url",
                        EUROPEAN_ENDPOINT_URL,
                        ASIAN_ENDPOINT_URL,
                        StringUtils.EMPTY,
                        jacksonMapper,
                        adSlotBuilder,
                        appBuilder,
                        deviceBuilder,
                        networkBuilder,
                        admBuilder));
    }

    @Test
    public void creationShouldFailOnInvalidEuropeanEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HuaweiAdsBidder(
                        ENDPOINT_URL,
                        CHINESE_ENDPOINT_URL,
                        RUSSIAN_ENDPOINT_URL,
                        "invalid_url",
                        ASIAN_ENDPOINT_URL,
                        StringUtils.EMPTY,
                        jacksonMapper,
                        adSlotBuilder,
                        appBuilder,
                        deviceBuilder,
                        networkBuilder,
                        admBuilder));
    }

    @Test
    public void creationShouldFailOnInvalidAsianEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HuaweiAdsBidder(
                        ENDPOINT_URL,
                        CHINESE_ENDPOINT_URL,
                        RUSSIAN_ENDPOINT_URL,
                        EUROPEAN_ENDPOINT_URL,
                        "invalid_url",
                        StringUtils.EMPTY,
                        jacksonMapper,
                        adSlotBuilder,
                        appBuilder,
                        deviceBuilder,
                        networkBuilder,
                        admBuilder));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCannotBeParsed() {
        // given
        final ObjectNode invalidImpExt = mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidImpExt));

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).first()
                .satisfies(error -> {
                    assertThat(error.getMessage()).startsWith("Cannot deserialize");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                });
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtHasNullBidder() {
        // given
        final ObjectNode invalidImpExt = mapper.valueToTree(ExtPrebid.of(null, null));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidImpExt));

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(badInput("ExtImpHuaweiAds is null."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpressionDoesNotHaveSlotId() {
        // given
        final ObjectNode invalidExtImp = givenImpExt(extImpBuilder -> extImpBuilder.slotId(null));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidExtImp));

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(badInput("ExtImpHuaweiAds: slotid is empty."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpressionDoesNotHaveAdType() {
        // given
        final ObjectNode invalidExtImp = givenImpExt(extImpBuilder -> extImpBuilder.adType(null));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidExtImp));

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(badInput("ExtImpHuaweiAds: adtype is empty."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpressionDoesNotHavePublisherId() {
        // given
        final ObjectNode invalidExtImp = givenImpExt(extImpBuilder -> extImpBuilder.publisherId(null));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidExtImp));

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(badInput("ExtImpHuaweiAds: publisherid is empty."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpressionDoesNotHaveSignKey() {
        // given
        final ObjectNode invalidExtImp = givenImpExt(extImpBuilder -> extImpBuilder.signKey(null));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidExtImp));

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(badInput("ExtImpHuaweiAds: signkey is empty."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpressionDoesNotHaveKeyId() {
        // given
        final ObjectNode invalidExtImp = givenImpExt(extImpBuilder -> extImpBuilder.keyId(null));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidExtImp));

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(badInput("ExtImpHuaweiAds: keyid is empty."));
    }

    @Test
    public void makeHttpRequestsShouldReturnRegsWhenCoppaIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity()).toBuilder()
                .regs(com.iab.openrtb.request.Regs.builder().coppa(1).build())
                .build();

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final HuaweiAdsRequest expectedRequest = HuaweiAdsRequest.builder()
                .clientAdRequestId("bid_request_id")
                .multislot(List.of(AdSlot30.builder().slotId("slotId").build()))
                .regs(Regs.of(1))
                .version("3.4")
                .build();

        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getImpIds())
                        .isEqualTo(Set.of("imp_id")))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedRequest))
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedRequest)));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRegsWhenCoppaIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity()).toBuilder()
                .regs(com.iab.openrtb.request.Regs.builder().build())
                .build();

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final HuaweiAdsRequest expectedRequest = HuaweiAdsRequest.builder()
                .clientAdRequestId("bid_request_id")
                .multislot(List.of(AdSlot30.builder().slotId("slotId").build()))
                .version("3.4")
                .build();

        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getImpIds())
                        .isEqualTo(Set.of("imp_id")))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedRequest))
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedRequest)));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnGeoWhenDeviceGeoIsPresent() {
        // given
        final com.iab.openrtb.request.Geo geo = com.iab.openrtb.request.Geo.builder()
                .lon(10.1f)
                .lat(10.2f)
                .accuracy(0)
                .lastfix(1)
                .build();
        final BidRequest bidRequest = givenBidRequest(identity()).toBuilder()
                .device(com.iab.openrtb.request.Device.builder().geo(geo).build())
                .build();

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final HuaweiAdsRequest expectedRequest = HuaweiAdsRequest.builder()
                .clientAdRequestId("bid_request_id")
                .multislot(List.of(AdSlot30.builder().slotId("slotId").build()))
                .geo(Geo.of(10.1f, 10.2f, 0, 1))
                .device(defaultDevice)
                .network(defaultNetwork)
                .version("3.4")
                .build();

        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getImpIds())
                        .isEqualTo(Set.of("imp_id")))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedRequest))
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedRequest)));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldNotReturnGeoWhenDeviceGeoIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity()).toBuilder()
                .device(com.iab.openrtb.request.Device.builder().geo(null).build())
                .build();

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final HuaweiAdsRequest expectedRequest = HuaweiAdsRequest.builder()
                .clientAdRequestId("bid_request_id")
                .multislot(List.of(AdSlot30.builder().slotId("slotId").build()))
                .device(defaultDevice)
                .network(defaultNetwork)
                .version("3.4")
                .build();

        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getImpIds())
                        .isEqualTo(Set.of("imp_id")))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedRequest))
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedRequest)));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnConsentWhenUserExtConsentIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity()).toBuilder()
                .user(User.builder().ext(ExtUser.builder().consent("consent").build()).build())
                .build();

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final HuaweiAdsRequest expectedRequest = HuaweiAdsRequest.builder()
                .clientAdRequestId("bid_request_id")
                .multislot(List.of(AdSlot30.builder().slotId("slotId").build()))
                .consent("consent")
                .version("3.4")
                .build();

        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getImpIds())
                        .isEqualTo(Set.of("imp_id")))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedRequest))
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedRequest)));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldNotReturnConsentWhenUserExtIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity()).toBuilder()
                .user(User.builder().ext(null).build())
                .build();

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final HuaweiAdsRequest expectedRequest = HuaweiAdsRequest.builder()
                .clientAdRequestId("bid_request_id")
                .multislot(List.of(AdSlot30.builder().slotId("slotId").build()))
                .version("3.4")
                .build();

        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getImpIds())
                        .isEqualTo(Set.of("imp_id")))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedRequest))
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedRequest)));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnAllImpIdsAndCorrespondingAdSlots() {
        //given
        final ObjectNode videoImpExt = givenImpExt(extBuilder -> extBuilder.slotId("video_slot_id").adType("roll"));
        final ObjectNode bannerImpExt = givenImpExt(extBuilder -> extBuilder.slotId("banner_slot_id").adType("banner"));
        final ObjectNode nativeImpExt = givenImpExt(extBuilder -> extBuilder.slotId("native_slot_id").adType("native"));
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("imp_video_id").video(Video.builder().build()).ext(videoImpExt),
                impBuilder -> impBuilder.id("imp_banner_id").banner(Banner.builder().build()).ext(bannerImpExt),
                impBuilder -> impBuilder.id("imp_native_id").xNative(Native.builder().build()).ext(nativeImpExt));

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final HuaweiAdsRequest expectedRequest = HuaweiAdsRequest.builder()
                .clientAdRequestId("bid_request_id")
                .multislot(List.of(
                        AdSlot30.builder().slotId("video_slot_id").build(),
                        AdSlot30.builder().slotId("banner_slot_id").build(),
                        AdSlot30.builder().slotId("native_slot_id").build()))
                .version("3.4")
                .build();

        verify(adSlotBuilder, times(3)).build(any(Imp.class), any(ExtImpHuaweiAds.class));

        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getImpIds())
                        .isEqualTo(Set.of("imp_video_id", "imp_banner_id", "imp_native_id")))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedRequest))
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedRequest)));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnAppDeviceAndNetworkWhenAppDeviceAndUserArePresent() {
        //given
        final com.iab.openrtb.request.App app = com.iab.openrtb.request.App.builder().build();
        final com.iab.openrtb.request.Device device = com.iab.openrtb.request.Device.builder().build();
        final User user = User.builder().build();
        final BidRequest bidRequest = givenBidRequest(identity())
                .toBuilder()
                .app(app)
                .device(device)
                .user(user)
                .build();

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final HuaweiAdsRequest expectedRequest = HuaweiAdsRequest.builder()
                .clientAdRequestId("bid_request_id")
                .multislot(List.of(AdSlot30.builder().slotId("slotId").build()))
                .app(defaultApp)
                .device(defaultDevice)
                .network(defaultNetwork)
                .version("3.4")
                .build();

        verify(appBuilder).build(app, "ZA");
        verify(deviceBuilder).build(device, user, "ZA");
        verify(networkBuilder).build(device);

        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getImpIds())
                        .isEqualTo(Set.of("imp_id")))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedRequest))
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedRequest)));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnDefaultEndpointWhenRequestTreatedAsFromCloseCountry() {
        // given
        target = new HuaweiAdsBidder(
                ENDPOINT_URL,
                CHINESE_ENDPOINT_URL,
                RUSSIAN_ENDPOINT_URL,
                EUROPEAN_ENDPOINT_URL,
                ASIAN_ENDPOINT_URL,
                "1",
                jacksonMapper,
                adSlotBuilder,
                appBuilder,
                deviceBuilder,
                networkBuilder, admBuilder);

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(givenBidRequest(identity()));

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getUri()).isEqualTo(ENDPOINT_URL));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnChineseEndpointWhenCountryComesFromChina() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity())
                .toBuilder()
                .device(com.iab.openrtb.request.Device.builder()
                        .geo(com.iab.openrtb.request.Geo.builder().country("CHN").build())
                        .build())
                .build();

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getUri()).isEqualTo(CHINESE_ENDPOINT_URL));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRussianEndpointWhenCountryComesFromrussia() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity())
                .toBuilder()
                .device(com.iab.openrtb.request.Device.builder()
                        .geo(com.iab.openrtb.request.Geo.builder().country("RUS").build())
                        .build())
                .build();

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getUri()).isEqualTo(RUSSIAN_ENDPOINT_URL));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnEuropeanEndpointWhenCountryComesFromUkraine() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity())
                .toBuilder()
                .device(com.iab.openrtb.request.Device.builder()
                        .geo(com.iab.openrtb.request.Geo.builder().country("UKR").build())
                        .build())
                .build();

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getUri()).isEqualTo(EUROPEAN_ENDPOINT_URL));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnAsianEndpointWhenCountryComesFromJapan() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity())
                .toBuilder()
                .device(com.iab.openrtb.request.Device.builder()
                        .geo(com.iab.openrtb.request.Geo.builder().country("JPN").build())
                        .build())
                .build();

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getUri()).isEqualTo(ASIAN_ENDPOINT_URL));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnAllHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity())
                .toBuilder()
                .device(com.iab.openrtb.request.Device.builder().ua("ua").build())
                .build();

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final Pattern authorizationPattern = Pattern.compile("^Digest username=publisherId,"
                + "realm=ppsadx/getResult,"
                + "nonce=(.*),"
                + "response=(.*),"
                + "algorithm=HmacSHA256,"
                + "usertype=1,"
                + "keyid=keyId$");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE))
                .satisfies(headers -> assertThat(headers.get(USER_AGENT_HEADER))
                        .isEqualTo("ua"))
                .satisfies(headers -> {
                    final Matcher matcher = authorizationPattern.matcher(headers.get(AUTHORIZATION_HEADER));
                    if (matcher.matches()) {
                        final String actualNonce = matcher.group(1);
                        final String actualResponse = matcher.group(2);
                        assertThat(actualNonce).isNotNull();
                        final String expectedMessage = actualNonce + ":POST:/ppsadx/getResult";
                        final String expectedKey = "publisherId:ppsadx/getResult:signKey";
                        assertThat(actualResponse).isEqualTo(encrypt(expectedMessage, expectedKey));
                    } else {
                        Assertions.fail("Authorization Header is incorrect");
                    }
                });
    }

    @Test
    public void makeHttpRequestsShouldNotReturnUserAgentHeaderWhenDeviceIsAbsence() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<HuaweiAdsRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(USER_AGENT_HEADER)).isNull());
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseCanNotBeParsed() {
        // given
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).containsExactly(badServerResponse("Bad Server Response"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseHas201Retcode() {
        // given
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder().retcode(201).reason("some reason").build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors())
                .containsExactly(badServerResponse("HuaweiAdsResponse retcode: 201 , reason: some reason"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseHas299Retcode() {
        // given
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder().retcode(299).reason("some reason").build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors())
                .containsExactly(badServerResponse("HuaweiAdsResponse retcode: 299 , reason: some reason"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseHas400Retcode() {
        // given
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder().retcode(400).reason("some reason").build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors())
                .containsExactly(badServerResponse("HuaweiAdsResponse retcode: 400 , reason: some reason"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseHas599Retcode() {
        // given
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder().retcode(599).reason("some reason").build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors())
                .containsExactly(badServerResponse("HuaweiAdsResponse retcode: 599 , reason: some reason"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseDoesNotHaveAds() {
        // given
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder()
                .retcode(200)
                .multiad(Collections.emptyList())
                .build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).containsExactly(badServerResponse("convert huaweiads response to bidder "
                + "response failed: multiad length is 0, get no ads from huawei side."));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidRequestDoesNotHaveImpressions() {
        // given
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder()
                .retcode(204)
                .multiad(List.of(Ad30.builder().build()))
                .build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);
        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).containsExactly(badServerResponse("convert huaweiads response to bidder "
                + "response failed: openRTBRequest.imp is empty"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpExtCannotBeParsed() {
        // given
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder()
                .retcode(206)
                .multiad(List.of(Ad30.builder().build()))
                .build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);
        final ObjectNode invalidImpExt = mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidImpExt));

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(actual.getErrors()).hasSize(1).first()
                .satisfies(error -> {
                    assertThat(error.getMessage()).startsWith("Cannot deserialize");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                });
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpExtHasNullBidder() {
        // given
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder()
                .retcode(301)
                .multiad(List.of(Ad30.builder().build()))
                .build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);
        final ObjectNode invalidImpExt = mapper.valueToTree(ExtPrebid.of(null, null));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidImpExt));

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).containsExactly(badServerResponse("ExtImpHuaweiAds is null."));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpressionDoesNotHaveSlotId() {
        // given
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder()
                .retcode(399)
                .multiad(List.of(Ad30.builder().build()))
                .build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);
        final ObjectNode invalidExtImp = givenImpExt(extImpBuilder -> extImpBuilder.slotId(null));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidExtImp));

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).containsExactly(badServerResponse("ExtImpHuaweiAds: slotid is empty."));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpressionDoesNotHaveAdType() {
        // given
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder()
                .retcode(600)
                .multiad(List.of(Ad30.builder().build()))
                .build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);
        final ObjectNode invalidExtImp = givenImpExt(extImpBuilder -> extImpBuilder.adType(null));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidExtImp));

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).containsExactly(badServerResponse("ExtImpHuaweiAds: adtype is empty."));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpressionDoesNotHavePublisherId() {
        // given
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder()
                .retcode(200)
                .multiad(List.of(Ad30.builder().build()))
                .build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);
        final ObjectNode invalidExtImp = givenImpExt(extImpBuilder -> extImpBuilder.publisherId(null));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidExtImp));

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).containsExactly(badServerResponse("ExtImpHuaweiAds: publisherid is empty."));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpressionDoesNotHaveSignKey() {
        // given
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder()
                .retcode(200)
                .multiad(List.of(Ad30.builder().build()))
                .build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);
        final ObjectNode invalidExtImp = givenImpExt(extImpBuilder -> extImpBuilder.signKey(null));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidExtImp));

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).containsExactly(badServerResponse("ExtImpHuaweiAds: signkey is empty."));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpressionDoesNotHaveKeyId() {
        // given
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder()
                .retcode(200)
                .multiad(List.of(Ad30.builder().build()))
                .build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);
        final ObjectNode invalidExtImp = givenImpExt(extImpBuilder -> extImpBuilder.keyId(null));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidExtImp));

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).containsExactly(badServerResponse("ExtImpHuaweiAds: keyid is empty."));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenAdHasNullContent() {
        // given
        final List<Content> contents = new ArrayList<>();
        contents.add(null);
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder()
                .retcode(200)
                .multiad(List.of(Ad30.builder()
                        .slotId("slotId")
                        .retCode(200)
                        .contentList(contents)
                        .build()))
                .build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).containsExactly(badServerResponse("extract Adm failed: content is empty"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMediaTypeIsAudio() {
        // given
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder()
                .retcode(200)
                .multiad(List.of(Ad30.builder()
                        .slotId("slotId")
                        .retCode(200)
                        .contentList(List.of(Content.builder().build()))
                        .build()))
                .build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.audio(Audio.builder().build()));

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).containsExactly(badServerResponse("no support bidtype: audio"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidWithCurrencyWhenAdIsBanner() {
        // given
        final Content content = Content.builder()
                .contentId("contentId")
                .price(BigDecimal.TEN)
                .cur("USD")
                .build();
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder()
                .retcode(200)
                .multiad(List.of(Ad30.builder()
                        .adType(8)
                        .slotId("slotId")
                        .retCode(200)
                        .contentList(List.of(content))
                        .build()))
                .build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.banner(Banner.builder().build()));

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, bidRequest);

        // then
        verify(admBuilder).buildBanner(AdsType.BANNER, content);
        final Bid expectedBid = Bid.builder()
                .id("imp_id")
                .impid("imp_id")
                .price(BigDecimal.TEN)
                .crid("contentId")
                .adm("banner")
                .w(100)
                .h(100)
                .adomain(List.of("huaweiads"))
                .nurl("")
                .build();
        assertThat(actual.getValue()).containsOnly(BidderBid.of(expectedBid, banner, "USD"));
        assertThat(actual.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnVideoBidWithDefaultCurrencyWhenAdIsVideoAndContentDoesNotHaveCurrency() {
        // given
        final Content content = Content.builder()
                .contentId("contentId")
                .price(BigDecimal.TEN)
                .build();
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder()
                .retcode(200)
                .multiad(List.of(Ad30.builder()
                        .adType(8)
                        .slotId("slotId")
                        .retCode(200)
                        .contentList(List.of(content))
                        .build()))
                .build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);
        final Video video = Video.builder().build();
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.video(video));

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, bidRequest);

        // then
        verify(admBuilder).buildVideo(AdsType.BANNER, content, video);
        final Bid expectedBid = Bid.builder()
                .id("imp_id")
                .impid("imp_id")
                .price(BigDecimal.TEN)
                .crid("contentId")
                .adm("video")
                .w(200)
                .h(200)
                .adomain(List.of("huaweiads"))
                .nurl("")
                .build();
        assertThat(actual.getValue()).containsOnly(BidderBid.of(expectedBid, BidType.video, "CNY"));
        assertThat(actual.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnNativeBidWhenAdIsNative() {
        // given
        final Content content = Content.builder()
                .contentId("contentId")
                .price(BigDecimal.TEN)
                .build();
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder()
                .retcode(200)
                .multiad(List.of(Ad30.builder()
                        .adType(60)
                        .slotId("slotId")
                        .retCode(200)
                        .contentList(List.of(content))
                        .build()))
                .build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);
        final Native xNative = Native.builder().build();
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.xNative(xNative));

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, bidRequest);

        // then
        verify(admBuilder).buildNative(AdsType.ROLL, content, xNative);
        final Bid expectedBid = Bid.builder()
                .id("imp_id")
                .impid("imp_id")
                .price(BigDecimal.TEN)
                .crid("contentId")
                .adm("native")
                .w(300)
                .h(300)
                .adomain(List.of("huaweiads"))
                .nurl("")
                .build();
        assertThat(actual.getValue()).containsOnly(BidderBid.of(expectedBid, BidType.xNative, "CNY"));
        assertThat(actual.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidWithFirstNoticeUrlOfWinMonitor() {
        // given
        final Content content = Content.builder()
                .contentId("contentId")
                .price(BigDecimal.TEN)
                .monitorList(List.of(
                        Monitor.of("win", List.of()),
                        Monitor.of("playStart", List.of("url1")),
                        Monitor.of("win", List.of("url2", "url3")),
                        Monitor.of("win", List.of("url4"))))
                .build();
        final HuaweiAdsResponse response = HuaweiAdsResponse.builder()
                .retcode(200)
                .multiad(List.of(Ad30.builder()
                        .adType(3)
                        .slotId("slotId")
                        .retCode(200)
                        .contentList(List.of(content))
                        .build()))
                .build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);
        final Native xNative = Native.builder().build();
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.xNative(xNative));

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, bidRequest);

        // then
        verify(admBuilder).buildNative(AdsType.NATIVE, content, xNative);
        final Bid expectedBid = Bid.builder()
                .id("imp_id")
                .impid("imp_id")
                .price(BigDecimal.TEN)
                .crid("contentId")
                .adm("native")
                .w(300)
                .h(300)
                .adomain(List.of("huaweiads"))
                .nurl("url2")
                .build();
        assertThat(actual.getValue()).containsOnly(BidderBid.of(expectedBid, BidType.xNative, "CNY"));
        assertThat(actual.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerNativeAndVideoBids() {
        // given
        final Content videoContent = Content.builder()
                .contentId("videoContentId")
                .price(BigDecimal.TEN)
                .build();
        final Ad30 videoSlot = Ad30.builder()
                .adType(60)
                .slotId("videoSlotId")
                .retCode(200)
                .contentList(List.of(videoContent))
                .build();

        final Ad30 ignoredSlot = Ad30.builder()
                .adType(60)
                .slotId("slotId")
                .retCode(201)
                .build();

        final Content nativeContent = Content.builder()
                .contentId("nativeContentId")
                .price(BigDecimal.TEN)
                .build();
        final Ad30 nativeSlot = Ad30.builder()
                .adType(3)
                .slotId("nativeSlotId")
                .retCode(200)
                .contentList(List.of(nativeContent))
                .build();

        final Content bannerContent = Content.builder()
                .contentId("bannerContentId")
                .price(BigDecimal.TEN)
                .build();
        final Ad30 bannerSlot = Ad30.builder()
                .adType(8)
                .slotId("bannerSlotId")
                .retCode(200)
                .contentList(List.of(bannerContent))
                .build();

        final HuaweiAdsResponse response = HuaweiAdsResponse.builder()
                .retcode(200)
                .multiad(List.of(videoSlot, bannerSlot, ignoredSlot, nativeSlot))
                .build();
        final BidderCall<HuaweiAdsRequest> httpCall = givenHttpCall(response);
        final Native xNative = Native.builder().build();
        final Video video = Video.builder().build();
        final Banner banner = Banner.builder().build();

        final ObjectNode videoImpExt = givenImpExt(extImpBuilder -> extImpBuilder.slotId("videoSlotId"));
        final ObjectNode bannerImpExt = givenImpExt(extImpBuilder -> extImpBuilder.slotId("bannerSlotId"));
        final ObjectNode nativeImpExt = givenImpExt(extImpBuilder -> extImpBuilder.slotId("nativeSlotId"));

        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.xNative(xNative).ext(nativeImpExt).id("native_imp_id"),
                impBuilder -> impBuilder.video(video).ext(videoImpExt).id("video_imp_id"),
                impBuilder -> impBuilder.banner(banner).ext(bannerImpExt).id("banner_imp_id"));

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, bidRequest);

        // then
        verify(admBuilder).buildVideo(AdsType.ROLL, videoContent, video);
        verify(admBuilder).buildNative(AdsType.NATIVE, nativeContent, xNative);
        verify(admBuilder).buildBanner(AdsType.BANNER, bannerContent);

        final Bid expectedNativeBid = Bid.builder()
                .id("native_imp_id")
                .impid("native_imp_id")
                .price(BigDecimal.TEN)
                .crid("nativeContentId")
                .adm("native")
                .w(300)
                .h(300)
                .adomain(List.of("huaweiads"))
                .nurl("")
                .build();

        final Bid expectedVideoBid = Bid.builder()
                .id("video_imp_id")
                .impid("video_imp_id")
                .price(BigDecimal.TEN)
                .crid("videoContentId")
                .adm("video")
                .w(200)
                .h(200)
                .adomain(List.of("huaweiads"))
                .nurl("")
                .build();

        final Bid expectedBannerBid = Bid.builder()
                .id("banner_imp_id")
                .impid("banner_imp_id")
                .price(BigDecimal.TEN)
                .crid("bannerContentId")
                .adm("banner")
                .w(100)
                .h(100)
                .adomain(List.of("huaweiads"))
                .nurl("")
                .build();

        assertThat(actual.getValue()).containsExactlyInAnyOrder(
                BidderBid.of(expectedVideoBid, BidType.video, "CNY"),
                BidderBid.of(expectedNativeBid, BidType.xNative, "CNY"),
                BidderBid.of(expectedBannerBid, BidType.banner, "CNY"));
        assertThat(actual.getErrors()).isEmpty();
    }

    private static String encrypt(String message, String key) {
        try {
            final Mac mac = HmacUtils.getInitializedMac(
                    HmacAlgorithms.HMAC_SHA_256,
                    key.getBytes(StandardCharsets.UTF_8));
            final byte[] hmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(hmac);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        final ObjectNode extUserData = mapper.createObjectNode()
                .set("gaid", mapper.createArrayNode().add("gaid_id"));

        return BidRequest.builder()
                .id("bid_request_id")
                .test(1)
                .user(User.builder().ext(ExtUser.builder().data(extUserData).build()).build())
                .imp(Arrays.stream(impCustomizers).map(HuaweiAdsBidderTest::givenImp).toList())
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("imp_id").ext(givenImpExt(identity()))).build();
    }

    private static ObjectNode givenImpExt(UnaryOperator<ExtImpHuaweiAds.ExtImpHuaweiAdsBuilder> impExtCustomizer) {
        final ExtImpHuaweiAds extImp = impExtCustomizer.apply(
                        ExtImpHuaweiAds.builder()
                                .slotId("slotId")
                                .keyId("  keyId")
                                .adType("adType")
                                .publisherId("publisherId")
                                .signKey("signKey  "))
                .build();
        return mapper.valueToTree(ExtPrebid.of(null, extImp));
    }

    private static BidderCall<HuaweiAdsRequest> givenHttpCall(HuaweiAdsResponse response) {
        try {
            return givenHttpCall(mapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            Assertions.fail(e.getMessage());
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private static BidderCall<HuaweiAdsRequest> givenHttpCall(String body) {

        return BidderCall.succeededHttp(
                HttpRequest.<HuaweiAdsRequest>builder().payload(HuaweiAdsRequest.builder().build()).build(),
                HttpResponse.of(200, null, body),
                null);
    }

}
