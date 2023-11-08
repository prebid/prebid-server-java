package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Request;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.DataObject;
import com.iab.openrtb.response.EventTracker;
import com.iab.openrtb.response.ImageObject;
import com.iab.openrtb.response.Link;
import com.iab.openrtb.response.Response;
import com.iab.openrtb.response.TitleObject;
import com.iab.openrtb.response.VideoObject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.huaweiads.model.AdsType;
import org.prebid.server.bidder.huaweiads.model.response.Content;
import org.prebid.server.bidder.huaweiads.model.response.CreativeType;
import org.prebid.server.bidder.huaweiads.model.response.HuaweiAdm;
import org.prebid.server.bidder.huaweiads.model.response.Icon;
import org.prebid.server.bidder.huaweiads.model.response.ImageInfo;
import org.prebid.server.bidder.huaweiads.model.response.MediaFile;
import org.prebid.server.bidder.huaweiads.model.response.MetaData;
import org.prebid.server.bidder.huaweiads.model.response.Monitor;
import org.prebid.server.bidder.huaweiads.model.response.MonitorEventType;
import org.prebid.server.bidder.huaweiads.model.response.PictureAdm;
import org.prebid.server.bidder.huaweiads.model.response.RewardedVideoPartAdm;
import org.prebid.server.bidder.huaweiads.model.response.VideoAdm;
import org.prebid.server.bidder.huaweiads.model.response.VideoInfo;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HuaweiAdmBuilder {

    private static final Integer DATA_ASSET_CTA_TEXT_TYPE = 12;
    private static final Set<Integer> DATA_ASSET_DESC_TYPES = Set.of(2, 10);
    private static final int IMAGE_ASSET_TYPE_ICON = 1;
    private static final int APP_PROMOTION_INTERACTION_TYPE = 3;
    private static final String DEFAULT_NATIVE_VERSION = "1.1";
    private static final String DEFAULT_VIDEO_MIME_TYPE = "video/mp4";
    private static final int EVENT_TRACKING_IMAGE_METHOD = 1;
    private static final int IMPRESSION_EVENT_TYPE = 1;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final JacksonMapper mapper;

    public HuaweiAdmBuilder(JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public HuaweiAdm buildBanner(AdsType adType, Content content) {
        if (adType != AdsType.BANNER && adType != AdsType.INTERSTITIAL) {
            throw new PreBidException("openrtb banner should correspond to huaweiads adtype: banner or interstitial");
        }

        final CreativeType creativeType = Optional.ofNullable(content.getCreativeType())
                .map(type -> type > 100 ? type - 100 : type)
                .map(CreativeType::of)
                .orElse(CreativeType.UNKNOWN);

        return switch (creativeType) {
            case VIDEO, VIDEO_TEXT, VIDEO_WITH_PICTURES_TEXT -> buildVideo(adType, content, null);
            case TEXT, BIG_PICTURE, BIG_PICTURE_2, SMALL_PICTURE,
                    THREE_SMALL_PICTURES_TEXT, ICON_TEXT, GIF -> buildPicture(content);
            default -> throw new PreBidException("no banner support creativetype");
        };
    }

    public HuaweiAdm buildVideo(AdsType adType, Content content, Video video) {
        if (adType == AdsType.AUDIO || adType == AdsType.SPLASH || adType == AdsType.UNKNOWN) {
            throw new PreBidException("openrtb video should correspond to huaweiads adtype: "
                    + "banner, interstitial, roll, rewarded or native");
        }

        final MetaData metaData = content.getMetaData();
        if (metaData == null) {
            throw new PreBidException("Content.MetaData is empty");
        }

        Integer adHeight = null;
        Integer adWidth = null;

        final String clickUrl = getClickUrl(metaData, content.getInteractionType());
        final VideoAdm.VideoAdmBuilder videoAdmBuilder = VideoAdm.builder()
                .adTitle(decode(metaData.getTitle()))
                .adId(content.getContentId())
                .creativeId(content.getContentId())
                .clickUrl(clickUrl);

        if (adType == AdsType.ROLL) {
            final MediaFile mediaFile = metaData.getMediaFile();
            if (mediaFile == null) {
                throw new PreBidException("Content.MetaData.MediaFile is mandatory for roll video impression");
            }

            final String resourceUrl = HuaweiUtils.getIfNotBlank(mediaFile.getUrl()).orElseThrow(() ->
                    new PreBidException("extract Adm for video failed: Content.MetaData.MediaFile.Url is empty"));
            videoAdmBuilder.resourceUrl(resourceUrl);
            videoAdmBuilder.duration(convertDuration(metaData.getDuration()));
            videoAdmBuilder.mime(HuaweiUtils.getIfNotBlank(mediaFile.getMime()).orElse(DEFAULT_VIDEO_MIME_TYPE));

            if (HuaweiUtils.isFormatDefined(mediaFile.getWidth(), mediaFile.getHeight())) {
                adHeight = mediaFile.getHeight();
                adWidth = mediaFile.getWidth();
            }
        } else {
            final VideoInfo videoInfo = metaData.getVideoInfo();
            if (videoInfo == null) {
                throw new PreBidException("Content.MetaData.VideoInfo is mandatory for video impression");
            }

            final String resourceUrl = HuaweiUtils.getIfNotBlank(videoInfo.getVideoDownloadUrl()).orElseThrow(() ->
                    new PreBidException(
                            "extract Adm for video failed: content.MetaData.VideoInfo.VideoDownloadUrl is empty"));
            videoAdmBuilder.resourceUrl(resourceUrl);
            videoAdmBuilder.duration(convertDuration(videoInfo.getVideoDuration()));
            videoAdmBuilder.mime(DEFAULT_VIDEO_MIME_TYPE);

            if (HuaweiUtils.isFormatDefined(videoInfo.getWidth(), videoInfo.getHeight())) {
                adHeight = videoInfo.getHeight();
                adWidth = videoInfo.getWidth();
            } else if (video != null && HuaweiUtils.isFormatDefined(video.getW(), video.getH())) {
                adHeight = video.getH();
                adWidth = video.getW();
            }
        }

        if (!HuaweiUtils.isFormatDefined(adWidth, adHeight)) {
            throw new PreBidException("extract Adm for video failed: cannot get video width, height");
        }

        videoAdmBuilder.height(adHeight);
        videoAdmBuilder.width(adWidth);

        final List<String> trackingEvents = new ArrayList<>();
        final List<String> errorTracking = new ArrayList<>();
        final List<String> dspImpTracking = new ArrayList<>();
        final List<String> dspClickTracking = new ArrayList<>();

        if (content.getMonitorList() != null) {
            for (Monitor monitor : content.getMonitorList()) {
                final List<String> urls = monitor.getUrlList();
                if (CollectionUtils.isNotEmpty(urls)) {
                    final MonitorEventType eventType = MonitorEventType.of(monitor.getEventType());
                    switch (eventType) {
                        case USER_CLOSE, PLAY_START, PLAY_END, PLAY_RESUME,
                                PLAY_PAUSE, SOUND_CLICK_OFF, SOUND_CLICK_ON ->
                                trackingEvents.add(getVastEventTrackingUrls(urls, eventType));
                        case VAST_ERROR -> errorTracking.add(getVastImpClickErrorTrackingUrls(urls, eventType));
                        case IMP -> dspImpTracking.add(getVastImpClickErrorTrackingUrls(urls, eventType));
                        case CLICK -> dspClickTracking.add(getVastImpClickErrorTrackingUrls(urls, eventType));
                    }
                }
            }
        }

        videoAdmBuilder.trackingEvents(String.join("", trackingEvents));
        videoAdmBuilder.errorTracking(String.join("", errorTracking));
        videoAdmBuilder.dspImpTracking(String.join("", dspImpTracking));
        videoAdmBuilder.dspClickTracking(String.join("", dspClickTracking));

        if (adType == AdsType.REWARDED) {
            videoAdmBuilder.rewardedVideoPart(buildRewardedVideoPart(content, adWidth, adHeight, clickUrl));
        }

        return HuaweiAdm.of(videoAdmBuilder.build().toString(), adWidth, adHeight);
    }

    private static String getVastImpClickErrorTrackingUrls(List<String> urls, MonitorEventType eventType) {
        return urls.stream().map(url -> makeUrl(eventType, url)).collect(Collectors.joining());
    }

    private static String makeUrl(MonitorEventType eventType, String url) {
        return switch (eventType) {
            case CLICK -> "<ClickTracking><![CDATA[" + url + "]]></ClickTracking>";
            case IMP -> "<Impression><![CDATA[" + url + "]]></Impression>";
            case VAST_ERROR -> "<Error><![CDATA[" + url + "&et=[ERRORCODE]]]></Error>";
            default -> StringUtils.EMPTY;
        };
    }

    private static String getVastEventTrackingUrls(List<String> urls, MonitorEventType eventType) {
        return urls.stream().map(eventUrl -> eventType == MonitorEventType.USER_CLOSE
                        ? "<Tracking event=\"skip\"><![CDATA[" + eventUrl + "]]></Tracking>"
                        + "<Tracking event=\"closeLinear\"><![CDATA[" + eventUrl + "]]></Tracking>"
                        : "<Tracking event=\"" + eventType.getEvent() + "\"><![CDATA[" + eventUrl + "]]></Tracking>")
                .collect(Collectors.joining());
    }

    private String buildRewardedVideoPart(Content content, Integer adWidth, Integer adHeight, String clickUrl) {
        final MetaData metaData = content.getMetaData();
        final String contentId = content.getContentId();

        final List<Icon> iconList = metaData.getIconList();
        final Icon firstIcon = CollectionUtils.isNotEmpty(iconList) ? iconList.get(0) : null;
        if (firstIcon != null && StringUtils.isNotBlank(firstIcon.getUrl())) {
            return buildIconRewardedPart(contentId, clickUrl, adWidth, adHeight, firstIcon);
        }

        final List<ImageInfo> imageInfoList = metaData.getImageInfoList();
        final ImageInfo firstImage = CollectionUtils.isNotEmpty(imageInfoList) ? imageInfoList.get(0) : null;
        if (firstImage != null && StringUtils.isNotBlank(firstImage.getUrl())) {
            return buildImageRewardedPart(contentId, clickUrl, adWidth, adHeight, firstImage);
        }

        return StringUtils.EMPTY;
    }

    private static String buildIconRewardedPart(String contentId,
                                                String clickUrl,
                                                Integer adWidth,
                                                Integer adHeight,
                                                Icon firstIcon) {

        final boolean isFormatDefined = HuaweiUtils.isFormatDefined(firstIcon.getWidth(), firstIcon.getHeight());
        return RewardedVideoPartAdm.builder()
                .id(contentId)
                .adId(contentId)
                .clickUrl(clickUrl)
                .staticImageUrl(firstIcon.getUrl())
                .staticImageHeight(isFormatDefined ? firstIcon.getHeight() : adHeight)
                .staticImageWidth(isFormatDefined ? firstIcon.getWidth() : adWidth)
                .build().toString();
    }

    private static String buildImageRewardedPart(String contentId,
                                                 String clickUrl,
                                                 Integer adWidth,
                                                 Integer adHeight,
                                                 ImageInfo firstImage) {

        final boolean isFormatDefined = HuaweiUtils.isFormatDefined(firstImage.getWidth(), firstImage.getHeight());
        return RewardedVideoPartAdm.builder()
                .id(contentId)
                .adId(contentId)
                .clickUrl(clickUrl)
                .staticImageUrl(firstImage.getUrl())
                .staticImageHeight(isFormatDefined ? firstImage.getHeight() : adHeight)
                .staticImageWidth(isFormatDefined ? firstImage.getWidth() : adWidth)
                .build().toString();
    }

    private HuaweiAdm buildPicture(Content content) {
        final MetaData metaData = content.getMetaData();
        if (metaData == null) {
            throw new PreBidException("Content.MetaData is empty");
        }

        final ImageInfo imageInfo = Stream.ofNullable(metaData.getImageInfoList())
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new PreBidException("content.MetaData.ImageInfo is empty"));

        if (!HuaweiUtils.isFormatDefined(imageInfo.getWidth(), imageInfo.getHeight())) {
            throw new PreBidException("content.MetaData.ImageInfo doesn't have width and/or height");
        }

        final PictureAdm adm = PictureAdm.builder()
                .imageTitle(decode(metaData.getTitle()))
                .imageInfoUrl(imageInfo.getUrl())
                .height(imageInfo.getHeight())
                .width(imageInfo.getWidth())
                .clickUrl(getClickUrl(metaData, content.getInteractionType()))
                .dspImpTrackings(makeDspImpTrackings(content))
                .dspClickTrackings(makeDspClickTrackings(content))
                .build();

        return HuaweiAdm.of(adm.toString(), adm.getWidth(), adm.getHeight());
    }

    private static String makeDspClickTrackings(Content content) {
        return Stream.ofNullable(content.getMonitorList())
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .filter(monitor -> MonitorEventType.of(monitor.getEventType()) == MonitorEventType.CLICK)
                .map(Monitor::getUrlList)
                .filter(Objects::nonNull)
                .findFirst()
                .map(list -> list.stream()
                        .filter(Objects::nonNull)
                        .map(tracking -> "\"" + tracking + "\"")
                        .collect(Collectors.joining(",")))
                .orElse(StringUtils.EMPTY);
    }

    private static String makeDspImpTrackings(Content content) {
        return Stream.ofNullable(content.getMonitorList())
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .filter(monitor -> MonitorEventType.of(monitor.getEventType()) == MonitorEventType.IMP)
                .map(Monitor::getUrlList)
                .filter(Objects::nonNull)
                .findFirst()
                .map(list -> list.stream()
                        .map(tracking -> "<img height=\"1\" width=\"1\" src='" + tracking + "' >  ")
                        .collect(Collectors.joining()))
                .orElse(StringUtils.EMPTY);
    }

    public HuaweiAdm buildNative(AdsType adType, Content content, Native xNative) {
        if (adType != AdsType.NATIVE) {
            throw new PreBidException("extract Adm for Native ad: huaweiads response is not a native ad");
        }

        final MetaData metaData = content.getMetaData();
        if (metaData == null) {
            throw new PreBidException("Content.MetaData is empty");
        }

        final String clickUrl = getClickUrl(metaData, content.getInteractionType());

        final Request nativePayload = parseNativeRequest(xNative);
        final List<Asset> assets = Optional.ofNullable(nativePayload)
                .map(Request::getAssets)
                .orElseGet(Collections::emptyList);

        Integer adHeight = null;
        Integer adWidth = null;
        final List<com.iab.openrtb.response.Asset> responseAssets = new ArrayList<>();

        final Iterator<Icon> iconIterator = Stream.ofNullable(metaData.getIconList())
                .flatMap(Collection::stream)
                .iterator();
        final Iterator<ImageInfo> imageIterator = Stream.ofNullable(metaData.getImageInfoList())
                .flatMap(Collection::stream)
                .iterator();

        for (Asset asset : assets) {
            final boolean isTitleAsset = asset.getTitle() != null;
            final boolean isVideoAsset = !isTitleAsset && asset.getVideo() != null;
            final boolean isImageAsset = !isVideoAsset && asset.getImg() != null;
            final boolean isDataAsset = !isImageAsset && asset.getData() != null;

            HuaweiAdm admVideo = null;
            if (isVideoAsset) {
                admVideo = buildVideo(adType, content, null);
                adHeight = admVideo.getHeight();
                adWidth = admVideo.getWidth();
            }

            final com.iab.openrtb.response.Asset responseAsset = com.iab.openrtb.response.Asset.builder()
                    .title(isTitleAsset ? buildTitleObject(metaData) : null)
                    .video(isVideoAsset ? VideoObject.builder().vasttag(admVideo.getAdm()).build() : null)
                    .img(isImageAsset ? buildImageObject(asset.getImg().getType(), iconIterator, imageIterator) : null)
                    .data(isDataAsset ? buildDataObject(metaData, asset.getData().getType()) : null)
                    .id(asset.getId())
                    .build();

            responseAssets.add(responseAsset);

            if (isImageAsset) {
                if (!HuaweiUtils.isFormatDefined(adWidth, adHeight)) {
                    adHeight = responseAsset.getImg().getH();
                    adWidth = responseAsset.getImg().getW();
                }
            }
        }

        final String version = Optional.ofNullable(nativePayload)
                .flatMap(payload -> HuaweiUtils.getIfNotBlank(payload.getVer()))
                .orElse(DEFAULT_NATIVE_VERSION);

        final Response response = Response.builder()
                .eventtrackers(buildEventTrackers(content))
                .assets(responseAssets)
                .link(Link.of(clickUrl, buildClickTrackers(content), null, null))
                .ver(version)
                .build();

        try {
            final String adm = mapper.mapper().writeValueAsString(response).replace("\n", "");
            return HuaweiAdm.of(adm, adWidth, adHeight);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Request parseNativeRequest(Native xNative) {
        final String nativeRequest = HuaweiUtils.getIfNotBlank(xNative.getRequest())
                .orElseThrow(() -> new PreBidException("extract openrtb native failed: imp.Native.Request is empty"));

        try {
            return mapper.mapper().readValue(nativeRequest, Request.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static TitleObject buildTitleObject(MetaData metaData) {
        final String text = decode(metaData.getTitle());
        return TitleObject.builder().text(text).len(text.length()).build();
    }

    private static ImageObject buildImageObject(Integer assetImageType,
                                                Iterator<Icon> iconsIterators,
                                                Iterator<ImageInfo> imageIterator) {
        final ImageObject.ImageObjectBuilder imgObjectBuilder = ImageObject.builder()
                .url(StringUtils.EMPTY)
                .type(assetImageType);

        if (Objects.equals(assetImageType, IMAGE_ASSET_TYPE_ICON)) {
            if (iconsIterators.hasNext()) {
                final Icon icon = iconsIterators.next();
                imgObjectBuilder.url(icon.getUrl());
                imgObjectBuilder.w(icon.getWidth());
                imgObjectBuilder.h(icon.getHeight());
            }
        } else {
            if (imageIterator.hasNext()) {
                final ImageInfo image = imageIterator.next();
                imgObjectBuilder.url(image.getUrl());
                imgObjectBuilder.w(image.getWidth());
                imgObjectBuilder.h(image.getHeight());
            }
        }

        return imgObjectBuilder.build();
    }

    private static DataObject buildDataObject(MetaData metaData, Integer assetDataType) {
        final boolean isDataAssetType = DATA_ASSET_DESC_TYPES.contains(assetDataType);
        final boolean isDataAssetCtaTextType = DATA_ASSET_CTA_TEXT_TYPE.equals(assetDataType);

        final String value = isDataAssetType
                ? metaData.getDescription()
                : isDataAssetCtaTextType
                    ? metaData.getCta()
                    : null;

        return DataObject.builder()
                .value(decode(value))
                .type(isDataAssetCtaTextType ? DATA_ASSET_CTA_TEXT_TYPE : null)
                .build();
    }

    private static List<String> buildClickTrackers(Content content) {
        return Stream.ofNullable(content.getMonitorList())
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .filter(monitor -> CollectionUtils.isNotEmpty(monitor.getUrlList())
                        && MonitorEventType.of(monitor.getEventType()) == MonitorEventType.CLICK)
                .map(Monitor::getUrlList)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .toList();
    }

    private static List<EventTracker> buildEventTrackers(Content content) {
        return Stream.ofNullable(content.getMonitorList())
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .filter(monitor -> CollectionUtils.isNotEmpty(monitor.getUrlList())
                        && MonitorEventType.of(monitor.getEventType()) == MonitorEventType.IMP)
                .map(Monitor::getUrlList)
                .filter(Objects::nonNull)
                .map(HuaweiAdmBuilder::getEventTrackers)
                .flatMap(Collection::stream)
                .toList();
    }

    private static List<EventTracker> getEventTrackers(List<String> urlList) {
        return urlList.stream()
                .map(url -> EventTracker.builder()
                        .event(IMPRESSION_EVENT_TYPE)
                        .method(EVENT_TRACKING_IMAGE_METHOD)
                        .url(url)
                        .build())
                .toList();
    }

    private String getClickUrl(MetaData metaData, Integer interactionType) {
        final Optional<String> optionalIntent = HuaweiUtils.getIfNotBlank(metaData.getIntent())
                .map(intent -> URLDecoder.decode(intent, StandardCharsets.UTF_8));

        if (Objects.equals(interactionType, APP_PROMOTION_INTERACTION_TYPE) && optionalIntent.isEmpty()) {
            throw new PreBidException("content.MetaData.Intent in huaweiads response is empty "
                    + "when interactiontype is appPromotion");
        }
        return optionalIntent
                .or(() -> HuaweiUtils.getIfNotBlank(metaData.getClickUrl()))
                .orElseThrow(() -> new PreBidException("content.MetaData.Intent and content.MetaData.ClickUrl "
                        + "in huaweiads response is empty"));
    }

    private static String convertDuration(Long duration) {
        return Optional.ofNullable(duration)
                .map(Duration::ofMillis)
                .map(LocalTime.MIDNIGHT::plus)
                .map(time -> time.format(DATE_TIME_FORMATTER))
                .orElseThrow(() -> new PreBidException("Content.MetaData.VideoInfo duration is empty"));
    }

    private static String decode(String value) {
        return HuaweiUtils.getIfNotBlank(value)
                .map(str -> URLDecoder.decode(str, StandardCharsets.UTF_8))
                .orElse(StringUtils.EMPTY);
    }

}
