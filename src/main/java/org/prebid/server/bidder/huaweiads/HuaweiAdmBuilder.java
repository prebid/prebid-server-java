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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class HuaweiAdmBuilder {

    private static final Set<Integer> DATA_ASSET_TYPES = Set.of(2, 10);
    private static final int IMAGE_ASSET_TYPE_ICON = 1;
    private static final int APP_PROMOTION_INTERACTION_TYPE = 3;
    private static final String DEFAULT_NATIVE_VERSION = "1.1";
    private static final String DEFAULT_VIDEO_MIME_TYPE = "video/mp4";

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
        //todo: PBS Go doesn't have this validation for some reason
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

        final String clickUrl = getClickUrl(content);
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

        if (content.getMonitorList() != null) {
            for (Monitor monitor : content.getMonitorList()) {
                final List<String> urls = monitor.getUrlList();
                if (CollectionUtils.isNotEmpty(urls)) {
                    final MonitorEventType eventType = MonitorEventType.of(monitor.getEventType());
                    switch (eventType) {
                        //todo: maybe addAll?
                        case USER_CLOSE, PLAY_START, PLAY_END, PLAY_RESUME,
                                PLAY_PAUSE, SOUND_CLICK_OFF, SOUND_CLICK_ON ->
                                videoAdmBuilder.trackingEvents(getVastEventTrackingUrls(urls, eventType));
                        case VAST_ERROR ->
                                videoAdmBuilder.errorTracking(getVastImpClickErrorTrackingUrls(urls, eventType));
                        case IMP -> videoAdmBuilder.dspImpTracking(getVastImpClickErrorTrackingUrls(urls, eventType));
                        case CLICK ->
                                videoAdmBuilder.dspClickTracking(getVastImpClickErrorTrackingUrls(urls, eventType));

                    }
                }
            }
        }

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
            default -> "";
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

        final RewardedVideoPartAdm.RewardedVideoPartAdmBuilder admBuilder = RewardedVideoPartAdm.builder()
                .id(content.getContentId())
                .adId(content.getContentId())
                .clickUrl(clickUrl);

        final List<Icon> iconList = metaData.getIconList();
        if (CollectionUtils.isNotEmpty(iconList) && StringUtils.isNotBlank(iconList.get(0).getUrl())) {
            final Icon icon = iconList.get(0);
            final boolean isFormatDefined = HuaweiUtils.isFormatDefined(icon.getWidth(), icon.getHeight());
            return admBuilder
                    .staticImageUrl(icon.getUrl())
                    .staticImageHeight(isFormatDefined ? icon.getHeight() : adHeight)
                    .staticImageWidth(isFormatDefined ? icon.getWidth() : adWidth)
                    .build().toString();
        }

        final List<ImageInfo> imageInfoList = metaData.getImageInfoList();
        if (CollectionUtils.isNotEmpty(imageInfoList) && StringUtils.isNotBlank(imageInfoList.get(0).getUrl())) {
            final ImageInfo imageInfo = imageInfoList.get(0);
            final boolean isFormatDefined = HuaweiUtils.isFormatDefined(imageInfo.getWidth(), imageInfo.getHeight());
            return admBuilder
                    .staticImageUrl(imageInfo.getUrl())
                    .staticImageHeight(isFormatDefined ? imageInfo.getHeight() : adHeight)
                    .staticImageWidth(isFormatDefined ? imageInfo.getWidth() : adWidth)
                    .build().toString();
        }

        return StringUtils.EMPTY;
    }

    private HuaweiAdm buildPicture(Content content) {
        final MetaData metaData = content.getMetaData();
        if (metaData == null) {
            throw new PreBidException("Content.MetaData is empty");
        }

        final ImageInfo imageInfo = Optional.ofNullable(metaData.getImageInfoList())
                .flatMap(imageInfos -> imageInfos.stream().findFirst())
                .orElseThrow(() -> new PreBidException("content.MetaData.ImageInfo is empty"));

        if (!HuaweiUtils.isFormatDefined(imageInfo.getWidth(), imageInfo.getHeight())) {
            throw new PreBidException("content.MetaData.ImageInfo doesn't have width and/or height");
        }

        final PictureAdm adm = PictureAdm.builder()
                .imageTitle(decode(metaData.getTitle()))
                .imageInfoUrl(imageInfo.getUrl())
                .height(imageInfo.getHeight())
                .width(imageInfo.getWidth())
                .clickUrl(getClickUrl(content))
                .dspImpTrackings(makeDspImpTrackings(content))
                .dspClickTrackings(makeDspClickTrackings(content))
                .build();

        return HuaweiAdm.of(adm.toString(), adm.getWidth(), adm.getHeight());
    }

    private static String makeDspClickTrackings(Content content) {
        return Optional.ofNullable(content.getMonitorList()).flatMap(list -> list.stream()
                        .filter(monitor -> CollectionUtils.isNotEmpty(monitor.getUrlList())
                                && MonitorEventType.of(monitor.getEventType()) == MonitorEventType.CLICK)
                        .map(Monitor::getUrlList)
                        //todo: maybe addAll?
                        .findFirst())
                .map(list -> list.stream()
                        .map(tracking -> "\"" + tracking + "\"")
                        .collect(Collectors.joining(",")))
                .orElse("");
    }

    private static String makeDspImpTrackings(Content content) {
        return Optional.ofNullable(content.getMonitorList()).flatMap(list -> list.stream()
                        .filter(monitor -> CollectionUtils.isNotEmpty(monitor.getUrlList())
                                && MonitorEventType.of(monitor.getEventType()) == MonitorEventType.IMP)
                        .map(Monitor::getUrlList)
                        //todo: maybe addAll?
                        .findFirst())
                .map(list -> list.stream()
                        .map(tracking -> "<img height=\"1\" width=\"1\" src='" + tracking + "' >  ")
                        .collect(Collectors.joining()))
                .orElse("");
    }

    public HuaweiAdm buildNative(AdsType adType, Content content, Native xNative) {
        if (adType != AdsType.NATIVE) {
            throw new PreBidException("extract Adm for Native ad: huaweiads response is not a native ad");
        }

        final MetaData metaData = content.getMetaData();
        if (metaData == null) {
            throw new PreBidException("Content.MetaData is empty");
        }

        final String clickUrl = getClickUrl(content);

        final Request nativePayload = parseNativeRequest(xNative);
        final List<Asset> assets = nativePayload.getAssets() == null
                ? Collections.emptyList()
                : nativePayload.getAssets();

        Integer adHeight = null;
        Integer adWidth = null;
        final List<com.iab.openrtb.response.Asset> responseAssets = new ArrayList<>();

        int imgIndex = 0;
        int iconIndex = 0;
        for (Asset asset : assets) {
            final com.iab.openrtb.response.Asset.AssetBuilder responseAssetBuilder =
                    com.iab.openrtb.response.Asset.builder();
            if (asset.getTitle() != null) {
                final String text = decode(metaData.getTitle());
                final TitleObject titleObject = TitleObject.builder().text(text).len(text.length()).build();
                responseAssetBuilder.title(titleObject);
            } else if (asset.getVideo() != null) {
                final HuaweiAdm admVideo = buildVideo(adType, content, null);
                //todo: video always overwrites format
                adHeight = admVideo.getHeight();
                adWidth = admVideo.getWidth();
                final VideoObject videoObject = VideoObject.builder().vasttag(admVideo.getAdm()).build();
                responseAssetBuilder.video(videoObject);
            } else if (asset.getImg() != null) {
                final Integer imageType = asset.getImg().getType();
                final ImageObject.ImageObjectBuilder imgObjectBuilder = ImageObject.builder()
                        //todo: can be overwritten by null
                        .url(StringUtils.EMPTY)
                        .type(imageType);

                if (Objects.equals(imageType, IMAGE_ASSET_TYPE_ICON)) {
                    final List<Icon> iconList = metaData.getIconList();
                    if (iconList != null && iconList.size() > iconIndex) {
                        imgObjectBuilder.url(iconList.get(iconIndex).getUrl());
                        //todo: it might be inappropriate if the format is not defined
                        imgObjectBuilder.w(iconList.get(iconIndex).getWidth());
                        imgObjectBuilder.h(iconList.get(iconIndex).getHeight());
                        iconIndex++;
                    }
                } else {
                    final List<ImageInfo> imageInfoList = metaData.getImageInfoList();
                    if (imageInfoList != null && imageInfoList.size() > imgIndex) {
                        imgObjectBuilder.url(imageInfoList.get(imgIndex).getUrl());
                        //todo: it might be inappropriate if the format is not defined
                        imgObjectBuilder.w(imageInfoList.get(imgIndex).getWidth());
                        imgObjectBuilder.h(imageInfoList.get(imgIndex).getHeight());
                        imgIndex++;
                    }
                }

                final ImageObject imageObject = imgObjectBuilder.build();

                if (!HuaweiUtils.isFormatDefined(adWidth, adHeight)) {
                    adHeight = imageObject.getH();
                    adWidth = imageObject.getW();
                }

                responseAssetBuilder.img(imageObject);
            } else if (asset.getData() != null) {
                final boolean isDataAssetType = DATA_ASSET_TYPES.contains(asset.getData().getType());
                final DataObject dataObject = DataObject.builder()
                        .value(isDataAssetType ? decode(metaData.getTitle()) : StringUtils.EMPTY)
                        .label(isDataAssetType ? "desc" : StringUtils.EMPTY)
                        .build();
                responseAssetBuilder.data(dataObject);
            }

            responseAssetBuilder.id(asset.getId());
            responseAssets.add(responseAssetBuilder.build());
        }

        final List<String> clickTrackers = new ArrayList<>();
        final List<EventTracker> eventTrackers = new ArrayList<>();
        // dsp imp click tracking + imp click tracking
        if (content.getMonitorList() != null) {
            for (Monitor monitor : content.getMonitorList()) {
                if (CollectionUtils.isNotEmpty(monitor.getUrlList())) {
                    final MonitorEventType eventType = MonitorEventType.of(monitor.getEventType());
                    switch (eventType) {
                        case IMP -> {
                            eventTrackers.addAll(monitor.getUrlList().stream()
                                    .map(url -> EventTracker.builder().event(1).method(1).url(url).build()).toList());
                        }
                        case CLICK -> clickTrackers.addAll(monitor.getUrlList());
                    }
                }
            }
        }

        final Response response = Response.builder()
                .eventtrackers(eventTrackers)
                .assets(responseAssets)
                .link(Link.of(clickUrl, clickTrackers, null, null))
                .ver(HuaweiUtils.getIfNotBlank(nativePayload.getVer()).orElse(DEFAULT_NATIVE_VERSION))
                .build();

        try {
            final String adm = mapper.mapper().writeValueAsString(response).replace("\n", "");
            //todo: returns nullable width and height if no video or image present or no assets
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

    private String getClickUrl(Content content) {
        final MetaData metaData = content.getMetaData();
        final Optional<String> optionalIntent = HuaweiUtils.getIfNotBlank(metaData.getIntent())
                .map(intent -> URLDecoder.decode(intent, StandardCharsets.UTF_8));

        if (Objects.equals(content.getInteractionType(), APP_PROMOTION_INTERACTION_TYPE) && optionalIntent.isEmpty()) {
            throw new PreBidException("content.MetaData.Intent in huaweiads response is empty "
                    + "when interactiontype is appPromotion");
        }
        return optionalIntent
                //todo: why no decoding?
                .or(() -> HuaweiUtils.getIfNotBlank(metaData.getClickUrl()))
                .orElseThrow(() -> new PreBidException("content.MetaData.Intent and content.MetaData.ClickUrl "
                        + "in huaweiads response is empty"));
    }

    private static String convertDuration(Long duration) {
        return Optional.ofNullable(duration)
                .map(Duration::ofMillis)
                .map(LocalTime.MIDNIGHT::plus)
                .map(time -> time.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")))
                .orElseThrow(() -> new PreBidException("Content.MetaData.VideoInfo duration is empty"));
    }

    private static String decode(String value) {
        return HuaweiUtils.getIfNotBlank(value)
                .map(str -> URLDecoder.decode(str, StandardCharsets.UTF_8))
                .orElse(StringUtils.EMPTY);
    }

}
