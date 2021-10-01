package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.*;
import com.iab.openrtb.response.Bid;


import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.huaweiads.model.*;
import org.prebid.server.bidder.huaweiads.model.xnative.*;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuaweiAds;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.*;

public class HuaweiUtil {
    //table of const codes for bidTypes
    private static final int BANNER_CODE = 8;
    private static final int NATIVE_CODE = 3;
    private static final int ROLL_CODE = 60;

    private static final Set<Integer> PICTURE_CREATIVE_TYPES = new HashSet<>(Arrays.asList(1, 2, 3, 4, 7, 8, 10));
    private static final Set<Integer> VIDEO_CREATIVE_TYPES = new HashSet<>(Arrays.asList(9, 6, 11 ));


    public static BidderResponse convertHuaweiAdsRespToBidderResp(HuaweiResponse huaweiAdsResponse, BidRequest bidRequest, JacksonMapper mapper,
                                                           TypeReference<ExtPrebid<?, ExtImp>> IMP_EXT_TYPE_REFERENCE,
                                                           TypeReference<ExtPrebid<?, ExtImpHuaweiAds>> HUAWEIADS_EXT_TYPE_REFERENCE) {
        List<HuaweiAd> multiad = huaweiAdsResponse.getMultiad();
        if(multiad == null) {
            throw new PreBidException("convertHuaweiAdsResp2BidderResp: multiad is null");
        }

        int multiAdLength = multiad.size();
        if(multiAdLength == 0) {
            throw new PreBidException("convertHuaweiAdsResp2BidderResp: multiad length is 0, get no ads from huawei side");
        }

        List<BidderBid> bids = new ArrayList<>();

        final Map<String, Imp> slotIdToImp = new HashMap<>();
        final Map<String, BidType> slotIdToMediaType = new HashMap<> ();

        for(Imp imp : bidRequest.getImp()) {
            ExtImpHuaweiAds extImpHuaweiAds = parseImpExt(imp, mapper, IMP_EXT_TYPE_REFERENCE, HUAWEIADS_EXT_TYPE_REFERENCE);
            slotIdToImp.put(extImpHuaweiAds.getSlotId(), imp);
            slotIdToMediaType.put(extImpHuaweiAds.getSlotId(), resolveMediaType(imp));
        }

        for(HuaweiAd huaweiAd : multiad) {
            String slotId = huaweiAd.getSlotId();
            if(slotId == null) {
                throw new PreBidException("convertHuaweiAdsResp2BidderResp: slotId is null");
            }

            String newBidIdFromImp = slotIdToImp.get(slotId) != null ? slotIdToImp.get(slotId).getId() : null;
            if( StringUtils.isBlank(newBidIdFromImp)) {
                continue; //if imp id is blank
            }
            if (huaweiAd.getRetcode() != 200) {
                continue;
            }

            List<HuaweiContent> contentList = huaweiAd.getContent();
            if(contentList == null) {
                throw new PreBidException("convertHuaweiAdsResp2BidderResp: multiad length is 0, get no ads from huawei side");
            }

            for(HuaweiContent content : contentList) {
                if(content == null) {
                    throw new PreBidException("convertHuaweiAdsResp2BidderResp: content is null");
                }
                Bid bid = createBid(huaweiAd.getAdType(), content, slotIdToMediaType.get(slotId), slotIdToImp.get(slotId), newBidIdFromImp, mapper);
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
                                               TypeReference<ExtPrebid<?, ExtImp>> IMP_EXT_TYPE_REFERENCE,
                                               TypeReference<ExtPrebid<?, ExtImpHuaweiAds>> HUAWEIADS_EXT_TYPE_REFERENCE) {
        ExtImpHuaweiAds extImpHuaweiAds;
        try {
            extImpHuaweiAds = mapper.mapper().convertValue(imp.getExt(), HUAWEIADS_EXT_TYPE_REFERENCE).getBidder();
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

    private static Bid createBid(Integer adType, HuaweiContent content, BidType bidType, Imp imp, String newBidIdFromImp, JacksonMapper mapper) {
        HuaweiMetadata metadata = content.getMetaData();
        if(metadata == null) {
            throw new PreBidException("createBid: Content.MetaData is null");
        }
        List<HuaweiMonitor> monitorList = content.getMonitor();
        if(monitorList == null) {
            throw new PreBidException("createBid: Content.Monitor is null");
        }
        BigDecimal bidPrice = content.getPrice();
        if(bidPrice == null) {
            throw new PreBidException("createBid: Content.Price is null");
        }
        String bidCrid = content.getContentid();
        if(bidCrid == null) {
            throw new PreBidException("createBid: Content.ContentId is null");
        }
        switch (bidType) {
            case banner:
                return resolveBannerBid(adType, content, metadata, bidType, imp, newBidIdFromImp, monitorList, bidPrice, bidCrid);
            case xNative:
                return resolveNativeBid(adType, content, metadata, bidType, imp, newBidIdFromImp, monitorList, bidPrice, bidCrid, mapper);
            case video:
                return resolveVideoBid(adType, content, metadata, bidType, imp, newBidIdFromImp, monitorList, bidPrice, bidCrid);
            default:
                throw new PreBidException("no support bidtype: audio");
        }
    }

    private static Bid resolveBannerBid(Integer adType, HuaweiContent content, HuaweiMetadata metadata, BidType bidType, Imp imp,
                                        String newBidIdFromImp, List<HuaweiMonitor> monitorList, BigDecimal bidPrice, String bidCrid) {
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
            return resolveVideoBid(adType, content, metadata, bidType, imp, newBidIdFromImp, monitorList, bidPrice, bidCrid);
        } else {
            throw new PreBidException("no banner support creativetype");
        }
    }

    private static Bid extractAdmPicture(HuaweiContent content, HuaweiMetadata metadata, String newBidIdFromImp, List<HuaweiMonitor> monitorList, BigDecimal bidPrice, String bidCrid) {
        HuaweiImageInfo firstImageInfo = getFirstImageInfo(metadata);

        int height = firstImageInfo.getHeight();
        int width = firstImageInfo.getWidth();

        return Bid.builder()
                .id(newBidIdFromImp)
                .impid(newBidIdFromImp)
                .price(bidPrice)
                .crid(bidCrid)
                .adm(resolvePictureAdm(height,width, content, metadata, firstImageInfo))
                .w(width)
                .h(height)
                .adomain(List.of("huaweiads"))
                .nurl(getNurl(monitorList))
                .build();
    }

    private static HuaweiImageInfo getFirstImageInfo(HuaweiMetadata metadata) {
        List<HuaweiImageInfo> imageInfo = metadata.getImageInfo();
        if(imageInfo == null) {
            throw new PreBidException("getFirstImageInfo: Metadata.ImageInfo is null");
        }
        if(imageInfo.isEmpty()) {
            throw new PreBidException("getFirstImageInfo: Metadata.ImageInfo is empty");
        }
        return imageInfo.get(0);
    }

    private static String resolvePictureAdm(int height, int width, HuaweiContent content, HuaweiMetadata metadata, HuaweiImageInfo firstImageInfo) {
        String clickUrl = resolveClickUrl(metadata);
        String imageInfoUrl = firstImageInfo.getUrl();
        String imageTitle = getDecodedValue(metadata.getTitle());
        String dspClickTrackings = resolveDspClickTrackings(content);
        String dspImpTrackings = resolveDspImpTrackings(content);

         return  "<style> html, body  "
                + "{ margin: 0; padding: 0; width: 100%; height: 100%; vertical-align: middle; }  "
                + "html  "
                + "{ display: table; }  "
                + "body { display: table-cell; vertical-align: middle; text-align: center; -webkit-text-size-adjust: none; }  "
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
        StringBuilder dspImpTrackings = new StringBuilder();
        for(String impTracking : resolveDspImpTrackingsFromMonitor(content)) {
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
                switch (monitor.getEventType()) {
                    case "click":
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
                switch (monitor.getEventType()) {
                    case "imp":
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
        for(int i =  0; i < eles.size(); i++) {
            output.append("\"" + eles.get(i) + "\"");
            if (i < eles.size()-1) {
                output.append(",");
            }
        }
        return output.toString();
    }

    private static Bid resolveNativeBid(Integer adType, HuaweiContent content, HuaweiMetadata metadata, BidType bidType, Imp imp, String newBidIdFromImp, List<HuaweiMonitor> monitorList, BigDecimal bidPrice, String bidCrid, JacksonMapper mapper) {
        if(adType != NATIVE_CODE) {
            throw new PreBidException("resolveNativeBid: response is not a native ad");
        }
        Native xnative = imp.getXNative();
        if(xnative == null) {
            throw new PreBidException("resolveNativeBid: imp.Native is null");
        }
        String request = xnative.getRequest();
        if(StringUtils.isBlank(request)) {
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
                .assets(resolveAssets(nativePayload, metadata, adType, content, bidType, imp, newBidIdFromImp, monitorList, bidPrice, bidCrid))
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
                .filter(image -> image.getH() != 0 && image.getW() !=0)
                .findFirst()
                .orElse(null);

        if(xnativeImage != null) {
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
                .filter(image -> image.getH() != 0 && image.getW() !=0)
                .findFirst()
                .orElse(null);

        if(xnativeImage != null) {
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
        String versionDefault = "1.1";
        String versionFromPayload = nativePayload.getVer();
        if (!StringUtils.isBlank(versionFromPayload)) {
            return versionFromPayload;
        }
        return versionDefault;
    }

    private static List<XnativeAsset> resolveAssets(Request nativePayload, HuaweiMetadata metadata, Integer adType, HuaweiContent content,
                                                    BidType bidType, Imp imp, String newBidIdFromImp, List<HuaweiMonitor> monitorList, BigDecimal bidPrice, String bidCrid) {
        /*
        List<XnativeAsset> nativeResultAssets = new ArrayList<>();
        final Iterator<HuaweiIcon> iconIterator = metadata.getIcon().iterator();
        final Iterator<HuaweiImageInfo> imageInfoIterator = metadata.getImageInfo().iterator();

        for(Asset asset : nativePayload.getAssets()) {
            AssetImageResult assetImageResult = resolveAssetImage(asset, iconIterator, imageInfoIterator);
            nativeResultAssets.add(XnativeAsset.builder()
                    .title(resolveAssetTitle(metadata, asset))
                    .video(resolveAssetVideo(asset, adType, content, metadata, bidType, imp, newBidIdFromImp, monitorList, bidPrice, bidCrid))
                    .image(assetImageResult.getXnativeImage())
                    .data(resolveAssetData(asset, metadata))
                    .id(asset.getId()).build());
            imgIndex = assetImageResult.getImgIndex();
            iconIndex = assetImageResult.getIconIndex();
        }
        return nativeResultAssets;*/
        return null;
    }

    private static XnativeData resolveAssetData(Asset asset, HuaweiMetadata metadata) {
        DataObject dataFromAsset = asset.getData();
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

    private static XnativeImage resolveAssetImage(Asset asset, Iterator<HuaweiIcon> iconIterator, Iterator<HuaweiImageInfo> imageInfoIterator) {
        //TODO check npe on metadata icon
        /*final ImageObject assetImg = asset.getImg();
        if (assetImg != null) {
            final Integer imageType = assetImg.getType();

            final XnativeImage xnativeImage = new XnativeImage();
            xnativeImage.setUrl("");
            xnativeImage.setImageAssetType(imageType);
            if (imageType.equals(ImageAssetType.imageAssetTypeIcon) && iconIterator.hasNext()) {
                    final HuaweiIcon icon = iconIterator.next();
                    return XnativeImage.of(imageType, icon.getUrl(), icon.getWidth(), icon.getHeight());
            } else if (imageInfoIterator.hasNext()) {
                    HuaweiImageInfo imageInfo = imageInfoIterator.next();
                    xnativeImage.setUrl(imageInfo.getUrl());
                    xnativeImage.setW(imageInfo.getWidth());
                    xnativeImage.setH(imageInfo.getHeight());
            }
            return xnativeImage;
        }*/
        return null;
    }

    private static XnativeVideo resolveAssetVideo(Asset asset, Integer adType, HuaweiContent content, HuaweiMetadata metadata, BidType bidType, Imp imp,
                                                  String newBidIdFromImp, List<HuaweiMonitor> monitorList, BigDecimal bidPrice, String bidCrid) {
        if (asset.getVideo() != null) {
            Bid bid = resolveVideoBid(adType, content, metadata, bidType, imp, newBidIdFromImp, monitorList, bidPrice, bidCrid);
            return XnativeVideo.builder().VASTTag(bid.getAdm()).build();
        }
        return null; //not sure about this ...
    }

    private static XnativeTitle resolveAssetTitle(HuaweiMetadata metadata, Asset asset) {
        String newTitleText = getDecodedValue(metadata.getTitle());
        if(asset.getTitle() != null) {
            return XnativeTitle.builder()
                    .text(newTitleText)
                    .len(newTitleText.length())
                    .build();
        }
        return null; //not sure about this ...
    }

    private static String resolveXnativeLinkUrl(HuaweiMetadata metadata) {
        String linkObjectUrl = "";
        String clickUrlFromContent = metadata.getClickUrl();
        String clickUrlFromContentIntent = metadata.getIntent();
        if (!StringUtils.isBlank(clickUrlFromContent)) {
            linkObjectUrl = clickUrlFromContent;
        } else if (!StringUtils.isBlank(clickUrlFromContentIntent)) {
            linkObjectUrl = clickUrlFromContentIntent;
        }
        return linkObjectUrl;
    }

    private static List<String> resolveClickTrackers(HuaweiContent content) {
        List<String> clickTrackers = new ArrayList<>();
        List<HuaweiMonitor> monitors = content.getMonitor();
        if(monitors != null) {
            for(HuaweiMonitor monitor : monitors) {
                List<String> url = monitor.getUrl();
                if(url == null) {
                    continue;
                }
                if(url.isEmpty()) {
                    continue;
                }
                String eventType = monitor.getEventType();
                if(eventType != null && eventType.equals("click")) {
                    clickTrackers.addAll(monitor.getUrl());
                }

            }
        }
        return clickTrackers;
    }

    private static List<String> resolveImpTrackers(HuaweiContent content) {
        List<HuaweiMonitor> monitors = content.getMonitor();
        List<String> impTrackers = new ArrayList<>();
        if(monitors != null) {
            for(HuaweiMonitor monitor : monitors) {
                List<String> url = monitor.getUrl();
                if(url == null) {
                    continue;
                }
                if(url.isEmpty()) {
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

    private static Bid resolveVideoBid(Integer adType, HuaweiContent content, HuaweiMetadata metadata, BidType bidType, Imp imp, String newBidIdFromImp, List<HuaweiMonitor> monitorList, BigDecimal bidPrice, String bidCrid) {
        HuaweiMediaFile mediaFile = metadata.getMediaFile();
        if(mediaFile == null) {
            throw new PreBidException("resolveVideoBid: Content.MetaData.MediaFile is null");
        }
        HuaweiVideoInfo videoInfo= metadata.getVideoInfo();
        if(videoInfo == null) {
            throw new PreBidException("resolveVideoBid: Content.MetaData.VideoInfo is null");
        }
        boolean adTypeIsRollCode = adType == ROLL_CODE;

        int width = resolveVideoWidth(adTypeIsRollCode, mediaFile, videoInfo, bidType, imp);
        int height = resolveVideoHeight(adTypeIsRollCode, mediaFile, videoInfo, bidType, imp);
        String adm = resolveVideoAdm(adTypeIsRollCode, content, metadata, mediaFile, videoInfo, monitorList, width, height);

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
        for(HuaweiMonitor monitor : monitorList) {
            List<String> urls = monitor.getUrl();
            if(monitor.getEventType().equals("win") && !urls.isEmpty()) {
                return urls.get(0);
            }
        }
        return "";
    }

    private static String resolveVideoAdm(boolean adTypeIsRollCode, HuaweiContent content, HuaweiMetadata metadata, HuaweiMediaFile mediaFile,
                                          HuaweiVideoInfo videoInfo, List<HuaweiMonitor> monitorList, int width, int height) {
        final String contentId = content.getContentid();
        if(StringUtils.isBlank(contentId)) {
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
        final StringBuilder errorTrackingToStr = new StringBuilder();;

        for(HuaweiMonitor monitor : monitorList) {
            List<String> urls = monitor.getUrl();
            if(urls == null) {
                throw new PreBidException("resolveVideoAdm: Content.Monitor is null");
            }
            if (urls.size() == 0) {
                continue;
            }
            String eventType = monitor.getEventType();
            if(eventType == null) {
                throw new PreBidException("resolveVideoAdm: Content.Monitor.EventType is null");
            }
            resolveTracking(dspImpTrackingToStr, dspClickTrackingToStr, errorTrackingToStr, urls, eventType);
            resolveTrackingEvents(trackingEvents, eventType, urls);
        }

        return  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
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

    private static void resolveTracking(StringBuilder dspImpTracking2Str, StringBuilder dspClickTracking2Str, StringBuilder errorTracking2Str, List<String> urlsFromMonitor, String eventType) {
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
        }
    }

    private static String getVastImpClickErrorTrackingUrls(List<String> urls, String eventType) {
        StringBuilder trackingUrls = new StringBuilder();
        for(String url : urls) {
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

    private static void resolveTrackingEvents(StringBuilder trackingEvents, String eventType, List<String> urlsFromMonitor) {
        String event = resolveEvent(eventType);
        if(!StringUtils.isBlank(event)) {
            if (!event.equals("skip&closeLinear")) {
                trackingEvents.append(getVastEventTrackingUrls(urlsFromMonitor, event));
            } else {
                trackingEvents.append(getVastEventTrackingUrls(urlsFromMonitor, "skip&closeLinear"));
            }
        }
    }

    private static String getVastEventTrackingUrls(List<String> urls, String eventType) {
        StringBuilder trackingUrls = new StringBuilder();
        for(String eventUrl : urls) {
            if (eventType.equals("skip&closeLinear")) {
                trackingUrls.append("<Tracking event=\"skip\"><![CDATA[");
                trackingUrls.append(eventUrl);
                trackingUrls.append ("]]></Tracking><Tracking event=\"closeLinear\"><![CDATA[");
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
        }
        return event;
    }

    private static int resolveVideoHeight(boolean adTypeIsRollCode, HuaweiMediaFile mediaFile, HuaweiVideoInfo videoInfo, BidType bidType, Imp imp) {
        if(adTypeIsRollCode) {
            return mediaFile.getHeight();
        }
        else {
            int heightFromVideoInfo = videoInfo.getHeight();
            if(heightFromVideoInfo != 0) {
                return heightFromVideoInfo;
            } else if (bidType == BidType.video) {
                Video videoFromImp = imp.getVideo();
                if(videoFromImp != null) {
                    Integer heightFromVideo = videoFromImp.getH();
                    if(heightFromVideo != null && heightFromVideo != 0) {
                         return heightFromVideo;
                    }
                }
            }
        }
        throw new PreBidException("resolveVideoHeight: cannot get height");
    }

    private static int resolveVideoWidth(boolean adTypeIsRollCode, HuaweiMediaFile mediaFile, HuaweiVideoInfo videoInfo, BidType bidType, Imp imp) {
        if(adTypeIsRollCode) {
            return mediaFile.getWidth();
        }
        else {
            int widthFromVideoInfo = videoInfo.getWidth();
            if(widthFromVideoInfo != 0) {
                 return widthFromVideoInfo;
            } else if (bidType == BidType.video) {
                Video videoFromImp = imp.getVideo();
                if(videoFromImp != null ) {
                    Integer widthFromVideo = videoFromImp.getW();
                    if(widthFromVideo != null && widthFromVideo != 0) {
                        return widthFromVideo;
                    }
                }
            }
        }
        throw new PreBidException("resolveVideoWidth: cannot get width");
    }

    private static String resolveDuration(boolean adTypeIsRollCode, HuaweiMetadata metadata, HuaweiVideoInfo videoInfo) {
        String duration;
        if(adTypeIsRollCode) {
            duration = getDuration(metadata.getDuration());
        }
        else {
            duration = getDuration(videoInfo.getVideoDuration());
        }
        return duration;
    }

    private static String resolveResourceUrl(boolean adTypeIsRollCode, HuaweiVideoInfo videoInfo, HuaweiMediaFile mediaFile) {
        String resourceUrl;
        if(adTypeIsRollCode) {
            String urlFromMediaFile = mediaFile.getUrl();
            if (!StringUtils.isBlank(urlFromMediaFile)) {
                resourceUrl = urlFromMediaFile;
            }
            else {
                throw new PreBidException("resolveResourceUrl: Content.MetaData.MediaFile.Url is empty");
            }
        }
        else {
            String videoDownloadUrlFromVideoInfo = videoInfo.getVideoDownloadUrl();
            if (!StringUtils.isBlank(videoDownloadUrlFromVideoInfo)) {
                resourceUrl = videoDownloadUrlFromVideoInfo;
            } else {
                throw new PreBidException("resolveResourceUrl: content.MetaData.VideoInfo.VideoDownloadUrl is empty");
            }
        }
        return resourceUrl;
    }

    private static String resolveMime(boolean adTypeIsRollCode, HuaweiContent content, HuaweiMetadata metadata, HuaweiMediaFile mediaFile) {
        String mime = "video/mp4";
        if(adTypeIsRollCode) {
            String mimeFromMediaFile = mediaFile.getMime();
            if (!StringUtils.isBlank(mimeFromMediaFile)) {
                mime = mimeFromMediaFile;
            }
        }
        return mime;
    }

    private static String resolveClickUrl(HuaweiMetadata metadata) {
        String clickUrl = "";
        String clickUrlFromContent = metadata.getClickUrl();
        String clickUrlFromContentIntent = metadata.getIntent();
        if (!StringUtils.isBlank(clickUrlFromContent)) {
            clickUrl = clickUrlFromContent;
        } else if (!StringUtils.isBlank(clickUrlFromContentIntent)) {
            clickUrl = getDecodedValue(clickUrlFromContentIntent);
        }
        return clickUrl;
    }

    private static String getDuration(int duration) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("15:04:05.000"); // is it ok ?
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(duration);
        return simpleDateFormat.format(calendar.getTime());
    }

    private static String getDecodedValue(String encodedString) {
        return encodedString != null ? URLDecoder.decode(encodedString) : "";
    }

    @Value
    @AllArgsConstructor(staticName = "of")
    static class AssetImageResult {
        private int iconIndex;
        private int imgIndex;
        private XnativeImage xnativeImage;
    }
}

