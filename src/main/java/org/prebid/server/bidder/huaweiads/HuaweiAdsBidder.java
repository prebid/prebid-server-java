package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.*;

import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.huaweiads.model.*;
import org.prebid.server.bidder.huaweiads.model.HuaweiAdsApp;
import org.prebid.server.bidder.huaweiads.model.Content;
import org.prebid.server.bidder.huaweiads.model.Format;
import org.prebid.server.bidder.huaweiads.model.xnative.*;
import org.prebid.server.bidder.model.*;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuaweiAds;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class HuaweiAdsBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpHuaweiAds>> HUAWEIADS_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpHuaweiAds>>() {
            };

    private static final TypeReference<ExtPrebid<?, ExtImp>> IMP_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImp>>() {
            };

    private static final String HUAWEI_ADX_API_VERSION = "3.4";
    private static final String DEFAULT_COUNTRY_NAME = "ZA";
    private static final String DEFAULT_MODEL_NAME = "HUAWEI";
    private static final int DEFAULT_UNKNOWN_NETWORK_TYPE = 0;
    private static final String DEFAULT_TIME_ZONE = "+0200";

    // table of const codes for creative types

    private static final int text = 1;
    private static final int bigPicture = 2;
    private static final int bigPicture2 = 3;
    private static final int gif = 4;
    private static final int videoText = 6;
    private static final int smallPicture = 7;
    private static final int threeSmallPicturesText = 8;
    private static final int video = 9;
    private static final int iconText = 10;
    private static final int videoWithPicturesText = 11;

    //table of const codes for bidTypes
    private static final int BANNER_CODE = 8;
    private static final int NATIVE_CODE = 3;
    private static final int ROLL_CODE = 60;
    private static final int REWARDED_CODE = 7;
    private static final int SPLASH_CODE = 1;
    private static final int INTERSTITIAL_CODE = 12;

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public HuaweiAdsBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Adslot> multislot = new ArrayList<>();
        HuaweiAdsRequest huaweiAdsRequest = new HuaweiAdsRequest();
        ExtImpHuaweiAds extImpHuaweiAds = null;
        for (Imp imp : request.getImp()) {
            try {
                extImpHuaweiAds = parseImpExt(imp);
                multislot.add(getHuaweiAdsReqAdslot(extImpHuaweiAds, request, imp));
                huaweiAdsRequest.setMultislot(multislot);
                getHuaweiAdsReqJson(huaweiAdsRequest, request, extImpHuaweiAds);
            } catch (PreBidException e) {
                return Result.withErrors(Collections.singletonList(BidderError.badInput(e.getMessage())));
            }
        }

        String reqJson;
        try {
            reqJson = mapper.mapper().writeValueAsString(huaweiAdsRequest);
        } catch (JsonProcessingException e) {
            return Result.withErrors(Collections.singletonList(BidderError.badInput(e.getMessage())));
        }
        boolean isTestAuthorization = false;
        if (extImpHuaweiAds != null && extImpHuaweiAds.getIsTestAuthorization().equals("true")) {
            isTestAuthorization = true;
        }
        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(resolveHeaders(extImpHuaweiAds, request, isTestAuthorization))
                .body(reqJson)
                .build());
    }

    private MultiMap resolveHeaders(ExtImpHuaweiAds extImpHuaweiAds, BidRequest request, boolean isTestAuthorization) {
        MultiMap headers = HttpUtil.headers();
        if(extImpHuaweiAds == null) {
            return headers;
        }
        headers.add("Authorization", getDigestAuthorization(extImpHuaweiAds, isTestAuthorization));
        Device device = request.getDevice();
        if(device != null && device.getUa().length() > 0) {
            headers.add("User-Agent", device.getUa());
        }
        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        HuaweiAdsResponse huaweiAdsResponse;
        List<BidderBid> bidderResponse;
        try {
            huaweiAdsResponse = mapper.decodeValue(httpCall.getResponse().getBody(), HuaweiAdsResponse.class);
            checkHuaweiAdsResponseRetcode(huaweiAdsResponse);
            bidderResponse = convertHuaweiAdsResp2BidderResp(huaweiAdsResponse, httpCall.getRequest().getPayload()).getSeatBid().getBids();
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
        return Result.of(bidderResponse, Collections.emptyList());
    }

    private ExtImpHuaweiAds parseImpExt(Imp imp) {
        ExtImpHuaweiAds extImpHuaweiAds;
        try {
            ExtImp extImpDefault = mapper.mapper().convertValue(imp.getExt(), IMP_EXT_TYPE_REFERENCE).getBidder();
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

    private Adslot getHuaweiAdsReqAdslot(ExtImpHuaweiAds extImpHuaweiAds, BidRequest request, Imp imp) {
        String lowerAdType = StringUtils.lowerCase(extImpHuaweiAds.getAdtype());

        Adslot adslot = Adslot.builder()
                .slotId(extImpHuaweiAds.getSlotId())
                .adType(convertAdtypeString2Integer(lowerAdType))
                .test(request.getTest())
                .build();
        Banner banner = imp.getBanner();
        if (banner != null) {
            if (banner.getW() != null && banner.getH() != null) {
                adslot.setW(banner.getW());
                adslot.setH(banner.getH());
            }
            if (banner.getFormat().size() != 0) {
                List<Format> newFormats = banner.getFormat().stream()
                        .filter(oldFormat -> oldFormat.getH() != 0 && oldFormat.getW() != 0)
                        .map(oldFormat -> Format.of(oldFormat.getW(), oldFormat.getH()))
                        .collect(Collectors.toList());
                adslot.setFormat(newFormats);
            }
        } else if (imp.getXNative() != null) {
            getNativeFormat(adslot, imp);
            return adslot;
        }
        if (lowerAdType.equals("roll")) {
            Video video = imp.getVideo();
            if (video != null && video.getMaxduration()!= null && video.getMaxduration() >= 0) {
                adslot.setTotalDuration(video.getMaxduration());
            } else {
                throw new PreBidException("GetHuaweiAdsReqAdslot: Video maxDuration is empty when adtype is roll");
            }
        }
        return adslot;
    }

    private void getNativeFormat(Adslot adslot, Imp imp) {
        String request = imp.getXNative().getRequest();
        if (StringUtils.isBlank(request)) {
            throw new PreBidException("getNativeFormat: imp.xNative.request is empty");
        }
        NativeRequest nativePayload = null;
        try {
            nativePayload = mapper.mapper().readValue(request, NativeRequest.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }
        int numImage = 0;
        int numVideo = 0;
        int width = 0;
        int height = 0;

        for (Asset asset : nativePayload.getAssets()) {
            if (asset.getVideo() != null) {
                numVideo++;
            }
            ImageObject image = asset.getImg();
            if (image != null) {
                numImage++;
                //can validate
                int h = image.getH() != null ? image.getH() : 0;
                int w = image.getW() != null ? image.getW() : 0;
                int wMin = image.getWmin() != null ? image.getWmin() : 0;
                int hMin = image.getHmin() != null ? image.getHmin() : 0;

                if (h != 0 && w != 0) {
                    width = w;
                    height = h;
                } else if (wMin != 0 && hMin != 0) {
                    width = wMin;
                    height = hMin;
                }
            }
        }

        adslot.setH(height);
        adslot.setW(width);

        ArrayList<String> detailedCreativeTypeList = new ArrayList<>();

        if (numVideo > 1) {
            detailedCreativeTypeList.add("903");
        } else if (numImage > 1) {
            detailedCreativeTypeList.add("904");
        } else if (numImage == 1) {
            detailedCreativeTypeList.add("909");
        } else {
            detailedCreativeTypeList.add("913");
            detailedCreativeTypeList.add("914");
        }
        adslot.setDetailedCreativeTypeList(detailedCreativeTypeList);
    }

    private int convertAdtypeString2Integer(String adType) {
        switch (adType) {
            case "banner":
                return BANNER_CODE;
            case "native":
                return NATIVE_CODE;
            case "rewarded":
                return REWARDED_CODE;
            case "splash":
                return SPLASH_CODE;
            case "interstitial":
                return INTERSTITIAL_CODE;
            case "roll":
                return ROLL_CODE;
            default:
                return BANNER_CODE;
        }
    }

    private void getHuaweiAdsReqJson(HuaweiAdsRequest huaweiAdsRequest, BidRequest bidRequest, ExtImpHuaweiAds extImpHuaweiAds) {
        huaweiAdsRequest.setVersion(HUAWEI_ADX_API_VERSION);
        getHuaweiAdsReqAppInfo(huaweiAdsRequest, bidRequest);
        getHuaweiAdsReqDeviceInfo(huaweiAdsRequest, bidRequest);
        resolveHuaweiAdsReqGeoInfo(huaweiAdsRequest, bidRequest);
        resolveHuaweiAdsReqNetWorkInfo(huaweiAdsRequest, bidRequest);
        resolveHuaweiAdsReqRegsInfo(huaweiAdsRequest, bidRequest);
    }

    private void getHuaweiAdsReqAppInfo(HuaweiAdsRequest huaweiAdsRequest, BidRequest bidRequest) {
        HuaweiAdsApp huaweiAdsApp;
        com.iab.openrtb.request.App appFromBidReq = bidRequest.getApp();

        if (appFromBidReq != null) {
            huaweiAdsApp = HuaweiAdsApp.builder()
                    .version(StringUtils.isBlank(appFromBidReq.getVer()) ? null : appFromBidReq.getVer())
                    .name(StringUtils.isBlank(appFromBidReq.getName()) ? null : appFromBidReq.getName())
                    .pkgname(resolvePkgName(appFromBidReq))
                    .lang(resolveLang(appFromBidReq))
                    .country(resolveCountry(bidRequest))
                    .build();
        }
    }

    private void getHuaweiAdsReqDeviceInfo(HuaweiAdsRequest huaweiAdsRequest, BidRequest bidRequest) {
        HuaweiAdsDevice huaweiAdsDevice;
        Device bidRequestDevice = bidRequest.getDevice();
        String country = resolveCountry(bidRequest);

        if (bidRequestDevice != null) {
            huaweiAdsDevice = HuaweiAdsDevice.builder()
                    .type(bidRequestDevice.getDevicetype())
                    .useragent(bidRequestDevice.getUa())
                    .os(bidRequestDevice.getOs())
                    .version(bidRequestDevice.getOsv())
                    .maker(bidRequestDevice.getMake())
                    .model(StringUtils.isBlank(bidRequestDevice.getModel()) ? DEFAULT_MODEL_NAME : bidRequestDevice.getModel())
                    .height(bidRequestDevice.getH())
                    .width(bidRequestDevice.getW())
                    .language(bidRequestDevice.getLanguage())
                    .pxratio(bidRequestDevice.getPxratio())
                    .belongCountry(country)
                    .localeCountry(country)
                    .ip(bidRequestDevice.getIp())
                    .build();
        } else {
            huaweiAdsDevice = new HuaweiAdsDevice();
        }

        getDeviceId(huaweiAdsDevice, bidRequest);
        huaweiAdsRequest.setDevice(huaweiAdsDevice);
    }

    private void getDeviceId(HuaweiAdsDevice huaweiAdsDevice, BidRequest request) {
        User user = request.getUser();
        ExtUserDataHuaweiAds extUserDataHuaweiAds;
        if(user == null) {
            throw new PreBidException("getDeviceID: BidRequest.user is null");
        }
        if(user.getExt() == null) {
            throw new PreBidException("getDeviceID: BidRequest.user.ext is null");
        }
        try {
             extUserDataHuaweiAds = mapper.mapper().readValue(request.getUser().getExt().getData().toString(), ExtUserDataHuaweiAds.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Unmarshal: BidRequest.user.ext -> extUserDataHuaweiAds failed");
        }
        if (extUserDataHuaweiAds.getData() == null) {
            throw new PreBidException("Unmarshal: BidRequest.user.ext -> extUserDataHuaweiAds failed");
        } // wtf it converts ????
        ExtUserDataDeviceIdHuaweiAds deviceId = extUserDataHuaweiAds.getData();
        String[] imei = deviceId.getImei(), oaid = deviceId.getOaid(), gaid = deviceId.getGaid(), clientTime = deviceId.getClientTime();

        if (imei.length == 0 && gaid.length == 0 && oaid.length == 0) {
            throw new PreBidException("getDeviceID: Imei ,Oaid, Gaid are all empty");
        }

        if(oaid.length > 0) {
            huaweiAdsDevice.setOaid(oaid[0]);
        }
        if(imei.length > 0) {
            huaweiAdsDevice.setImei(imei[0]);
        }
        if(gaid.length > 0) {
            huaweiAdsDevice.setGaid(gaid[0]);
        }
        if(clientTime.length > 0) {
            huaweiAdsDevice.setClientTime(resolveClientTime(clientTime[0]));
        }

        Device device = request.getDevice();
        if (device != null && device.getDnt() != null) {
            if(!StringUtils.isBlank(huaweiAdsDevice.getOaid())) {
                huaweiAdsDevice.setIsTrackingEnabled(String.valueOf(1 - device.getDnt())); //?
            }
            if(!StringUtils.isBlank(huaweiAdsDevice.getGaid())) {
                huaweiAdsDevice.setGaidTrackingEnabled(String.valueOf(1 - device.getDnt())); //?
            }
        }
    }

    private String resolveClientTime(String clientTime) {
        return clientTime;
        /*
    String zone = DEFAULT_TIME_ZONE;
    String format = "yyyy-MM-dd'T'HH:mm:ssXXX";
    String t  = LocalDateTime.now().format(DateTimeFormatter.ofPattern(format));
    int index = t.contains("+") ? t.indexOf("+") : t.indexOf("-");
    if(index > 0 && t.length()-index == 5) {
        zone = t.substring(index);
    }
    if (StringUtils.isBlank(clientTime)) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format))+zone; //??
    }
    if (clientTime.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]{1}\\d{4}$")) {
        return clientTime;
    }
    if (clientTime.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}$")) {
        return clientTime + zone;
    }
    return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format))+zone; //??
    */
    }

    private String resolveCountry(BidRequest bidRequest) {
        Geo geo;
        String country;

        Device device = bidRequest.getDevice();
        geo = device != null ? device.getGeo() : null;
        country = geo != null ? geo.getCountry() : null;
        if( !StringUtils.isBlank(country)) {
            return convertCountryCode(country);
        }

        User user = bidRequest.getUser();
        geo = user != null ? user.getGeo() : null;
        country = geo != null ? geo.getCountry() : null;
        if( !StringUtils.isBlank(country)) {
            return convertCountryCode(country);
        }

        return DEFAULT_COUNTRY_NAME;
    }

    // convertCountryCode: ISO 3166-1 Alpha3 -> Alpha2, Some countries may use
    private String convertCountryCode(String country) {
        Map<String, String> mapCountryCodeAlpha3ToAlpha2 = Map.of("CHL",  "CL", "CHN", "CN", "ARE", "AE");
        String countryPresentInMap = mapCountryCodeAlpha3ToAlpha2.keySet().stream().filter(element -> element.equals(country)).findFirst().orElse(null);
        if(countryPresentInMap != null) {
            return mapCountryCodeAlpha3ToAlpha2.get(countryPresentInMap);
        }
        if(country.length() > 2) {
            return country.substring(0, 2);
        }
        return DEFAULT_COUNTRY_NAME;
    }

    private String resolvePkgName(App appFromBidReq) {
        String bundle = appFromBidReq.getBundle();
        if (StringUtils.isBlank(bundle)) {
            throw new PreBidException("resolvePkgName: app.bundle is empty");
        }
        return bundle;
    }

    private String resolveLang(App appFromBidReq) {
        String lang = appFromBidReq.getContent() != null ? appFromBidReq.getContent().getLanguage() : null;
        if (StringUtils.isBlank(lang)) {
            return "en";
        }
        return lang;
    }

    private void resolveHuaweiAdsReqNetWorkInfo (HuaweiAdsRequest huaweiAdsRequest, BidRequest bidRequest) {
        Device device = bidRequest.getDevice();
        if (device != null ) {
            Network network = new Network();
            network.setType(device.getConnectiontype() != null
                    ? device.getConnectiontype()
                    : DEFAULT_UNKNOWN_NETWORK_TYPE);
            network.setCarrier(0);

            String[] arrMccmnc = device.getMccmnc().split("-");
            List<CellInfo> cellInfos = new ArrayList<>();

            if(arrMccmnc.length > 2) {
                cellInfos.add(CellInfo.of(arrMccmnc[0], arrMccmnc[1]));
            }
            String str = arrMccmnc[0] + arrMccmnc[1];
            if (str.equals("46000") || str.equals("46002") || str.equals("46007")){
                network.setCarrier(2);
            } else if (str.equals("46001") || str.equals("46006")) {
                network.setCarrier(1);
            } else if (str.equals("46003") || str.equals("46005") || str.equals("46011")) {
                network.setCarrier(3);
            } else {
                network.setCarrier(99);
            }
            network.setCellInfoList(cellInfos);
            huaweiAdsRequest.setNetwork(network);
        }
    }

    private void resolveHuaweiAdsReqRegsInfo(HuaweiAdsRequest huaweiAdsRequest, BidRequest bidRequest) {
        Regs regs = bidRequest.getRegs();
        if(regs != null && regs.getCoppa() >= 0) {
            huaweiAdsRequest.setRegs(Regs.of(regs.getCoppa(), null));
        }
    }

    private void resolveHuaweiAdsReqGeoInfo(HuaweiAdsRequest huaweiAdsRequest, BidRequest bidRequest) {
        Device device = bidRequest.getDevice();
        Geo geo = device != null ? device.getGeo() : null;
        if(geo != null) {
            huaweiAdsRequest.setGeo(Geo.builder()
                    .lon(geo.getLon())
                    .lat(geo.getLat())
                    .accuracy(geo.getAccuracy())
                    .build());
        }
    }

    private void checkHuaweiAdsResponseRetcode(HuaweiAdsResponse response)  {
        Integer retcode = response.getRetcode();
        if ((retcode < 600 && retcode >= 400) || (retcode < 300 && retcode > 200)) {
            throw new PreBidException("HuaweiAdsResponse retcode: " + retcode);
            }
    }

    private BidderResponse convertHuaweiAdsResp2BidderResp(HuaweiAdsResponse huaweiAdsResponse, BidRequest bidRequest) {
        int multiAdLength = huaweiAdsResponse.getMultiad().size();
        List<BidderBid> bids = new ArrayList<>();
        if(multiAdLength == 0) {
            throw new PreBidException("convertHuaweiAdsResp2BidderResp: multiad length is 0, get no ads from huawei side");
        }
        List<Imp> imps = bidRequest.getImp();
        if (imps == null) {
            throw new PreBidException("convertHuaweiAdsResp2BidderResp: BidRequest.imp is null");
        }
        HashMap<String, Imp> mapSlotid2Imp = new HashMap<String, Imp> ();
        HashMap<String, BidType> mapSlotid2MediaType = new HashMap<String, BidType> ();

        for(Imp imp : imps) {
            ExtImpHuaweiAds extImpHuaweiAds = parseImpExt(imp);
            mapSlotid2Imp.put(extImpHuaweiAds.getSlotId(), imp);
            BidType mediaType = BidType.banner;

            if (imp.getVideo() != null) {
                mediaType = BidType.video;
            } else if (imp.getXNative() != null) {
                mediaType = BidType.xNative;
            } else if (imp.getAudio() != null) {
                mediaType = BidType.audio;
            }
            mapSlotid2MediaType.put(extImpHuaweiAds.getSlotId(), mediaType);
        }

        if (mapSlotid2Imp.size() < 1) {
            throw new PreBidException("convertHuaweiAdsResp2BidderResp: BidRequest.imp is null");
        } //? do i have to use it

        for(Ad ad : huaweiAdsResponse.getMultiad()) {
           if( StringUtils.isBlank( mapSlotid2Imp.get( ad.getSlotId() ).getId() ) ) {
               continue;
           }
           if (ad.getRetcode() != 200) {
               continue;
           }

           for(Content content : ad.getContent()) {
               StringBuffer adm = new StringBuffer();
               Integer width = 0, height = 0;
               String slotId = ad.getSlotId();
               handleHuaweiAdsContent(ad.getAdType(), content, mapSlotid2MediaType.get(slotId), mapSlotid2Imp.get(slotId),
                       adm, width, height);
               Bid bid = Bid.builder()
                       .id(mapSlotid2Imp.get(ad.getSlotId()).getId())
                       .impid(mapSlotid2Imp.get(ad.getSlotId()).getId())
                       .price(content.getPrice())
                       .crid(content.getContentid())
                       .adm(adm.toString())
                       .w(width)
                       .h(height)
                       .adomain(List.of("huaweiads"))
                       .nurl(getNurl(content))
                       .build();
               bids.add(BidderBid.of(bid, mapSlotid2MediaType.get(slotId), "CNY"));
           }
        }
        return BidderResponse.of("huaweiads", BidderSeatBid.of(bids, null, null), 5000); // ??????? not sure
    }

    private String getNurl(Content content) {
        List<Monitor> monitors = content.getMonitor();
        if (monitors.isEmpty()) {
            return "";
        }
        for(Monitor monitor : monitors) {
            List<String> urls = monitor.getUrl();
            if(monitor.getEventType().equals("win") && !urls.isEmpty()) {
                return urls.get(0);
            }
        }
        return "";
    }

    private void extractAdmBanner(Integer adType, Content content, BidType bidType, Imp imp, StringBuffer adm, Integer adWidth, Integer adHeight) {
        if (adType != BANNER_CODE) {
            throw new PreBidException("extractAdmBanner: huaweiads response is not a banner ad");
        }
        if (content == null) {
            throw new PreBidException("extractAdmPicture: content is empty");
        } // it must be higher in methods tree

        Integer creativeType = content.getCreativetype();
        if (creativeType > 100) {
            creativeType = creativeType - 100;
        }
        if (creativeType == text || creativeType == bigPicture || creativeType == bigPicture2 ||
                creativeType == smallPicture || creativeType == threeSmallPicturesText ||
                creativeType == iconText || creativeType == gif) {
                extractAdmPicture(content, adm, adWidth, adHeight);
        } else if (creativeType == videoText || creativeType == video || creativeType == videoWithPicturesText) {
                extractAdmVideo(adType, content, bidType, imp, adm , adWidth, adHeight);
        } else {
            throw new PreBidException("no banner support creativetype");
        }
    }

    private void handleHuaweiAdsContent(Integer adType, Content content, BidType bidType, Imp imp,
                                        StringBuffer adm,  Integer admWidth, Integer admHeight) {
        switch (bidType) {
            case banner:
               extractAdmBanner(adType, content, bidType, imp,  adm, admWidth, admHeight);
               break;
            case xNative:
                extractAdmNative(adType, content, bidType, imp, adm, admWidth, admHeight);
                break;
            case video:
                extractAdmVideo(adType, content, bidType, imp, adm, admWidth, admHeight);
                break;
            default:
                throw new PreBidException("no support bidtype: audio"); //code never reaches this statement
        }
    }

    private void extractAdmNative(Integer adType, Content content, BidType bidType, Imp imp,
                                  StringBuffer adm, Integer admWidth, Integer admHeight) {
        if(adType != NATIVE_CODE) {
            throw new PreBidException("extractAdmNative: response is not a native ad");
        }
        Native xnative = imp.getXNative();
        if(xnative == null) {
            throw new PreBidException("extractAdmNative: imp.Native is null"); //seems code cant reach this statement
        }
        String request = xnative.getRequest();
        if(StringUtils.isBlank(request)) {
            throw new PreBidException("extractAdmNative: imp.Native.Request is empty");
        }
        Request nativePayload;
        try {
            nativePayload = mapper.mapper().readValue(request, Request.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("extractAdmNative: cant convert request to object");
        }

        NativeResponse nativeResult = new NativeResponse();
        List<String> impTrackers = new ArrayList<>();
        nativeResult.setImpTrackers(impTrackers);
        Metadata metadata = content.getMetaData();
        String linkObjectUrl = "";
        String clickUrlFromContent = content.getMetaData().getClickUrl();
        String clickUrlFromContentIntent = content.getMetaData().getIntent();
        if (!StringUtils.isBlank(clickUrlFromContent)) {
            linkObjectUrl = clickUrlFromContent;
        } else if (!StringUtils.isBlank(clickUrlFromContentIntent)) {
            linkObjectUrl = clickUrlFromContentIntent;
        }
        XnativeLink xnativeLink = new XnativeLink();
        List<String> clickTrackers = new ArrayList<>();
        xnativeLink.setClickTrackers(clickTrackers);
        xnativeLink.setUrl(linkObjectUrl);
        nativeResult.setLink(xnativeLink);
        List<XnativeAsset> nativeResultAssets = new ArrayList<>();
        nativeResult.setAssets(nativeResultAssets);
        int imgIndex = 0;
        int iconIndex = 0;
        for(Asset asset : nativePayload.getAssets()) {
            XnativeAsset responseAsset = new XnativeAsset();
            XnativeTitle newTitle;
            TitleObject oldTitle = asset.getTitle();
            ImageObject imageFromAsset = asset.getImg();
            DataObject dataFromAsset = asset.getData();
            if(oldTitle != null) {
                String newTitleText = getDecodedValue(metadata.getTitle());
                newTitle = XnativeTitle.builder()
                        .text(newTitleText)
                        .len(newTitleText.length())
                        .build();
                responseAsset.setTitle(newTitle);
            } else if(asset.getVideo() != null) {
                StringBuffer videoObjectVASTTag = new StringBuffer();
                Integer adWidth = 0;
                Integer adHeight = 0;
                extractAdmVideo(adType, content, bidType, imp, videoObjectVASTTag, adWidth, adHeight);
                XnativeVideo xnativeVideo = XnativeVideo.builder().VASTTag(videoObjectVASTTag.toString()).build();
                responseAsset.setVideo(xnativeVideo);
            } else if (imageFromAsset != null) {
                XnativeImage xnativeImage = new XnativeImage();
                Integer imageType = imageFromAsset.getType();
                xnativeImage.setUrl("");
                xnativeImage.setImageAssetType(imageType);
                if (imageType.equals(ImageAssetType.imageAssetTypeIcon)) {
                    if (metadata.getIcon().size() > iconIndex) {
                        Icon icon = metadata.getIcon().get(iconIndex);
                        xnativeImage.setUrl(icon.getUrl());
                        xnativeImage.setW(icon.getWidth());
                        xnativeImage.setH(icon.getHeight());
                        iconIndex ++;
                    }
                } else {
                    if (metadata.getImageInfo().size() > imgIndex) {
                        ImageInfo imageInfo = metadata.getImageInfo().get(imgIndex);
                        xnativeImage.setUrl(imageInfo.getUrl());
                        xnativeImage.setW(imageInfo.getWidth());
                        xnativeImage.setH(imageInfo.getHeight());
                        imgIndex ++;
                    }
                }
                if(admHeight == 0 && admWidth == 0) {
                    admHeight = xnativeImage.getH();
                    admWidth = xnativeImage.getW();
                }
                responseAsset.setImage(xnativeImage);
            } else if (dataFromAsset != null) {
                XnativeData data = new XnativeData();
                data.setLabel("");
                data.setValue("");
                Integer dataType = dataFromAsset.getType();
                if (dataType.equals(DataAssetType.dataAssetTypeDesc) || dataType.equals(DataAssetType.dataAssetTypeDesc2)) {
                    data.setLabel("");
                    data.setValue(getDecodedValue(metadata.getDescription()));
                }
                responseAsset.setData(data);
            }
            responseAsset.setId(asset.getId());
            nativeResultAssets.add(responseAsset);
        }

        List<Monitor> monitors = content.getMonitor();
        if(monitors != null) {
            for(Monitor monitor : monitors) {
                List<String> url = monitor.getUrl();
                if(url.size() == 0) {
                    continue;
                }
                String eventType = monitor.getEventType();
                String intent = metadata.getIntent();
                if(eventType.equals("click")) {
                   clickTrackers.addAll(monitor.getUrl());
                }
                if(eventType.equals("imp")) {
                    impTrackers.addAll(monitor.getUrl());
                }
            }
        }
        nativeResult.setVer("1.1");
        String version = nativePayload.getVer();
        if (!StringUtils.isBlank(version)) {
            nativeResult.setVer(version);
        }
        adm.append(jsonEncode(nativeResult));
    }

    private String jsonEncode(NativeResponse response) {
        String json;
        try {
            json = mapper.mapper().writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new PreBidException("jsonEncode: cant convert NativeResponse to String");
        }
        return json;
    }

    private void extractAdmPicture(Content content, StringBuffer adm, Integer adWidth, Integer adHeight) {
        String clickUrl = "";
        String clickUrlFromContent = content.getMetaData().getClickUrl();
        String clickUrlFromContentIntent = content.getMetaData().getIntent();
        if (!StringUtils.isBlank(clickUrlFromContent)) {
            clickUrl = clickUrlFromContent;
        } else if (!StringUtils.isBlank(clickUrlFromContentIntent)) {
            clickUrl = getDecodedValue(clickUrlFromContentIntent);
        }

        String imageInfoUrl = "";
        List<ImageInfo> imageInfoList = content.getMetaData().getImageInfo();
        if (imageInfoList != null) {
            imageInfoUrl = imageInfoList.get(0).getUrl();
            adHeight += imageInfoList.get(0).getHeight();
            adWidth += imageInfoList.get(0).getWidth();
        }
        else {
            throw new PreBidException("content.MetaData.ImageInfo is null");
        }

        String imageTitle = getDecodedValue(content.getMetaData().getTitle());
        List<String> dspImpTrackings = new ArrayList<>();
        String dspClickTrackings = "";
        getDspImpClickTrackings(content, dspImpTrackings, dspClickTrackings);
        StringBuilder dspImpTrackings2StrImg = new StringBuilder();
        for(String impTracking : dspImpTrackings) {
            dspImpTrackings2StrImg.append("<img height=\"1\" width=\"1\" src='");
            dspImpTrackings2StrImg.append(impTracking);
            dspImpTrackings2StrImg.append("' >  ");
        }

         adm.append("<style> html, body  " +
                "{ margin: 0; padding: 0; width: 100%; height: 100%; vertical-align: middle; }  " +
                "html  " +
                "{ display: table; }  " +
                "body { display: table-cell; vertical-align: middle; text-align: center; -webkit-text-size-adjust: none; }  " +
                "</style> " +
		"<span class=\"title-link advertiser_label\">" + imageTitle + "</span> " +
                "<a href='" + clickUrl + "' style=\"text-decoration:none\" " +
        "onclick=sendGetReq()> " +
                "<img src='" + imageInfoUrl + "' width='" + adWidth + "' height='" + adHeight + "'/> " +
                "</a> " +
                dspImpTrackings2StrImg.toString() +
		"<script type=\"text/javascript\">" +
                "var dspClickTrackings = [" + dspClickTrackings + "];" +
                "function sendGetReq() {" +
                "sendSomeGetReq(dspClickTrackings)" +
                "}" +
                "function sendOneGetReq(url) {" +
                "var req = new XMLHttpRequest();" +
                "req.open('GET', url, true);" +
                "req.send(null);" +
                "}" +
                "function sendSomeGetReq(urls) {" +
                "for (var i = 0; i < urls.length; i++) {" +
                "sendOneGetReq(urls[i]);" +
                "}" +
                "}" +
                "</script>");
    }

    private void getDspImpClickTrackings(Content content, List<String> dspImpTrackings, String dspClickTrackings) {
        for (Monitor monitor : content.getMonitor()) {
            if (monitor.getUrl().size() != 0) {
                switch (monitor.getEventType()) {
                    case "imp":
                        dspImpTrackings = monitor.getUrl();
                    case "click":
                        dspClickTrackings = getStrings(monitor.getUrl());
               }
           }
       }
    }

    private String getStrings(List<String> eles) {
        if (eles.size() == 0) {
            return "";
        }
        StringBuffer output = new StringBuffer();
        for(int i =  0; i < eles.size(); i++) {
            output.append("\"" + eles.get(i) + "\"");
            if (i < eles.size()-1) {
                output.append(",");
            }
        }
        return output.toString();
    }

    // getDuration: millisecond -> format: 00:00:00.000
    private String getDuration(int duration) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("15:04:05.000");
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(duration);
        return simpleDateFormat.format(calendar.getTime());
    }

    private void extractAdmVideo(Integer adType, Content content, BidType bidType, Imp imp,
                                 StringBuffer adm, Integer adWidth, Integer adHeight) {
        if (content == null) {
           throw new PreBidException("extractAdmVideo: content is empty");
        }
        String clickUrl = "";
        String clickUrlFromContent = content.getMetaData().getClickUrl();
        String clickUrlFromContentIntent = content.getMetaData().getIntent();
        if (!StringUtils.isBlank(clickUrlFromContent)) {
            clickUrl = clickUrlFromContent;
        } else if (!StringUtils.isBlank(clickUrlFromContentIntent)) {
            clickUrl = getDecodedValue(clickUrlFromContentIntent);
        }

        String mime = "video/mp4";
        String resourceUrl = "";
        String duration = "";

        if(adType == ROLL_CODE) {
            MediaFile mediaFile = content.getMetaData().getMediaFile();
            String mimeFromMediaFile = mediaFile.getMime();
            if (!StringUtils.isBlank(mimeFromMediaFile)) {
                mime = mimeFromMediaFile;
            }
            adWidth = mediaFile.getWidth();
            adHeight = mediaFile.getHeight();

            String urlFromMediaFile = mediaFile.getUrl();
            if (!StringUtils.isBlank(urlFromMediaFile)) {
                resourceUrl = urlFromMediaFile;
            }
            else {
                throw new PreBidException("extractAdmVideo: Content.MetaData.MediaFile.Url is empty");
            }
            duration = getDuration(content.getMetaData().getDuration());
        }
        else {
            VideoInfo videoInfo = content.getMetaData().getVideoInfo();
            String videoDownloadUrlFromVideoInfo = videoInfo.getVideoDownloadUrl();
            if (!StringUtils.isBlank(videoDownloadUrlFromVideoInfo)) {
                resourceUrl = videoDownloadUrlFromVideoInfo;
            }
            else {
                throw new PreBidException("extractAdmVideo: content.MetaData.VideoInfo.VideoDownloadUrl is empty");
            }
            Integer widthFromVideoInfo = videoInfo.getWidth(); // do we need to check on null ?
            Integer heightFromVideoInfo = videoInfo.getHeight(); // do we need to check on null ?
            if(widthFromVideoInfo != 0 && heightFromVideoInfo != 0) {
                adWidth = widthFromVideoInfo;
                adHeight = heightFromVideoInfo;
            } else if (bidType == BidType.video) {
                Video videoFromImp = imp.getVideo();
                if(videoFromImp != null && videoFromImp.getW() != 0 && videoFromImp.getH() != 0) {
                    adWidth = videoFromImp.getW();
                    adHeight = videoFromImp.getH();
                }
            } else {
                throw new PreBidException("extractAdmVideo: cannot get width, height");
            }
            duration = getDuration(videoInfo.getVideoDuration());
        }

        String adTitle = getDecodedValue(content.getMetaData().getTitle());
        String adId = content.getContentid();
        String creativeId = content.getContentid();
        StringBuilder trackingEvents = new StringBuilder();
        String dspImpTracking2Str = "";
        String dspClickTracking2Str = "";
        String errorTracking2Str = "";

        for(Monitor monitor : content.getMonitor()) {
            if (monitor.getUrl().size() == 0) {
                continue;
            }
            String event = "";
            List<String> urlsFromMonitor = monitor.getUrl();
            switch (monitor.getEventType()) {
                case "vastError":
                    errorTracking2Str = getVastImpClickErrorTrackingUrls(urlsFromMonitor, "vastError");
                    break;
                case "imp":
                    dspImpTracking2Str = getVastImpClickErrorTrackingUrls(urlsFromMonitor, "imp");
                    break;
                case "click":
                    dspClickTracking2Str = getVastImpClickErrorTrackingUrls(urlsFromMonitor, "click");
                    break;
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
            if(!StringUtils.isBlank(event)) {
                if (!event.equals("skip&closeLinear")) {
                    trackingEvents.append(getVastEventTrackingUrls(urlsFromMonitor, event));
                } else {
                    trackingEvents.append(getVastEventTrackingUrls(urlsFromMonitor, "skip&closeLinear"));
                }
            }
        }

        adm.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<VAST version=\"3.0\">" +
		"<Ad id=\"" + adId + "\"><InLine>" +
                "<AdSystem>HuaweiAds</AdSystem>" +
                "<AdTitle>" + adTitle + "</AdTitle>" +
                errorTracking2Str + dspImpTracking2Str +
                "<Creatives>" +
		"<Creative adId=\"" + adId + "\" id=\"" + creativeId + "\">" +
                "<Linear>" +
                "<Duration>" + duration + "</Duration>" +
                "<TrackingEvents>" + trackingEvents.toString() + "</TrackingEvents>" +
                "<VideoClicks>" +
                "<ClickThrough><![CDATA[" + clickUrl + "]]></ClickThrough>" +
                dspClickTracking2Str +
                "</VideoClicks>" +
                "<MediaFiles>" +
		"<MediaFile delivery=\"progressive\" type=\"" + mime + "\" width=\"" + adWidth + "\" " +
		"height=\"" + adHeight + "\" scalable=\"true\" maintainAspectRatio=\"true\"> " +
                "<![CDATA[" + resourceUrl + "]]>" +
                "</MediaFile>" +
                "</MediaFiles>" +
                "</Linear>" +
                "</Creative>" +
                "</Creatives>" +
                "</InLine>" +
                "</Ad>" +
                "</VAST>");
    }

    private String getDecodedValue(String encodedString) {
        return encodedString != null ? URLDecoder.decode(encodedString) : "";
    }

    private String getVastImpClickErrorTrackingUrls(List<String> urls, String eventType) {
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

    private String getVastEventTrackingUrls(List<String> urls, String eventType) {
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

    private String getDigestAuthorization(ExtImpHuaweiAds extImpHuaweiAds, boolean isTestAuthorization) {
        String nonce = generateNonce();
        // this is for test case, time 2021/8/20 19:30
        if (isTestAuthorization) {
            nonce = "1629473330823";
        }
        String publisherId = extImpHuaweiAds.getPublisherId();
        var apiKey = publisherId + ":ppsadx/getResult:" + extImpHuaweiAds.getSignKey();
        return "Digest username=" + publisherId + "," +
                "realm=ppsadx/getResult," +
                "nonce=" + nonce + "," +
                "response=" + computeHmacSha256(nonce+":POST:/ppsadx/getResult", apiKey) + "," +
                "algorithm=HmacSHA256,usertype=1,keyid=" + extImpHuaweiAds.getKeyId();
    }

    private String computeHmacSha256(String data, String apiKey) {
        String output;
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(apiKey.getBytes("UTF-8"), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            output = Hex.encodeHexString(sha256_HMAC.doFinal(data.getBytes("UTF-8")));
        }
        catch (NoSuchAlgorithmException | UnsupportedEncodingException | InvalidKeyException e) {
            throw new PreBidException(e.getMessage());
        }
        return output;
    }

    private static String generateNonce() {
        String dateTimeString = Long.toString(new Date().getTime());
        byte[] nonceByte = dateTimeString.getBytes();
        return Base64.getEncoder().encodeToString(nonceByte);
    }
}

