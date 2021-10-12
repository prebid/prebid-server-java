package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.DataObject;
import com.iab.openrtb.request.ImageObject;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Request;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.huaweiads.model.HuaweiAd;
import org.prebid.server.bidder.huaweiads.model.HuaweiContent;
import org.prebid.server.bidder.huaweiads.model.HuaweiIcon;
import org.prebid.server.bidder.huaweiads.model.HuaweiImageInfo;
import org.prebid.server.bidder.huaweiads.model.HuaweiMediaFile;
import org.prebid.server.bidder.huaweiads.model.HuaweiMetadata;
import org.prebid.server.bidder.huaweiads.model.HuaweiMonitor;
import org.prebid.server.bidder.huaweiads.model.HuaweiNativeResponse;
import org.prebid.server.bidder.huaweiads.model.HuaweiResponse;
import org.prebid.server.bidder.huaweiads.model.HuaweiVideoInfo;
import org.prebid.server.bidder.huaweiads.model.xnative.DataAssetType;
import org.prebid.server.bidder.huaweiads.model.xnative.ImageAssetType;
import org.prebid.server.bidder.huaweiads.model.xnative.XnativeAsset;
import org.prebid.server.bidder.huaweiads.model.xnative.XnativeData;
import org.prebid.server.bidder.huaweiads.model.xnative.XnativeImage;
import org.prebid.server.bidder.huaweiads.model.xnative.XnativeLink;
import org.prebid.server.bidder.huaweiads.model.xnative.XnativeTitle;
import org.prebid.server.bidder.huaweiads.model.xnative.XnativeVideo;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuawei;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class HuaweiUtilResponse {

    private static final Set<Integer> PICTURE_CREATIVE_TYPES = new HashSet<>(Arrays.asList(1, 2, 3, 4, 7, 8, 10));
    private static final Set<Integer> VIDEO_CREATIVE_TYPES = new HashSet<>(Arrays.asList(9, 6, 11));

    private HuaweiUtilResponse() {
    }

    public static BidderResponse convertHuaweiAdsRespToBidderResp(HuaweiResponse huaweiAdsResponse,
                                                                  BidRequest bidRequest, JacksonMapper mapper,
                                                                  TypeReference<ExtPrebid<?, ExtImpHuawei>> tr) {
        final List<HuaweiAd> multiad = huaweiAdsResponse.getMultiad();

        if (multiad == null || multiad.isEmpty()) {
            throw new PreBidException("convertHuaweiAdsRespToBidderResp: multiad is null or length is 0");
        }

        final List<BidderBid> bids = new ArrayList<>();

        final Map<String, Imp> slotIdToImp = new HashMap<>();
        final Map<String, BidType> slotIdToMediaType = new HashMap<>();

        for (Imp imp : bidRequest.getImp()) {
            final ExtImpHuawei extImpHuawei = parseImpExt(imp, mapper, tr);
            String slotId = extImpHuawei.getSlotId();
            slotIdToImp.put(slotId, imp);
            slotIdToMediaType.put(slotId, resolveMediaType(imp));
        }

        for (HuaweiAd huaweiAd : multiad) {
            final String slotId = huaweiAd.getSlotId();

            if (StringUtils.isBlank(slotId)) {
                continue;
            }
            final Integer retcode = huaweiAd.getRetcode();
            if (retcode == null || retcode != 200) {
                continue;
            }

            final String newBidId = slotIdToImp.get(slotId) != null ? slotIdToImp.get(slotId).getId() : null;

            final List<HuaweiContent> contentList = huaweiAd.getContent();
            if (contentList == null) {
                continue;
            }

            for (HuaweiContent content : contentList) {
                final Bid bid = createBid(huaweiAd.getAdType(), content, slotIdToMediaType.get(slotId),
                        slotIdToImp.get(slotId), newBidId, mapper);
                bids.add(BidderBid.of(bid, slotIdToMediaType.get(slotId), "CNY"));
            }
        }
        return BidderResponse.of("huaweiads", BidderSeatBid.of(bids, null, null), 5000); // ??????? not sure
    }

    private static BidType resolveMediaType(Imp imp) {
        BidType mediaType = BidType.banner;
        if (imp.getVideo() != null) {
            mediaType = BidType.video;
        } else if (imp.getXNative() != null) {
            mediaType = BidType.xNative;
        } else if (imp.getAudio() != null) {
            mediaType = BidType.audio;
        }
        return mediaType;
    }

    public static ExtImpHuawei parseImpExt(Imp imp, JacksonMapper mapper,
                                           TypeReference<ExtPrebid<?, ExtImpHuawei>> typeReference) {
        ExtImpHuawei extImpHuawei;
        try {
            extImpHuawei = mapper.mapper().convertValue(imp.getExt(), typeReference).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("ExtImpHuaweiAds: missing bidder ext");
        }

        if (extImpHuawei.getSlotId() == null) {
            throw new PreBidException("ExtImpHuaweiAds: slotId is empty");
        }
        if (extImpHuawei.getAdType() == null) {
            throw new PreBidException("ExtImpHuaweiAds: adType is empty");
        }
        if (extImpHuawei.getPublisherId() == null) {
            throw new PreBidException("ExtImpHuaweiAds: publisherId is empty");
        }
        if (extImpHuawei.getKeyId() == null) {
            throw new PreBidException("ExtImpHuaweiAds: keyId is empty");
        }
        if (extImpHuawei.getSignKey() == null) {
            throw new PreBidException("ExtImpHuaweiAds: signKey is empty");
        }
        if (extImpHuawei.getIsTestAuthorization() == null) {
            throw new PreBidException("ExtImpHuaweiAds: IsTestAuthorization is empty");
        }
        return extImpHuawei;
    }

    private static Bid createBid(Integer adType, HuaweiContent content, BidType bidType, Imp imp,
                                 String bidId, JacksonMapper mapper) {

        final HuaweiMetadata metadata = ObjectUtils.defaultIfNull(content.getMetaData(),
                HuaweiMetadata.builder().build());
        final List<HuaweiMonitor> monitorList = ObjectUtils.defaultIfNull(content.getMonitor(), new ArrayList<>());
        final BigDecimal bidPrice = ObjectUtils.defaultIfNull(content.getPrice(), BigDecimal.ZERO); //Todo 775-golang, must be checked
        final String bidCrid = ObjectUtils.defaultIfNull(content.getContentid(), "");
        adType = ObjectUtils.defaultIfNull(adType, 0);
        bidType = ObjectUtils.defaultIfNull(bidType, BidType.audio);
        bidId = ObjectUtils.defaultIfNull(bidId, "");

        switch (bidType) {
            case banner:
                return resolveBannerBid(adType, content, metadata, bidType, imp, bidId,
                        monitorList, bidPrice, bidCrid);
            case xNative:
                return resolveNativeBid(adType, content, metadata, bidType, imp, bidId,
                        monitorList, bidPrice, bidCrid, mapper);
            case video:
                return resolveVideoBid(adType, content, metadata, bidType, imp, bidId,
                        monitorList, bidPrice, bidCrid);
            default:
                throw new PreBidException("no support bidtype: audio");
        }
    }

    private static Bid resolveBannerBid(Integer adType, HuaweiContent content, HuaweiMetadata metadata, BidType bidType,
                                        Imp imp, String bidId, List<HuaweiMonitor> monitorList,
                                        BigDecimal bidPrice, String bidCrid) {
        if (!Objects.equals(adType, BidTypes.BANNER_CODE.getValue())) {
            throw new PreBidException("resolveBannerBid: huaweiads response is not a banner ad");
        }

        Integer creativeType = ObjectUtils.defaultIfNull(content.getCreativetype(), 0);

        if (creativeType > 100) {
            creativeType = creativeType - 100;
        }

        if (PICTURE_CREATIVE_TYPES.contains(creativeType)) {
            return resolvePictureBid(content, metadata, bidId, monitorList, bidPrice, bidCrid);
        } else if (VIDEO_CREATIVE_TYPES.contains(creativeType)) {
            return resolveVideoBid(adType, content, metadata, bidType, imp, bidId,
                    monitorList, bidPrice, bidCrid);
        } else {
            throw new PreBidException("No banner support for this creativetype: " + creativeType);
        }
    }

    private static Bid resolvePictureBid(HuaweiContent content, HuaweiMetadata metadata, String bidId,
                                         List<HuaweiMonitor> monitorList, BigDecimal bidPrice, String bidCrid) {
        final HuaweiImageInfo imageInfo = getImageInfo(metadata);

        final int height = ObjectUtils.defaultIfNull(imageInfo.getHeight(), 0);
        final int width = ObjectUtils.defaultIfNull(imageInfo.getWidth(), 0);

        return Bid.builder()
                .id(bidId)
                .impid(bidId)
                .price(bidPrice)
                .crid(bidCrid)
                .adm(resolvePictureAdm(height, width, content, metadata, imageInfo))
                .w(width)
                .h(height)
                .adomain(List.of("huaweiads"))
                .nurl(getNurl(monitorList))
                .build();
    }

    private static HuaweiImageInfo getImageInfo(HuaweiMetadata metadata) {
        final List<HuaweiImageInfo> imageInfo = metadata.getImageInfo();
        if (imageInfo == null || imageInfo.isEmpty()) {
            throw new PreBidException("getFirstImageInfo: Metadata.ImageInfo is null or empty");
        }
        return imageInfo.get(0);
    }

    private static String resolvePictureAdm(int height, int width, HuaweiContent content, HuaweiMetadata metadata,
                                            HuaweiImageInfo firstImageInfo) {
        final String clickUrl = resolveClickUrl(metadata);
        final String imageInfoUrl = ObjectUtils.defaultIfNull(firstImageInfo.getUrl(), "");
        final String imageTitle = getDecodedValue(metadata.getTitle());
        final String dspClickTrackings = resolveDspClickTrackings(content);
        final String dspImpTrackings = resolveDspImpTrackings(content);

        return "<style> html, body  "
                + "{ margin: 0; padding: 0; width: 100%; height: 100%; vertical-align: middle; }  "
                + "html  "
                + "{ display: table; }  "
                + "body { display: table-cell; vertical-align: middle; text-align: center;"
                + " -webkit-text-size-adjust: none; }  "
                + "</style> "
                + "<span class=\"title-link advertiser_label\">" + imageTitle + "</span> "
                + "<a href='" + clickUrl + "' style=\"text-decoration:none\" "
                + "onclick=sendGetReq()> "
                + "<img src='" + imageInfoUrl + "' width='" + width + "' height='" + height + "'/> "
                + "</a> "
                + dspImpTrackings
                + "<script type=\"text/javascript\">"
                + "var dspClickTrackings = [" + dspClickTrackings + "];"
                + "function sendGetReq() {"
                + "sendSomeGetReq(dspClickTrackings)"
                + "}"
                + "function sendOneGetReq(url) {"
                + "var req = new XMLHttpRequest();"
                + "req.open('GET', url, true);"
                + "req.send(null);"
                + "}"
                + "function sendSomeGetReq(urls) {"
                + "for (var i = 0; i < urls.length; i++) {"
                + "sendOneGetReq(urls[i]);"
                + "}"
                + "}"
                + "</script>";
    }

    private static String resolveDspImpTrackings(HuaweiContent content) {
        final StringBuilder dspImpTrackings = new StringBuilder();
        for (String impTracking : resolveDspImpTrackingsFromMonitor(content)) {
            dspImpTrackings.append("<img height=\"1\" width=\"1\" src='");
            dspImpTrackings.append(impTracking);
            dspImpTrackings.append("' >  ");
        }
        return dspImpTrackings.toString();
    }

    private static String resolveDspClickTrackings(HuaweiContent content) {
        String dspClickTrackings = "";
        for (HuaweiMonitor monitor : content.getMonitor()) {
            if (monitor.getUrl().size() != 0) {
                if (monitor.getEventType().equals("imp")) {
                    dspClickTrackings = getStrings(monitor.getUrl());
                }
            }
        }
        return dspClickTrackings;
    }

    private static List<String> resolveDspImpTrackingsFromMonitor(HuaweiContent content) {
        List<String> dspImpTrackings = new ArrayList<>();
        for (HuaweiMonitor monitor : content.getMonitor()) {
            if (monitor.getUrl().size() != 0) {
                if (monitor.getEventType().equals("imp")) {
                    dspImpTrackings = monitor.getUrl();
                }
            }
        }
        return dspImpTrackings;
    }

    private static String getStrings(List<String> eles) {
        if (eles.size() == 0) {
            return "";
        }
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < eles.size(); i++) {
            output.append("\"").append(eles.get(i)).append("\"");
            if (i < eles.size() - 1) {
                output.append(",");
            }
        }
        return output.toString();
    }

    private static Bid resolveNativeBid(Integer adType, HuaweiContent content, HuaweiMetadata metadata, BidType bidType,
                                        Imp imp, String bidId, List<HuaweiMonitor> monitorList,
                                        BigDecimal bidPrice, String bidCrid, JacksonMapper mapper) {
        if (!Objects.equals(adType, BidTypes.NATIVE_CODE.getValue())) {
            throw new PreBidException("resolveNativeBid: response is not a native ad");
        }
        final Native xnative = imp.getXNative();
        if (xnative == null) {
            throw new PreBidException("resolveNativeBid: imp.Native is null");
        }
        final String request = xnative.getRequest();
        if (StringUtils.isBlank(request)) {
            throw new PreBidException("resolveNativeBid: imp.Native.Request is empty");
        }
        Request nativePayload;
        try {
            nativePayload = mapper.mapper().readValue(request, Request.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("resolveNativeBid: cant convert request to object");
        }

        final HuaweiNativeResponse nativeResult = HuaweiNativeResponse.builder()
                .impTrackers(resolveImpTrackers(content))
                .link(XnativeLink.builder()
                        .clickTrackers(resolveClickTrackers(content))
                        .url(resolveXnativeLinkUrl(metadata))
                        .build())
                .assets(resolveAssets(nativePayload, metadata, adType, content, bidType,
                        imp, bidId, monitorList, bidPrice, bidCrid))
                .ver(resolveVersion(nativePayload))
                .build();

        return Bid.builder()
                .id(bidId)
                .impid(bidId)
                .price(bidPrice)
                .crid(bidCrid)
                .adm(jsonEncode(nativeResult, mapper))
                .w(resolveNativeWidth(nativeResult))
                .h(resolveNativeHeight(nativeResult))
                .adomain(List.of("huaweiads"))
                .nurl(getNurl(monitorList))
                .build();
    }

    private static Integer resolveNativeWidth(HuaweiNativeResponse nativeResult) {
        ArrayList<XnativeAsset> assetsCopyList = new ArrayList<>(nativeResult.getAssets());
        Collections.reverse(assetsCopyList);
        XnativeImage xnativeImage = assetsCopyList.stream()
                .map(XnativeAsset::getImage)
                .filter(Objects::nonNull)
                .filter(image -> image.getH() != 0 && image.getW() != 0)
                .findFirst()
                .orElse(null);

        if (xnativeImage != null) {
            return xnativeImage.getW();
        }
        return 0;
    }

    private static Integer resolveNativeHeight(HuaweiNativeResponse nativeResult) {
        ArrayList<XnativeAsset> assetsCopyList = new ArrayList<>(nativeResult.getAssets());
        Collections.reverse(assetsCopyList);
        XnativeImage xnativeImage = assetsCopyList.stream()
                .map(XnativeAsset::getImage)
                .filter(Objects::nonNull)
                .filter(image -> image.getH() != 0 && image.getW() != 0)
                .findFirst()
                .orElse(null);

        if (xnativeImage != null) {
            return xnativeImage.getH();
        }
        return 0;
    }

    private static String jsonEncode(HuaweiNativeResponse response, JacksonMapper mapper) {
        String json;
        try {
            json = mapper.mapper().writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new PreBidException("jsonEncode: cant convert NativeResponse to String");
        }
        return json;
    }

    private static String resolveVersion(Request nativePayload) {
        final String versionDefault = "1.1";
        final String versionFromPayload = nativePayload.getVer();
        if (!StringUtils.isBlank(versionFromPayload)) {
            return versionFromPayload;
        }
        return versionDefault;
    }

    private static List<XnativeAsset> resolveAssets(Request nativePayload, HuaweiMetadata metadata, Integer adType,
                                                    HuaweiContent content, BidType bidType, Imp imp,
                                                    String newBidIdFromImp, List<HuaweiMonitor> monitorList,
                                                    BigDecimal bidPrice, String bidCrid) {
        final List<HuaweiIcon> icon = ObjectUtils.defaultIfNull(metadata.getIcon(), new ArrayList<>());
        final List<HuaweiImageInfo> imageInfo = ObjectUtils.defaultIfNull(metadata.getImageInfo(), new ArrayList<>());

        final List<XnativeAsset> nativeResultAssets = new ArrayList<>();
        final Iterator<HuaweiIcon> iconIterator = icon.iterator();
        final Iterator<HuaweiImageInfo> imageInfoIterator = imageInfo.iterator();

        for (Asset asset : nativePayload.getAssets()) {
            nativeResultAssets.add(XnativeAsset.builder()
                    .title(resolveAssetTitle(metadata, asset))
                    .video(resolveAssetVideo(asset, adType, content, metadata, bidType, imp, newBidIdFromImp,
                            monitorList, bidPrice, bidCrid))
                    .image(resolveAssetImage(asset, iconIterator, imageInfoIterator))
                    .data(resolveAssetData(asset, metadata))
                    .id(asset.getId()).build());
        }
        return nativeResultAssets;
    }

    private static XnativeData resolveAssetData(Asset asset, HuaweiMetadata metadata) {
        final DataObject dataFromAsset = asset.getData();
        if (dataFromAsset != null) {
            Integer dataType = dataFromAsset.getType();
            if (dataType.equals(DataAssetType.DATA_ASSET_TYPE_DESC.getValue())
                    || dataType.equals(DataAssetType.DATA_ASSET_TYPE_DESC_2.getValue())) {
                return XnativeData.builder()
                        .label("")
                        .value(getDecodedValue(metadata.getDescription())).build();
            }
        }
        return XnativeData.builder()
                .value("")
                .label("").build();
    }

    private static XnativeImage resolveAssetImage(Asset asset, Iterator<HuaweiIcon> iconIterator,
                                                  Iterator<HuaweiImageInfo> imageInfoIterator) {
        final ImageObject assetImg = asset.getImg();
        if (assetImg != null) {
            final Integer imageType = assetImg.getType();
            if (imageType == null) {
                throw new PreBidException("resolveAssetImage: Asset.Image.Type is null");
            }
            if (imageType.equals(ImageAssetType.IMAGE_ASSET_TYPE_ICON.getValue()) && iconIterator.hasNext()) {
                final HuaweiIcon icon = iconIterator.next();
                final Integer width = ObjectUtils.defaultIfNull(icon.getWidth(), 0);
                final Integer height = ObjectUtils.defaultIfNull(icon.getHeight(), 0);
                return XnativeImage.of(imageType, icon.getUrl(), width, height);
            } else if (imageInfoIterator.hasNext()) {
                HuaweiImageInfo imageInfo = imageInfoIterator.next();
                return XnativeImage.of(imageType, imageInfo.getUrl(), imageInfo.getWidth(), imageInfo.getHeight());
            }
            return XnativeImage.of(imageType, "", 0, 0);
        }
        return null;
    }

    private static XnativeVideo resolveAssetVideo(Asset asset, Integer adType, HuaweiContent content,
                                                  HuaweiMetadata metadata, BidType bidType, Imp imp,
                                                  String newBidIdFromImp, List<HuaweiMonitor> monitorList,
                                                  BigDecimal bidPrice, String bidCrid) {
        if (asset.getVideo() != null) {
            Bid bid = resolveVideoBid(adType, content, metadata, bidType, imp, newBidIdFromImp,
                    monitorList, bidPrice, bidCrid);
            return XnativeVideo.of(bid.getAdm());
        }
        return null;
    }

    private static XnativeTitle resolveAssetTitle(HuaweiMetadata metadata, Asset asset) {
        final String newTitleText = getDecodedValue(metadata.getTitle());
        if (asset.getTitle() != null) {
            return XnativeTitle.builder()
                    .text(newTitleText)
                    .len(newTitleText.length())
                    .build();
        }
        return null;
    }

    private static String resolveXnativeLinkUrl(HuaweiMetadata metadata) {
        String linkObjectUrl = "";
        final String clickUrlFromContent = metadata.getClickUrl();
        final String clickUrlFromContentIntent = metadata.getIntent();
        if (!StringUtils.isBlank(clickUrlFromContent)) {
            linkObjectUrl = clickUrlFromContent;
        } else if (!StringUtils.isBlank(clickUrlFromContentIntent)) {
            linkObjectUrl = clickUrlFromContentIntent;
        }
        return linkObjectUrl;
    }

    private static List<String> resolveClickTrackers(HuaweiContent content) {
        final List<String> clickTrackers = new ArrayList<>();
        final List<HuaweiMonitor> monitors = content.getMonitor();
        if (monitors != null) {
            for (HuaweiMonitor monitor : monitors) {
                List<String> url = monitor.getUrl();
                if (url == null) {
                    continue;
                }
                if (url.isEmpty()) {
                    continue;
                }
                String eventType = monitor.getEventType();
                if (eventType != null && eventType.equals("click")) {
                    clickTrackers.addAll(monitor.getUrl());
                }

            }
        }
        return clickTrackers;
    }

    private static List<String> resolveImpTrackers(HuaweiContent content) {
        final List<HuaweiMonitor> monitors = content.getMonitor();
        final List<String> impTrackers = new ArrayList<>();
        if (monitors != null) {
            for (HuaweiMonitor monitor : monitors) {
                List<String> url = monitor.getUrl();
                if (url == null) {
                    continue;
                }
                if (url.isEmpty()) {
                    continue;
                }
                String eventType = monitor.getEventType();
                if (eventType != null && eventType.equals("imp")) {
                    impTrackers.addAll(monitor.getUrl());
                }
            }
        }
        return impTrackers;
    }

    private static Bid resolveVideoBid(Integer adType, HuaweiContent content, HuaweiMetadata metadata, BidType bidType,
                                       Imp imp, String bidId, List<HuaweiMonitor> monitorList,
                                       BigDecimal bidPrice, String bidCrid) {
        final HuaweiMediaFile mediaFile = metadata.getMediaFile();
        final HuaweiVideoInfo videoInfo = metadata.getVideoInfo();
        final boolean adTypeIsRollCode = Objects.equals(adType, BidTypes.ROLL_CODE.getValue());

        final int width = resolveVideoWidth(adTypeIsRollCode, mediaFile, videoInfo, bidType, imp);
        final int height = resolveVideoHeight(adTypeIsRollCode, mediaFile, videoInfo, bidType, imp);
        final String adm = resolveVideoAdm(adTypeIsRollCode, content, metadata, mediaFile, videoInfo,
                monitorList, width, height);

        return Bid.builder()
                .id(bidId)
                .impid(bidId)
                .price(bidPrice)
                .crid(bidCrid)
                .adm(adm)
                .w(width)
                .h(height)
                .adomain(List.of("huaweiads"))
                .nurl(getNurl(monitorList))
                .build();
    }

    private static String getNurl(List<HuaweiMonitor> monitorList) {
        if (monitorList.isEmpty()) {
            return "";
        }
        for (HuaweiMonitor monitor : monitorList) {
            List<String> urls = monitor.getUrl();
            if (monitor.getEventType().equals("win") && !urls.isEmpty()) {
                return urls.get(0);
            }
        }
        return "";
    }

    private static String resolveVideoAdm(boolean adTypeIsRollCode, HuaweiContent content, HuaweiMetadata metadata,
                                          HuaweiMediaFile mediaFile, HuaweiVideoInfo videoInfo,
                                          List<HuaweiMonitor> monitorList, Integer width, Integer height) {
        final String contentId = ObjectUtils.defaultIfNull(content.getContentid(), "");
        final String adId = contentId; //TODO inline or not ?
        final String adTitle = getDecodedValue(metadata.getTitle());
        final String creativeId = contentId;
        final String clickUrl = resolveClickUrl(metadata);
        final String mime = resolveMime(adTypeIsRollCode, mediaFile);
        final String resourceUrl = resolveResourceUrl(adTypeIsRollCode, videoInfo, mediaFile);
        final String duration = resolveDuration(adTypeIsRollCode, metadata, videoInfo);

        final StringBuilder trackingEvents = new StringBuilder();
        final StringBuilder dspImpTrackingToStr = new StringBuilder();
        final StringBuilder dspClickTrackingToStr = new StringBuilder();
        final StringBuilder errorTrackingToStr = new StringBuilder();

        for (HuaweiMonitor monitor : monitorList) {
            List<String> urls = ObjectUtils.defaultIfNull(monitor.getUrl(), new ArrayList<>());

            if (urls.isEmpty()) {
                continue;
            }
            String eventType = ObjectUtils.defaultIfNull(monitor.getEventType(), "");

            resolveTracking(dspImpTrackingToStr, dspClickTrackingToStr, errorTrackingToStr, urls, eventType);
            resolveTrackingEvents(trackingEvents, eventType, urls);
        }

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<VAST version=\"3.0\">"
                + "<Ad id=\"" + adId + "\"><InLine>"
                + "<AdSystem>HuaweiAds</AdSystem>"
                + "<AdTitle>" + adTitle + "</AdTitle>"
                + errorTrackingToStr + dspImpTrackingToStr
                + "<Creatives>"
                + "<Creative adId=\"" + adId + "\" id=\"" + creativeId + "\">"
                + "<Linear>"
                + "<Duration>" + duration + "</Duration>"
                + "<TrackingEvents>" + trackingEvents + "</TrackingEvents>"
                + "<VideoClicks>"
                + "<ClickThrough><![CDATA[" + clickUrl + "]]></ClickThrough>"
                + dspClickTrackingToStr
                + "</VideoClicks>"
                + "<MediaFiles>"
                + "<MediaFile delivery=\"progressive\" type=\"" + mime + "\" width=\"" + height + "\" "
                + "height=\"" + width + "\" scalable=\"true\" maintainAspectRatio=\"true\"> "
                + "<![CDATA[" + resourceUrl + "]]>"
                + "</MediaFile>"
                + "</MediaFiles>"
                + "</Linear>"
                + "</Creative>"
                + "</Creatives>"
                + "</InLine>"
                + "</Ad>"
                + "</VAST>";
    }

    private static void resolveTracking(StringBuilder dspImpTracking2Str, StringBuilder dspClickTracking2Str,
                                        StringBuilder errorTracking2Str, List<String> urlsFromMonitor,
                                        String eventType) {
        switch (eventType) {
            case "vastError":
                errorTracking2Str.append(getVastImpClickErrorTrackingUrls(urlsFromMonitor, "vastError"));
                break;
            case "imp":
                dspImpTracking2Str.append(getVastImpClickErrorTrackingUrls(urlsFromMonitor, "imp"));
                break;
            case "click":
                dspClickTracking2Str.append(getVastImpClickErrorTrackingUrls(urlsFromMonitor, "click"));
                break;
            default:
                break;
        }
    }

    private static String getVastImpClickErrorTrackingUrls(List<String> urls, String eventType) {
        final StringBuilder trackingUrls = new StringBuilder();
        for (String url : urls) {
            if (eventType.equals("click")) {
                trackingUrls.append("<ClickTracking><![CDATA[");
                trackingUrls.append(url);
                trackingUrls.append("]]></ClickTracking>");
            } else if (eventType.equals("imp")) {
                trackingUrls.append("<Impression><![CDATA[");
                trackingUrls.append(url);
                trackingUrls.append("]]></Impression>");
            } else if (eventType.equals("vastError")) {
                trackingUrls.append("<Error><![CDATA[");
                trackingUrls.append(url);
                trackingUrls.append("&et=[ERRORCODE]]]></Error>");
            }
        }
        return trackingUrls.toString();
    }

    private static void resolveTrackingEvents(StringBuilder trackingEvents, String eventType,
                                              List<String> urlsFromMonitor) {
        final String event = resolveEvent(eventType);
        if (!StringUtils.isBlank(event)) {
            if (!event.equals("skip&closeLinear")) {
                trackingEvents.append(getVastEventTrackingUrls(urlsFromMonitor, event));
            } else {
                trackingEvents.append(getVastEventTrackingUrls(urlsFromMonitor, "skip&closeLinear"));
            }
        }
    }

    private static String getVastEventTrackingUrls(List<String> urls, String eventType) {
        final StringBuilder trackingUrls = new StringBuilder();
        for (String eventUrl : urls) {
            if (eventType.equals("skip&closeLinear")) {
                trackingUrls.append("<Tracking event=\"skip\"><![CDATA[");
                trackingUrls.append(eventUrl);
                trackingUrls.append("]]></Tracking><Tracking event=\"closeLinear\"><![CDATA[");
                trackingUrls.append(eventUrl);
                trackingUrls.append("]]></Tracking>");
            } else {
                trackingUrls.append("<Tracking event=\"");
                trackingUrls.append(eventType);
                trackingUrls.append("\"><![CDATA[");
                trackingUrls.append(eventUrl);
                trackingUrls.append("]]></Tracking>");
            }
        }
        return trackingUrls.toString();
    }

    private static String resolveEvent(String eventType) {
        String event = "";
        switch (eventType) {
            case "userclose":
                event = "skip&closeLinear";
                break;
            case "playStart":
                event = "start";
                break;
            case "playEnd":
                event = "complete";
                break;
            case "playResume":
                event = "resume";
                break;
            case "playPause":
                event = "pause";
                break;
            case "soundClickOff":
                event = "mute";
                break;
            case "soundClickOn":
                event = "unmute";
                break;
            default:
                event = "";
                break;
        }
        return event;
    }

    private static Integer resolveVideoHeight(boolean adTypeIsRollCode, HuaweiMediaFile mediaFile,
                                          HuaweiVideoInfo videoInfo, BidType bidType, Imp imp) {
        if (adTypeIsRollCode) {
            checkOnNull(mediaFile, "resolveVideoHeight: cannot get height");
            return mediaFile.getHeight();
        } else {
            checkOnNull(videoInfo, "resolveVideoHeight: cannot get height");
            int heightFromVideoInfo = videoInfo.getHeight();
            if (heightFromVideoInfo != 0) {
                return heightFromVideoInfo;
            } else if (bidType == BidType.video) {
                checkOnNull(imp, "resolveVideoHeight: cannot get height");
                Video videoFromImp = imp.getVideo();
                if (videoFromImp != null) {
                    Integer heightFromVideo = videoFromImp.getH();
                    if (heightFromVideo != null && heightFromVideo != 0) {
                        return heightFromVideo;
                    }
                }
            }
        }
        throw new PreBidException("resolveVideoHeight: cannot get height");
    }

    private static Integer resolveVideoWidth(boolean adTypeIsRollCode, HuaweiMediaFile mediaFile,
                                             HuaweiVideoInfo videoInfo, BidType bidType, Imp imp) {
        if (adTypeIsRollCode) {
            checkOnNull(mediaFile, "resolveVideoWidth: cannot get width height");
            return mediaFile.getWidth();
        } else {
            checkOnNull(videoInfo, "resolveVideoWidth: cannot get width height");
            Integer widthFromVideoInfo = videoInfo.getWidth();
            if (widthFromVideoInfo != 0) {
                return widthFromVideoInfo;
            } else if (bidType == BidType.video) {
                checkOnNull(imp, "resolveVideoWidth: cannot get width height");
                Video videoFromImp = imp.getVideo();
                if (videoFromImp != null) {
                    Integer widthFromVideo = videoFromImp.getW();
                    if (widthFromVideo != null && widthFromVideo != 0) {
                        return widthFromVideo;
                    }
                }
            }
        }
        throw new PreBidException("resolveVideoWidth: cannot get width");
    }

    private static String resolveDuration(boolean adTypeIsRollCode, HuaweiMetadata metadata,
                                          HuaweiVideoInfo videoInfo) {
        String duration = "";
        if (adTypeIsRollCode) {
            duration = getDuration(metadata.getDuration());
        } else {
            if (videoInfo == null) {
                return duration;
            }
            duration = getDuration(videoInfo.getVideoDuration());
        }
        return duration;
    }

    private static String resolveResourceUrl(boolean adTypeIsRollCode, HuaweiVideoInfo videoInfo,
                                             HuaweiMediaFile mediaFile) {
        String resourceUrl;
        if (adTypeIsRollCode) {
            checkOnNull(mediaFile, "resolveResourceUrl: content.MetaData.MediaFile is empty");
            String urlFromMediaFile = mediaFile.getUrl();
            if (!StringUtils.isBlank(urlFromMediaFile)) {
                resourceUrl = urlFromMediaFile;
            } else {
                throw new PreBidException("resolveResourceUrl: content.MetaData.MediaFile.Url is empty");
            }
        } else {
            checkOnNull(videoInfo, "resolveResourceUrl: content.MetaData.VideoInfo is empty");
            String videoDownloadUrlFromVideoInfo = videoInfo.getVideoDownloadUrl();
            if (!StringUtils.isBlank(videoDownloadUrlFromVideoInfo)) {
                resourceUrl = videoDownloadUrlFromVideoInfo;
            } else {
                throw new PreBidException("resolveResourceUrl: content.MetaData.VideoInfo.VideoDownloadUrl is empty");
            }
        }
        return resourceUrl;
    }

    private static String resolveMime(boolean adTypeIsRollCode, HuaweiMediaFile mediaFile) {
        String mime = "video/mp4";
        if (mediaFile == null) {
            return mime;
        }
        if (adTypeIsRollCode) {
            String mimeFromMediaFile = mediaFile.getMime();
            if (!StringUtils.isBlank(mimeFromMediaFile)) {
                mime = mimeFromMediaFile;
            }
        }
        return mime;
    }

    private static String resolveClickUrl(HuaweiMetadata metadata) {
        String clickUrl = "";
        final String clickUrlFromContent = metadata.getClickUrl();
        final String clickUrlFromContentIntent = metadata.getIntent();
        if (!StringUtils.isBlank(clickUrlFromContent)) {
            clickUrl = clickUrlFromContent;
        } else if (!StringUtils.isBlank(clickUrlFromContentIntent)) {
            clickUrl = getDecodedValue(clickUrlFromContentIntent);
        }
        return clickUrl;
    }

    private static String getDuration(Integer duration) {
        if (duration == null) {
            return "";
        }
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("15:04:05.000");
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(duration);
        return simpleDateFormat.format(calendar.getTime());
    }

    private static String getDecodedValue(String encodedString) {
        return encodedString != null ? URLDecoder.decode(encodedString) : "";
    }

    private static void checkOnNull(Object object, String errorMessage) {
        if (object == null) {
            throw new PreBidException(errorMessage);
        }
    }
}

