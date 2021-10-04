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
import lombok.AllArgsConstructor;
import lombok.Value;
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
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuaweiAds;
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

    //table of const codes for bidTypes
    private static final int BANNER_CODE = 8;
    private static final int NATIVE_CODE = 3;
    private static final int ROLL_CODE = 60;

    private static final Set<Integer> PICTURE_CREATIVE_TYPES = new HashSet<>(Arrays.asList(1, 2, 3, 4, 7, 8, 10));
    private static final Set<Integer> VIDEO_CREATIVE_TYPES = new HashSet<>(Arrays.asList(9, 6, 11));

    private HuaweiUtilResponse() {
    }

    public static BidderResponse convertHuaweiAdsRespToBidderResp(HuaweiResponse huaweiAdsResponse,
                                                                  BidRequest bidRequest, JacksonMapper mapper,
                                                                  TypeReference<ExtPrebid<?, ExtImpHuaweiAds>> tr) {
        final List<HuaweiAd> multiad = huaweiAdsResponse.getMultiad();
        if (multiad == null) {
            throw new PreBidException("convertHuaweiAdsResp2BidderResp: multiad is null");
        }

        final int multiAdLength = multiad.size();
        if (multiAdLength == 0) {
            throw new PreBidException("convertHuaweiAdsResp2BidderResp: multiad length is 0");
        }

        final List<BidderBid> bids = new ArrayList<>();

        final Map<String, Imp> slotIdToImp = new HashMap<>();
        final Map<String, BidType> slotIdToMediaType = new HashMap<>();

        for (Imp imp : bidRequest.getImp()) {
            final ExtImpHuaweiAds extImpHuaweiAds = parseImpExt(imp, mapper, tr);
            slotIdToImp.put(extImpHuaweiAds.getSlotId(), imp);
            slotIdToMediaType.put(extImpHuaweiAds.getSlotId(), resolveMediaType(imp));
        }

        for (HuaweiAd huaweiAd : multiad) {
            final String slotId = huaweiAd.getSlotId();
            if (slotId == null) {
                throw new PreBidException("convertHuaweiAdsResp2BidderResp: slotId is null");
            }

            final String newBidIdFromImp = slotIdToImp.get(slotId) != null ? slotIdToImp.get(slotId).getId() : null;
            if (StringUtils.isBlank(newBidIdFromImp)) {
                continue; //if imp id is blank
            }
            if (huaweiAd.getRetcode() != 200) {
                continue;
            }

            final List<HuaweiContent> contentList = huaweiAd.getContent();
            if (contentList == null) {
                throw new PreBidException("convertHuaweiAdsResp2BidderResp: multiad length is 0");
            }

            for (HuaweiContent content : contentList) {
                if (content == null) {
                    throw new PreBidException("convertHuaweiAdsResp2BidderResp: content is null");
                }
                final Bid bid = createBid(huaweiAd.getAdType(), content, slotIdToMediaType.get(slotId),
                        slotIdToImp.get(slotId), newBidIdFromImp, mapper);
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

    public static ExtImpHuaweiAds parseImpExt(Imp imp, JacksonMapper mapper,
                                              TypeReference<ExtPrebid<?, ExtImpHuaweiAds>> typeReference) {
        ExtImpHuaweiAds extImpHuaweiAds;
        try {
            extImpHuaweiAds = mapper.mapper().convertValue(imp.getExt(), typeReference).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("ExtImpHuaweiAds: missing bidder ext");
        }

        if (extImpHuaweiAds.getSlotId() == null) {
            throw new PreBidException("ExtImpHuaweiAds: slotId is empty");
        }
        if (extImpHuaweiAds.getAdtype() == null) {
            throw new PreBidException("ExtImpHuaweiAds: adType is empty");
        }
        if (extImpHuaweiAds.getPublisherId() == null) {
            throw new PreBidException("ExtImpHuaweiAds: publisherId is empty");
        }
        if (extImpHuaweiAds.getKeyId() == null) {
            throw new PreBidException("ExtImpHuaweiAds: keyId is empty");
        }
        if (extImpHuaweiAds.getSignKey() == null) {
            throw new PreBidException("ExtImpHuaweiAds: signKey is empty");
        }
        if (extImpHuaweiAds.getIsTestAuthorization() == null) {
            throw new PreBidException("ExtImpHuaweiAds: IsTestAuthorization is empty");
        }
        return extImpHuaweiAds;
    }

    private static Bid createBid(Integer adType, HuaweiContent content, BidType bidType, Imp imp,
                                 String newBidIdFromImp, JacksonMapper mapper) {
        final HuaweiMetadata metadata = content.getMetaData();
        if (metadata == null) {
            throw new PreBidException("createBid: Content.MetaData is null");
        }
        final List<HuaweiMonitor> monitorList = content.getMonitor();
        if (monitorList == null) {
            throw new PreBidException("createBid: Content.Monitor is null");
        }
        final BigDecimal bidPrice = content.getPrice();
        if (bidPrice == null) {
            throw new PreBidException("createBid: Content.Price is null");
        }
        final String bidCrid = content.getContentid();
        if (bidCrid == null) {
            throw new PreBidException("createBid: Content.ContentId is null");
        }
        switch (bidType) {
            case banner:
                return resolveBannerBid(adType, content, metadata, bidType, imp, newBidIdFromImp,
                        monitorList, bidPrice, bidCrid);
            case xNative:
                return resolveNativeBid(adType, content, metadata, bidType, imp, newBidIdFromImp,
                        monitorList, bidPrice, bidCrid, mapper);
            case video:
                return resolveVideoBid(adType, content, metadata, bidType, imp, newBidIdFromImp,
                        monitorList, bidPrice, bidCrid);
            default:
                throw new PreBidException("no support bidtype: audio");
        }
    }

    private static Bid resolveBannerBid(Integer adType, HuaweiContent content, HuaweiMetadata metadata, BidType bidType,
                                        Imp imp, String newBidIdFromImp, List<HuaweiMonitor> monitorList,
                                        BigDecimal bidPrice, String bidCrid) {
        if (adType != BANNER_CODE) {
            throw new PreBidException("resolveBannerBid: huaweiads response is not a banner ad");
        }
        int creativeType = content.getCreativetype();
        if (creativeType > 100) {
            creativeType = creativeType - 100;
        }

        if (PICTURE_CREATIVE_TYPES.contains(creativeType)) {
            return extractAdmPicture(content, metadata, newBidIdFromImp, monitorList, bidPrice, bidCrid);
        } else if (VIDEO_CREATIVE_TYPES.contains(creativeType)) {
            return resolveVideoBid(adType, content, metadata, bidType, imp, newBidIdFromImp,
                    monitorList, bidPrice, bidCrid);
        } else {
            throw new PreBidException("no banner support creativetype");
        }
    }

    private static Bid extractAdmPicture(HuaweiContent content, HuaweiMetadata metadata, String newBidIdFromImp,
                                         List<HuaweiMonitor> monitorList, BigDecimal bidPrice, String bidCrid) {
        final HuaweiImageInfo firstImageInfo = getFirstImageInfo(metadata);

        final int height = firstImageInfo.getHeight();
        final int width = firstImageInfo.getWidth();

        return Bid.builder()
                .id(newBidIdFromImp)
                .impid(newBidIdFromImp)
                .price(bidPrice)
                .crid(bidCrid)
                .adm(resolvePictureAdm(height, width, content, metadata, firstImageInfo))
                .w(width)
                .h(height)
                .adomain(List.of("huaweiads"))
                .nurl(getNurl(monitorList))
                .build();
    }

    private static HuaweiImageInfo getFirstImageInfo(HuaweiMetadata metadata) {
        final List<HuaweiImageInfo> imageInfo = metadata.getImageInfo();
        if (imageInfo == null) {
            throw new PreBidException("getFirstImageInfo: Metadata.ImageInfo is null");
        }
        if (imageInfo.isEmpty()) {
            throw new PreBidException("getFirstImageInfo: Metadata.ImageInfo is empty");
        }
        return imageInfo.get(0);
    }

    private static String resolvePictureAdm(int height, int width, HuaweiContent content, HuaweiMetadata metadata,
                                            HuaweiImageInfo firstImageInfo) {
        final String clickUrl = resolveClickUrl(metadata);
        final String imageInfoUrl = firstImageInfo.getUrl();
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
            output.append("\"" + eles.get(i) + "\"");
            if (i < eles.size() - 1) {
                output.append(",");
            }
        }
        return output.toString();
    }

    private static Bid resolveNativeBid(Integer adType, HuaweiContent content, HuaweiMetadata metadata, BidType bidType,
                                        Imp imp, String newBidIdFromImp, List<HuaweiMonitor> monitorList,
                                        BigDecimal bidPrice, String bidCrid, JacksonMapper mapper) {
        if (adType != NATIVE_CODE) {
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
                        imp, newBidIdFromImp, monitorList, bidPrice, bidCrid))
                .ver(resolveVersion(nativePayload))
                .build();

        return Bid.builder()
                .id(newBidIdFromImp)
                .impid(newBidIdFromImp)
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
        final List<HuaweiIcon> icon = metadata.getIcon();
        final List<HuaweiImageInfo> imageInfo = metadata.getImageInfo();
        if (icon == null) {
            throw new PreBidException("resolveAssets: Metadata.Icon is null");
        }
        if (imageInfo == null) {
            throw new PreBidException("resolveAssets: Metadata.ImageInfo is null");
        }

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
            XnativeData data = new XnativeData();
            data.setLabel("");
            data.setValue("");
            Integer dataType = dataFromAsset.getType();
            if (dataType.equals(DataAssetType.dataAssetTypeDesc) || dataType.equals(DataAssetType.dataAssetTypeDesc2)) {
                data.setLabel("");
                data.setValue(getDecodedValue(metadata.getDescription()));
            }
            return data;
        }
        return null;
    }

    private static XnativeImage resolveAssetImage(Asset asset, Iterator<HuaweiIcon> iconIterator,
                                                  Iterator<HuaweiImageInfo> imageInfoIterator) {
        final ImageObject assetImg = asset.getImg();
        if (assetImg != null) {
            final Integer imageType = assetImg.getType();
            if (imageType == null) {
                throw new PreBidException("resolveAssetImage: Asset.Image.Type is null");
            }
            if (imageType.equals(ImageAssetType.imageAssetTypeIcon) && iconIterator.hasNext()) {
                final HuaweiIcon icon = iconIterator.next();
                return XnativeImage.of(imageType, icon.getUrl(), icon.getWidth(), icon.getHeight());
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
            return XnativeVideo.builder().vastTag(bid.getAdm()).build();
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
                                       Imp imp, String newBidIdFromImp, List<HuaweiMonitor> monitorList,
                                       BigDecimal bidPrice, String bidCrid) {
        final HuaweiMediaFile mediaFile = metadata.getMediaFile();
        if (mediaFile == null) {
            throw new PreBidException("resolveVideoBid: Content.MetaData.MediaFile is null");
        }
        final HuaweiVideoInfo videoInfo = metadata.getVideoInfo();
        if (videoInfo == null) {
            throw new PreBidException("resolveVideoBid: Content.MetaData.VideoInfo is null");
        }
        final boolean adTypeIsRollCode = adType == ROLL_CODE;

        final int width = resolveVideoWidth(adTypeIsRollCode, mediaFile, videoInfo, bidType, imp);
        final int height = resolveVideoHeight(adTypeIsRollCode, mediaFile, videoInfo, bidType, imp);
        final String adm = resolveVideoAdm(adTypeIsRollCode, content, metadata, mediaFile, videoInfo,
                monitorList, width, height);

        return Bid.builder()
                .id(newBidIdFromImp)
                .impid(newBidIdFromImp)
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
                                          List<HuaweiMonitor> monitorList, int width, int height) {
        final String contentId = content.getContentid();
        if (StringUtils.isBlank(contentId)) {
            throw new PreBidException("resolveVideoAdm: Content.ContentId is null");
        }
        final String adId = contentId; //TODO inline or not ?
        final String adTitle = getDecodedValue(metadata.getTitle());
        final String creativeId = contentId;
        final String clickUrl = resolveClickUrl(metadata);
        final String mime = resolveMime(adTypeIsRollCode, content, metadata, mediaFile);
        final String resourceUrl = resolveResourceUrl(adTypeIsRollCode, videoInfo, mediaFile);
        final String duration = resolveDuration(adTypeIsRollCode, metadata, videoInfo);

        final StringBuilder trackingEvents = new StringBuilder();
        final StringBuilder dspImpTrackingToStr = new StringBuilder();
        final StringBuilder dspClickTrackingToStr = new StringBuilder();
        final StringBuilder errorTrackingToStr = new StringBuilder();

        for (HuaweiMonitor monitor : monitorList) {
            List<String> urls = monitor.getUrl();
            if (urls == null) {
                throw new PreBidException("resolveVideoAdm: Content.Monitor is null");
            }
            if (urls.size() == 0) {
                continue;
            }
            String eventType = monitor.getEventType();
            if (eventType == null) {
                throw new PreBidException("resolveVideoAdm: Content.Monitor.EventType is null");
            }
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

    private static int resolveVideoHeight(boolean adTypeIsRollCode, HuaweiMediaFile mediaFile,
                                          HuaweiVideoInfo videoInfo, BidType bidType, Imp imp) {
        if (adTypeIsRollCode) {
            return mediaFile.getHeight();
        } else {
            int heightFromVideoInfo = videoInfo.getHeight();
            if (heightFromVideoInfo != 0) {
                return heightFromVideoInfo;
            } else if (bidType == BidType.video) {
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

    private static int resolveVideoWidth(boolean adTypeIsRollCode, HuaweiMediaFile mediaFile,
                                         HuaweiVideoInfo videoInfo, BidType bidType, Imp imp) {
        if (adTypeIsRollCode) {
            return mediaFile.getWidth();
        } else {
            int widthFromVideoInfo = videoInfo.getWidth();
            if (widthFromVideoInfo != 0) {
                return widthFromVideoInfo;
            } else if (bidType == BidType.video) {
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
        String duration;
        if (adTypeIsRollCode) {
            duration = getDuration(metadata.getDuration());
        } else {
            duration = getDuration(videoInfo.getVideoDuration());
        }
        return duration;
    }

    private static String resolveResourceUrl(boolean adTypeIsRollCode, HuaweiVideoInfo videoInfo,
                                             HuaweiMediaFile mediaFile) {
        final String resourceUrl;
        if (adTypeIsRollCode) {
            String urlFromMediaFile = mediaFile.getUrl();
            if (!StringUtils.isBlank(urlFromMediaFile)) {
                resourceUrl = urlFromMediaFile;
            } else {
                throw new PreBidException("resolveResourceUrl: Content.MetaData.MediaFile.Url is empty");
            }
        } else {
            String videoDownloadUrlFromVideoInfo = videoInfo.getVideoDownloadUrl();
            if (!StringUtils.isBlank(videoDownloadUrlFromVideoInfo)) {
                resourceUrl = videoDownloadUrlFromVideoInfo;
            } else {
                throw new PreBidException("resolveResourceUrl: content.MetaData.VideoInfo.VideoDownloadUrl is empty");
            }
        }
        return resourceUrl;
    }

    private static String resolveMime(boolean adTypeIsRollCode, HuaweiContent content, HuaweiMetadata metadata,
                                      HuaweiMediaFile mediaFile) {
        String mime = "video/mp4";
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

    private static String getDuration(int duration) {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("15:04:05.000"); // is it ok ?
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(duration);
        return simpleDateFormat.format(calendar.getTime());
    }

    private static String getDecodedValue(String encodedString) {
        return encodedString != null ? URLDecoder.decode(encodedString) : "";
    }

    @Value
    @AllArgsConstructor(staticName = "of")
    static class AssetImageResult {
        int iconIndex;
        int imgIndex;
        XnativeImage xnativeImage;
    }
}

