package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Request;
import com.iab.openrtb.request.Video;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.huaweiads.model.AdsType;
import org.prebid.server.bidder.huaweiads.model.request.AdSlot30;
import org.prebid.server.bidder.huaweiads.model.request.Format;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuaweiAds;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class HuaweiAdSlotBuilder {

    private static final Integer IMAGE_ASSET_TYPE_MAIN = 3;
    private static final String TEST_AUTH_ENABLED = "true";

    private final JacksonMapper mapper;

    public HuaweiAdSlotBuilder(JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public AdSlot30 build(Imp imp, ExtImpHuaweiAds impExt) throws PreBidException {
        final AdsType adsType = AdsType.ofTypeName(impExt.getAdType());
        final AdSlot30 adSlot;

        if (imp.getBanner() != null) {
            adSlot = switch (adsType) {
                case BANNER, INTERSTITIAL -> makeBannerAdSlot(imp.getBanner());
                default -> throw new PreBidException(
                        "check openrtb format: request has banner, doesn't correspond to huawei adtype " + adsType);
            };
        } else if (imp.getXNative() != null) {
            adSlot = switch (adsType) {
                case NATIVE -> makeNativeAdSlot(imp.getXNative());
                default -> throw new PreBidException(
                        "check openrtb format: request has native, doesn't correspond to huawei adtype " + adsType);
            };
        } else if (imp.getVideo() != null) {
            adSlot = switch (adsType) {
                case BANNER, INTERSTITIAL, REWARDED -> makeVideoAdSlot(imp.getVideo());
                case ROLL -> makeRollVideoAdSlot(imp.getVideo());
                default -> throw new PreBidException(
                        "check openrtb format: request has video, doesn't correspond to huawei adtype " + adsType);
            };
        } else if (imp.getAudio() != null) {
            throw new PreBidException(
                    "check openrtb format: request has audio, not currently supported");
        } else {
            throw new PreBidException(
                    "check openrtb format: please choose one of our supported type banner, native, or video");
        }

        return adSlot.toBuilder()
                .slotId(impExt.getSlotId())
                .adType(adsType.getType())
                .test(TEST_AUTH_ENABLED.equals(impExt.getIsTestAuthorization()) ? 1 : 0)
                .build();
    }

    private AdSlot30 makeVideoAdSlot(Video video) {
        return HuaweiUtils.isFormatDefined(video.getW(), video.getH())
                ? AdSlot30.builder().w(video.getW()).h(video.getH()).build()
                : AdSlot30.builder().build();
    }

    private AdSlot30 makeRollVideoAdSlot(Video video) {
        final Integer maxDuration = video.getMaxduration();
        if (maxDuration == null || maxDuration == 0) {
            throw new PreBidException("extract openrtb video failed: MaxDuration is empty "
                    + "when huaweiads adtype is roll.");
        }

        return makeVideoAdSlot(video).toBuilder()
                .totalDuration(maxDuration)
                .build();
    }

    private AdSlot30 makeNativeAdSlot(Native xNative) {
        final List<Asset> assets = parseNativeRequestAssets(xNative);
        // Only one of the {title,img,video,data} objects should be present in each object.
        final long numVideo = assets.stream().map(Asset::getVideo).filter(Objects::nonNull).count();
        final long numImage = assets.stream().map(Asset::getImg).filter(Objects::nonNull)
                .filter(img -> IMAGE_ASSET_TYPE_MAIN.equals(img.getType()))
                .count();
        return AdSlot30.builder()
                .detailedCreativeTypeList(makeDetailedCreativeTypeList(numVideo, numImage))
                .build();
    }

    private AdSlot30 makeBannerAdSlot(Banner banner) {
        final Integer bannerWidth = banner.getW();
        final Integer bannerHeight = banner.getH();
        final boolean validFormat = HuaweiUtils.isFormatDefined(bannerWidth, bannerHeight);

        return AdSlot30.builder()
                .w(validFormat ? bannerWidth : null)
                .h(validFormat ? bannerHeight : null)
                .format(getBannerFormats(banner))
                .build();
    }

    private static List<Format> getBannerFormats(Banner banner) {
        return Stream.ofNullable(banner.getFormat())
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .filter(format -> HuaweiUtils.isFormatDefined(format.getW(), format.getH()))
                .map(format -> Format.of(format.getW(), format.getH()))
                .toList();
    }

    private List<Asset> parseNativeRequestAssets(Native xNative) {
        final String nativeRequest = xNative.getRequest();
        if (StringUtils.isBlank(nativeRequest)) {
            throw new PreBidException("extract openrtb native failed: imp.Native.Request is empty");
        }
        final Request nativePayload;
        try {
            nativePayload = mapper.mapper().readValue(nativeRequest, Request.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }
        return Optional.ofNullable(nativePayload)
                .map(Request::getAssets)
                .orElseGet(Collections::emptyList);
    }

    private static List<String> makeDetailedCreativeTypeList(long numVideo, long numImage) {
        if (numVideo >= 1) {
            return List.of("903");
        } else if (numImage > 1) {
            return List.of("904");
        } else if (numImage == 1) {
            return List.of("901");
        } else {
            return List.of("913", "914");
        }
    }

}
