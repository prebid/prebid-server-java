package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.*;
import com.iab.openrtb.request.Device;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.huaweiads.model.*;
import org.prebid.server.bidder.huaweiads.model.Format;
import org.prebid.server.bidder.model.*;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuaweiAds;
import org.prebid.server.util.HttpUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class HuaweiAdsBidder implements Bidder<Request> {

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
    public Result<List<HttpRequest<Request>>> makeHttpRequests(BidRequest request) {
        final List<Adslot> multislot = new ArrayList<>();
        HuaweiAdsRequest huaweiAdsRequest = new HuaweiAdsRequest();
        for (Imp imp : request.getImp()) {
            try {
                ExtImpHuaweiAds extImpHuaweiAds = parseImpExt(imp);
                multislot.add(getHuaweiAdsReqAdslot(extImpHuaweiAds, request, imp));
            } catch (PreBidException e) {
                return Result.withErrors(Collections.singletonList(BidderError.badInput(e.getMessage())));
            }
        }
        huaweiAdsRequest.setMultislot(multislot);

        return Result.withValue();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<Request> httpCall, BidRequest bidRequest) {
        return null;
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
            if (video != null && video.getMaxduration() >= 0) {
                adslot.setTotalDuration(video.getMaxduration());
            } else {
                throw new PreBidException("GetHuaweiAdsReqAdslot: MaxDuration is empty when adtype is roll");
            }
        }
        return adslot;
    }

    private void getNativeFormat(Adslot adslot, Imp imp) {
        if (StringUtils.isBlank(imp.getXNative().getRequest())) {
            throw new PreBidException("extractAdmNative: imp.xNative.request is empty");
        }
        NativeRequest nativePayload = null;
        try {
            nativePayload = mapper.mapper().readValue(imp.getXNative().getRequest(), NativeRequest.class);
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

    }

    private void getHuaweiAdsReqAppInfo(HuaweiAdsRequest huaweiAdsRequest, BidRequest bidRequest) {
        HuaweiAdsApp app;
        App appFromBidReq = bidRequest.getApp();

        if (appFromBidReq != null) {
            app = HuaweiAdsApp.builder()
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
             extUserDataHuaweiAds = mapper.mapper().readValue(request.getUser().getExt().toString(), ExtUserDataHuaweiAds.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Unmarshal: BidRequest.user.ext -> extUserDataHuaweiAds failed");
        }
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
                huaweiAdsDevice.setIsTrackingEnabled(); //?
            }
            if(!StringUtils.isBlank(huaweiAdsDevice.getGaid())) {
                huaweiAdsDevice.setGaidTrackingEnabled(); //?
            }
        }


    }

    private String resolveClientTime(String clientTime) {
    String zone = DEFAULT_TIME_ZONE;
    String format = "02 Jan 06 15:04 -0700";
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
            throw new PreBidException("HuaweiAdsReqApp: Pkgname is empty.");
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
            HuaweiAdsNetwork network = HuaweiAdsNetwork.builder()
                    .type(device.getConnectiontype() != null ? device.getConnectiontype() : DEFAULT_UNKNOWN_NETWORK_TYPE)
                    .carrier(resolveNetworkCarrier(device))
        }
    }

    private Integer resolveNetworkCarrier(Device device) {
        String[] arr = device.getMccmnc().split("-");

    }


}
