package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.ImageObject;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Request;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.request.VideoObject;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.huaweiads.model.request.AdSlot30;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuaweiAds;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HuaweiAdSlotBuilderTest extends VertxTest {

    private final HuaweiAdSlotBuilder target = new HuaweiAdSlotBuilder(jacksonMapper);

    @Test
    public void buildShouldFailWhenBannerImpressionHasNativeAdType() {
        // given
        final Imp givenImp = Imp.builder().banner(Banner.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("native").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: request has banner, doesn't correspond to huawei adtype NATIVE");
    }

    @Test
    public void buildShouldFailWhenBannerImpressionHasRollAdType() {
        // given
        final Imp givenImp = Imp.builder().banner(Banner.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("roll").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: request has banner, doesn't correspond to huawei adtype ROLL");
    }

    @Test
    public void buildShouldFailWhenBannerImpressionHasRewardedAdType() {
        // given
        final Imp givenImp = Imp.builder().banner(Banner.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("rewarded").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: request has banner, doesn't correspond to huawei adtype REWARDED");
    }

    @Test
    public void buildShouldFailWhenBannerImpressionHasSplashAdType() {
        // given
        final Imp givenImp = Imp.builder().banner(Banner.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("splash").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: request has banner, doesn't correspond to huawei adtype SPLASH");
    }

    @Test
    public void buildShouldFailWhenBannerImpressionHasMagazineLockAdType() {
        // given
        final Imp givenImp = Imp.builder().banner(Banner.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("magazinelock").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: request has banner, "
                        + "doesn't correspond to huawei adtype MAGAZINELOCK");
    }

    @Test
    public void buildShouldFailWhenBannerImpressionHasAudioAdType() {
        // given
        final Imp givenImp = Imp.builder().banner(Banner.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("audio").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: request has banner, doesn't correspond to huawei adtype AUDIO");
    }

    @Test
    public void buildShouldFailWhenNativeImpressionHasBannerAdType() {
        // given
        final Imp givenImp = Imp.builder().xNative(Native.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("banner").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: request has native, doesn't correspond to huawei adtype BANNER");
    }

    @Test
    public void buildShouldFailWhenNativeImpressionHasRollAdType() {
        // given
        final Imp givenImp = Imp.builder().xNative(Native.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("roll").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: request has native, doesn't correspond to huawei adtype ROLL");
    }

    @Test
    public void buildShouldFailWhenNativeImpressionHasRewardedAdType() {
        // given
        final Imp givenImp = Imp.builder().xNative(Native.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("rewarded").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: request has native, doesn't correspond to huawei adtype REWARDED");
    }

    @Test
    public void buildShouldFailWhenNativeImpressionHasSplashAdType() {
        // given
        final Imp givenImp = Imp.builder().xNative(Native.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("splash").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: request has native, doesn't correspond to huawei adtype SPLASH");
    }

    @Test
    public void buildShouldFailWhenNativeImpressionHasMagazineLockAdType() {
        // given
        final Imp givenImp = Imp.builder().xNative(Native.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("magazinelock").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: request has native, "
                        + "doesn't correspond to huawei adtype MAGAZINELOCK");
    }

    @Test
    public void buildShouldFailWhenNativeImpressionHasAudioAdType() {
        // given
        final Imp givenImp = Imp.builder().xNative(Native.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("audio").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: request has native, doesn't correspond to huawei adtype AUDIO");
    }

    @Test
    public void buildShouldFailWhenNativeImpressionHasInterstitialAdType() {
        // given
        final Imp givenImp = Imp.builder().xNative(Native.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("interstitial").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: request has native, "
                        + "doesn't correspond to huawei adtype INTERSTITIAL");
    }

    @Test
    public void buildShouldFailWhenNativeImpressionHasEmptyRequest() {
        // given
        final Imp givenImp = Imp.builder().xNative(Native.builder().request(null).build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("native").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("extract openrtb native failed: imp.Native.Request is empty");
    }

    @Test
    public void buildShouldFailWhenNativeImpressionHasRequestThatCanNotBeParsed() {
        // given
        final Imp givenImp = Imp.builder().xNative(Native.builder().request("invalid_request").build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("native").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessageStartingWith("Unrecognized token");
    }

    @Test
    public void buildShouldFailWhenVideoImpressionHasNativeAdType() {
        // given
        final Imp givenImp = Imp.builder().video(Video.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("native").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: request has video, doesn't correspond to huawei adtype NATIVE");
    }

    @Test
    public void buildShouldFailWhenVideoImpressionHasSplashAdType() {
        // given
        final Imp givenImp = Imp.builder().video(Video.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("splash").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: request has video, doesn't correspond to huawei adtype SPLASH");
    }

    @Test
    public void buildShouldFailWhenVideoImpressionHasMagazineLockAdType() {
        // given
        final Imp givenImp = Imp.builder().video(Video.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("magazinelock").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: request has video, "
                        + "doesn't correspond to huawei adtype MAGAZINELOCK");
    }

    @Test
    public void buildShouldFailWhenVideoImpressionHasAudioAdType() {
        // given
        final Imp givenImp = Imp.builder().video(Video.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("audio").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: request has video, doesn't correspond to huawei adtype AUDIO");
    }

    @Test
    public void buildShouldFailWhenVideoImpressionHasRollAdTypeAndMaxDurationIsZero() {
        // given
        final Imp givenImp = Imp.builder().video(Video.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().adType("roll").build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("extract openrtb video failed: MaxDuration is empty when huaweiads adtype is roll.");
    }

    @Test
    public void buildShouldFailWhenImpressionIsAudio() {
        // given
        final Imp givenImp = Imp.builder().audio(Audio.builder().build()).build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: request has audio, not currently supported");
    }

    @Test
    public void buildShouldFailWhenImpressionIsEmpty() {
        // given
        final Imp givenImp = Imp.builder().build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder().build();

        // when & then
        assertThatThrownBy(() -> target.build(givenImp, givenImpExt))
                .isInstanceOf(PreBidException.class)
                .hasMessage("check openrtb format: please choose one of our supported type banner, native, or video");
    }

    @Test
    public void buildShouldBuildBannerAdSlotWithAllFormatsWhenImpIsBannerAndBannerAdType() {
        // given
        final Imp givenImp = Imp.builder()
                .banner(Banner.builder()
                        .w(1920)
                        .h(1080)
                        .format(List.of(
                                Format.builder().w(100).build(),
                                Format.builder().h(100).build(),
                                Format.builder().build(),
                                Format.builder().w(200).h(200).build()))
                        .build())
                .build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder()
                .slotId("slotId")
                .adType("banner")
                .build();

        // when
        final AdSlot30 actual = target.build(givenImp, givenImpExt);

        // then
        final AdSlot30 expected = AdSlot30.builder()
                .w(1920)
                .h(1080)
                .adType(8)
                .slotId("slotId")
                .test(0)
                .format(List.of(org.prebid.server.bidder.huaweiads.model.request.Format.of(200, 200)))
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildBannerAdSlotWithoutFormatsWhenImpIsBannerAndHasInterstitialAdType() {
        // given
        final Imp givenImp = Imp.builder()
                .banner(Banner.builder().build())
                .build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder()
                .slotId("slotId")
                .adType("interstitial")
                .build();

        // when
        final AdSlot30 actual = target.build(givenImp, givenImpExt);

        // then
        final AdSlot30 expected = AdSlot30.builder()
                .adType(12)
                .slotId("slotId")
                .format(Collections.emptyList())
                .test(0)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildNativeAdSlotWhenImpIsNativeAndHasNoImagesAndNoVideos() throws JsonProcessingException {
        // given
        final Request nativeRequest = Request.builder().assets(List.of()).build();
        final Imp givenImp = Imp.builder()
                .xNative(Native.builder().request(mapper.writeValueAsString(nativeRequest)).build())
                .build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder()
                .slotId("slotId")
                .adType("native")
                .build();

        // when
        final AdSlot30 actual = target.build(givenImp, givenImpExt);

        // then
        final AdSlot30 expected = AdSlot30.builder()
                .adType(3)
                .slotId("slotId")
                .detailedCreativeTypeList(List.of("913", "914"))
                .test(0)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildNativeAdSlotWhenImpIsNativeAndHasOneNonMainImageWithFormat()
            throws JsonProcessingException {
        // given
        final Request nativeRequest = Request.builder()
                .assets(List.of(Asset.builder().img(ImageObject.builder()
                                .w(200)
                                .h(200)
                                .wmin(300)
                                .hmin(300)
                                .type(1).build())
                        .build()))
                .build();
        final Imp givenImp = Imp.builder()
                .xNative(Native.builder().request(mapper.writeValueAsString(nativeRequest)).build())
                .build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder()
                .slotId("slotId")
                .adType("native")
                .build();

        // when
        final AdSlot30 actual = target.build(givenImp, givenImpExt);

        // then
        final AdSlot30 expected = AdSlot30.builder()
                .adType(3)
                .slotId("slotId")
                .detailedCreativeTypeList(List.of("913", "914"))
                .test(0)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildNativeAdSlotWhenImpIsNativeAndHasOneMainImageWithFormat()
            throws JsonProcessingException {
        // given
        final Request nativeRequest = Request.builder()
                .assets(List.of(Asset.builder().img(ImageObject.builder()
                                .w(200)
                                .h(200)
                                .wmin(300)
                                .hmin(300)
                                .type(3).build())
                        .build()))
                .build();
        final Imp givenImp = Imp.builder()
                .xNative(Native.builder().request(mapper.writeValueAsString(nativeRequest)).build())
                .build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder()
                .slotId("slotId")
                .adType("native")
                .build();

        // when
        final AdSlot30 actual = target.build(givenImp, givenImpExt);

        // then
        final AdSlot30 expected = AdSlot30.builder()
                .adType(3)
                .slotId("slotId")
                .w(200)
                .h(200)
                .detailedCreativeTypeList(List.of("901"))
                .test(0)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildNativeAdSlotWhenImpIsNativeAndHasOneMainImageWithMinFormatOnly()
            throws JsonProcessingException {
        // given
        final Request nativeRequest = Request.builder()
                .assets(List.of(Asset.builder().img(ImageObject.builder()
                                .h(200)
                                .wmin(300)
                                .hmin(300)
                                .type(3).build())
                        .build()))
                .build();
        final Imp givenImp = Imp.builder()
                .xNative(Native.builder().request(mapper.writeValueAsString(nativeRequest)).build())
                .build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder()
                .slotId("slotId")
                .adType("native")
                .build();

        // when
        final AdSlot30 actual = target.build(givenImp, givenImpExt);

        // then
        final AdSlot30 expected = AdSlot30.builder()
                .adType(3)
                .slotId("slotId")
                .w(300)
                .h(300)
                .detailedCreativeTypeList(List.of("901"))
                .test(0)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildNativeAdSlotWhenImpIsNativeAndHasOneMainImageWithoutFormat()
            throws JsonProcessingException {
        // given
        final Request nativeRequest = Request.builder()
                .assets(List.of(Asset.builder().img(ImageObject.builder().type(3).build()).build()))
                .build();
        final Imp givenImp = Imp.builder()
                .xNative(Native.builder().request(mapper.writeValueAsString(nativeRequest)).build())
                .build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder()
                .slotId("slotId")
                .adType("native")
                .build();

        // when
        final AdSlot30 actual = target.build(givenImp, givenImpExt);

        // then
        final AdSlot30 expected = AdSlot30.builder()
                .adType(3)
                .slotId("slotId")
                .detailedCreativeTypeList(List.of("901"))
                .test(0)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildNativeAdSlotWhenImpIsNativeAndHasTwoMainImages()
            throws JsonProcessingException {
        // given
        final Request nativeRequest = Request.builder()
                .assets(List.of(
                        Asset.builder().img(ImageObject.builder().type(3).build()).build(),
                        Asset.builder().img(ImageObject.builder().type(3).build()).build()))
                .build();
        final Imp givenImp = Imp.builder()
                .xNative(Native.builder().request(mapper.writeValueAsString(nativeRequest)).build())
                .build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder()
                .slotId("slotId")
                .adType("native")
                .build();

        // when
        final AdSlot30 actual = target.build(givenImp, givenImpExt);

        // then
        final AdSlot30 expected = AdSlot30.builder()
                .adType(3)
                .slotId("slotId")
                .detailedCreativeTypeList(List.of("904"))
                .test(0)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildNativeAdSlotWhenImpIsNativeAndHasOneMainAndOneNonMainImages()
            throws JsonProcessingException {
        // given
        final Request nativeRequest = Request.builder()
                .assets(List.of(
                        Asset.builder().img(ImageObject.builder().type(3).build()).build(),
                        Asset.builder().img(ImageObject.builder().type(2).build()).build()))
                .build();
        final Imp givenImp = Imp.builder()
                .xNative(Native.builder().request(mapper.writeValueAsString(nativeRequest)).build())
                .build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder()
                .slotId("slotId")
                .adType("native")
                .build();

        // when
        final AdSlot30 actual = target.build(givenImp, givenImpExt);

        // then
        final AdSlot30 expected = AdSlot30.builder()
                .adType(3)
                .slotId("slotId")
                .detailedCreativeTypeList(List.of("901"))
                .test(0)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildNativeAdSlotWhenImpIsNativeAndHasOneMainImageAndOneVideo()
            throws JsonProcessingException {
        // given
        final Request nativeRequest = Request.builder()
                .assets(List.of(
                        Asset.builder().img(ImageObject.builder().type(3).build()).build(),
                        Asset.builder().video(VideoObject.builder().build()).build()))
                .build();
        final Imp givenImp = Imp.builder()
                .xNative(Native.builder().request(mapper.writeValueAsString(nativeRequest)).build())
                .build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder()
                .slotId("slotId")
                .adType("native")
                .build();

        // when
        final AdSlot30 actual = target.build(givenImp, givenImpExt);

        // then
        final AdSlot30 expected = AdSlot30.builder()
                .adType(3)
                .slotId("slotId")
                .detailedCreativeTypeList(List.of("903"))
                .test(0)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildNativeAdSlotWhenImpIsNativeAndHasTwoMainImagesAndOneVideo()
            throws JsonProcessingException {
        // given
        final Request nativeRequest = Request.builder()
                .assets(List.of(
                        Asset.builder().img(ImageObject.builder().type(3).build()).build(),
                        Asset.builder().img(ImageObject.builder().type(3).build()).build(),
                        Asset.builder().video(VideoObject.builder().build()).build()))
                .build();
        final Imp givenImp = Imp.builder()
                .xNative(Native.builder().request(mapper.writeValueAsString(nativeRequest)).build())
                .build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder()
                .slotId("slotId")
                .adType("native")
                .build();

        // when
        final AdSlot30 actual = target.build(givenImp, givenImpExt);

        // then
        final AdSlot30 expected = AdSlot30.builder()
                .adType(3)
                .slotId("slotId")
                .detailedCreativeTypeList(List.of("903"))
                .test(0)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildNativeAdSlotWhenImpIsNativeAndHasTwoVideos()
            throws JsonProcessingException {
        // given
        final Request nativeRequest = Request.builder()
                .assets(List.of(
                        Asset.builder().video(VideoObject.builder().build()).build(),
                        Asset.builder().video(VideoObject.builder().build()).build()))
                .build();
        final Imp givenImp = Imp.builder()
                .xNative(Native.builder().request(mapper.writeValueAsString(nativeRequest)).build())
                .build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder()
                .slotId("slotId")
                .adType("native")
                .build();

        // when
        final AdSlot30 actual = target.build(givenImp, givenImpExt);

        // then
        final AdSlot30 expected = AdSlot30.builder()
                .adType(3)
                .slotId("slotId")
                .detailedCreativeTypeList(List.of("903"))
                .test(0)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildVideoAdSlotWhenImpIsVideoAndHasFormatAndHasBannerType() {
        // given
        final Imp givenImp = Imp.builder()
                .video(Video.builder().w(100).h(100).build())
                .build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder()
                .slotId("slotId")
                .adType("banner")
                .build();

        // when
        final AdSlot30 actual = target.build(givenImp, givenImpExt);

        // then
        final AdSlot30 expected = AdSlot30.builder()
                .adType(8)
                .slotId("slotId")
                .w(100)
                .h(100)
                .test(0)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildVideoAdSlotWhenImpIsVideoAndHasNoFormatAndHasInterstitialType() {
        // given
        final Imp givenImp = Imp.builder()
                .video(Video.builder().build())
                .build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder()
                .slotId("slotId")
                .adType("interstitial")
                .build();

        // when
        final AdSlot30 actual = target.build(givenImp, givenImpExt);

        // then
        final AdSlot30 expected = AdSlot30.builder()
                .adType(12)
                .slotId("slotId")
                .test(0)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildVideoAdSlotWhenImpIsVideoAndHasPartialFormatAndHasRewardedType() {
        // given
        final Imp givenImp = Imp.builder()
                .video(Video.builder().w(100).build())
                .build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder()
                .slotId("slotId")
                .adType("rewarded")
                .build();

        // when
        final AdSlot30 actual = target.build(givenImp, givenImpExt);

        // then
        final AdSlot30 expected = AdSlot30.builder()
                .adType(7)
                .slotId("slotId")
                .test(0)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildVideoAdSlotWhenImpIsVideoAndAndHasRollTypeAndMaxDurationSet() {
        // given
        final Imp givenImp = Imp.builder()
                .video(Video.builder().maxduration(2000).build())
                .build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder()
                .slotId("slotId")
                .adType("roll")
                .build();

        // when
        final AdSlot30 actual = target.build(givenImp, givenImpExt);

        // then
        final AdSlot30 expected = AdSlot30.builder()
                .adType(60)
                .slotId("slotId")
                .totalDuration(2000)
                .test(0)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildAdSlotWithTestAuthEnabledWhenImpExtHasTestAuthTrue() {
        // given
        final Imp givenImp = Imp.builder()
                .video(Video.builder().build())
                .build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder()
                .slotId("slotId")
                .adType("interstitial")
                .isTestAuthorization("true")
                .build();

        // when
        final AdSlot30 actual = target.build(givenImp, givenImpExt);

        // then
        final AdSlot30 expected = AdSlot30.builder()
                .adType(12)
                .slotId("slotId")
                .test(1)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildAdSlotWithTestAuthDisabledWhenImpExtHasRandomStringAsTestAuth() {
        // given
        final Imp givenImp = Imp.builder()
                .video(Video.builder().build())
                .build();
        final ExtImpHuaweiAds givenImpExt = ExtImpHuaweiAds.builder()
                .slotId("slotId")
                .adType("interstitial")
                .isTestAuthorization(UUID.randomUUID().toString())
                .build();

        // when
        final AdSlot30 actual = target.build(givenImp, givenImpExt);

        // then
        final AdSlot30 expected = AdSlot30.builder()
                .adType(12)
                .slotId("slotId")
                .test(0)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

}
