package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.ImageObject;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.huaweiads.model.ExtUserDataDeviceIdHuaweiAds;
import org.prebid.server.bidder.huaweiads.model.ExtUserDataHuaweiAds;
import org.prebid.server.bidder.huaweiads.model.HuaweiAdSlot;
import org.prebid.server.bidder.huaweiads.model.HuaweiApp;
import org.prebid.server.bidder.huaweiads.model.HuaweiCellInfo;
import org.prebid.server.bidder.huaweiads.model.HuaweiDevice;
import org.prebid.server.bidder.huaweiads.model.HuaweiFormat;
import org.prebid.server.bidder.huaweiads.model.HuaweiNativeRequest;
import org.prebid.server.bidder.huaweiads.model.HuaweiNetwork;
import org.prebid.server.bidder.huaweiads.model.HuaweiRequest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuawei;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HuaweiUtilRequest<T> {

    private static final String HUAWEI_ADX_API_VERSION = "3.4";
    private static final String DEFAULT_COUNTRY_NAME = "ZA";
    private static final String DEFAULT_MODEL_NAME = "HUAWEI";
    private static final int DEFAULT_UNKNOWN_NETWORK_TYPE = 0;
    private static final String DEFAULT_TIME_ZONE = "+0200";

    private HuaweiUtilRequest() {
    }

    public static HuaweiRequest resolveHuaweiReq(ExtImpHuawei extImpHuawei, Imp imp,
                                                 BidRequest bidRequest, JacksonMapper mapper) {
        return HuaweiRequest.builder()
                .multislot(List.of(resolveHuaweiReqAdslot(extImpHuawei, bidRequest, imp, mapper)))
                .version(HUAWEI_ADX_API_VERSION)
                .app(resolveHuaweiApp(bidRequest))
                .device(resolveHuaweiDevice(bidRequest, mapper))
                .geo(resolveGeo(bidRequest))
                .network(resolveHuaweiNetwork(bidRequest))
                .regs(resolveRegs(bidRequest))
                .build();
    }

    private static HuaweiAdSlot resolveHuaweiReqAdslot(ExtImpHuawei extImpHuawei, BidRequest bidRequest,
                                                       Imp imp, JacksonMapper mapper) {
        final String lowerAdType = StringUtils.lowerCase(extImpHuawei.getAdType());
        final Banner banner = imp.getBanner();

        final boolean ifBanner = banner != null;
        List<Asset> nativeAssets = !ifBanner ? getAssets(imp, mapper) : null;

        return HuaweiAdSlot.builder()
                .slotId(extImpHuawei.getSlotId())
                .adType(convertAdtypeString2Integer(lowerAdType))
                .test(bidRequest.getTest())
                .format(ifBanner ? resolveFormat(banner) : null)
                .w(ifBanner ? resolveBannerWidth(banner) : resolveNativeWidth(nativeAssets))
                .h(ifBanner ? resolveBannerHeight(banner) : resolveNativeHeight(nativeAssets))
                .totalDuration(resolveTotalDuration(imp, lowerAdType))
                .detailedCreativeTypeList(ifBanner ? null : resolveDetailedCreativeList(nativeAssets))
                .build();
    }

    private static List<String> resolveDetailedCreativeList(List<Asset> assets) {
        final ArrayList<String> detailedCreativeTypeList = new ArrayList<>();

        int numImage = 0;
        int numVideo = 0;

        if (assets == null) {
            return detailedCreativeTypeList;
        }

        for (Asset asset : assets) {
            if (asset.getVideo() != null) {
                numVideo++;
            }
            ImageObject image = asset.getImg();
            if (image != null) {
                numImage++;
            }
        }
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
        return detailedCreativeTypeList;
    }

    private static Integer resolveNativeWidth(List<Asset> assets) {
        if (assets == null) {
            return null;
        }
        Integer width = null;
        for (Asset asset : assets) {
            ImageObject image = asset.getImg();
            if (image != null) {
                int h = image.getH() != null ? image.getH() : 0;
                int w = image.getW() != null ? image.getW() : 0;
                int wMin = image.getWmin() != null ? image.getWmin() : 0;
                int hMin = image.getHmin() != null ? image.getHmin() : 0;

                if (h != 0 && w != 0) {
                    width = w;
                } else if (wMin != 0 && hMin != 0) {
                    width = wMin;
                }
            }
        }
        return width;
    }

    private static Integer resolveNativeHeight(List<Asset> assets) {
        if (assets == null) {
            return null;
        }
        Integer height = null;
        for (Asset asset : assets) {
            ImageObject image = asset.getImg();
            if (image != null) {
                int h = image.getH() != null ? image.getH() : 0;
                int w = image.getW() != null ? image.getW() : 0;
                int wMin = image.getWmin() != null ? image.getWmin() : 0;
                int hMin = image.getHmin() != null ? image.getHmin() : 0;

                if (h != 0 && w != 0) {
                    height = h;
                } else if (wMin != 0 && hMin != 0) {
                    height = hMin;
                }
            }
        }
        return height;
    }

    private static int convertAdtypeString2Integer(String adType) {
        switch (adType) {
            case "banner":
                return BidTypes.BANNER_CODE.getValue();
            case "native":
                return BidTypes.NATIVE_CODE.getValue();
            case "rewarded":
                return BidTypes.REWARDED_CODE.getValue();
            case "splash":
                return BidTypes.SPLASH_CODE.getValue();
            case "interstitial":
                return BidTypes.INTERSTITIAL_CODE.getValue();
            case "roll":
                return BidTypes.ROLL_CODE.getValue();
            default:
                return BidTypes.BANNER_CODE.getValue();
        }
    }

    private static Integer resolveTotalDuration(Imp imp, String lowerAdType) {
        if (lowerAdType.equals("roll")) {
            final Video video = imp.getVideo();
            if (video != null && video.getMaxduration() != null && video.getMaxduration() >= 0) {
                return video.getMaxduration();
            } else {
                throw new PreBidException("resolveTotalDuration: Video maxDuration is empty when adtype is roll");
            }
        }
        return null;
    }

    private static Integer resolveBannerWidth(Banner banner) {
        if (banner.getW() != null && banner.getH() != null) {
            return banner.getW();
        }
        return 0;
    }

    private static Integer resolveBannerHeight(Banner banner) {
        if (banner.getW() != null && banner.getH() != null) {
            return banner.getH();
        }
        return null;
    }

    private static List<HuaweiFormat> resolveFormat(Banner banner) {
        if (banner.getFormat().size() != 0) {
            return banner.getFormat().stream()
                    .filter(oldFormat -> oldFormat.getH() != 0 && oldFormat.getW() != 0)
                    .map(oldFormat -> HuaweiFormat.of(oldFormat.getW(), oldFormat.getH()))
                    .collect(Collectors.toList());
        }
        return null;
    }

    private static List<Asset> getAssets(Imp imp, JacksonMapper mapper) {
        final String request = checkOnNull(imp.getXNative(), "getAssets: imp.xnative is null").getRequest();
        if (StringUtils.isBlank(request)) {
            throw new PreBidException("getAssets: imp.xNative.request is empty");
        }
        HuaweiNativeRequest nativePayload;
        try {
            nativePayload = mapper.mapper().readValue(request, HuaweiNativeRequest.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }
        return nativePayload.getAssets();
    }

    private static HuaweiApp resolveHuaweiApp(BidRequest bidRequest) {
        final App appFromBidReq = bidRequest.getApp();

        if (appFromBidReq != null) {
            return HuaweiApp.builder()
                    .version(StringUtils.isBlank(appFromBidReq.getVer()) ? null : appFromBidReq.getVer())
                    .name(StringUtils.isBlank(appFromBidReq.getName()) ? null : appFromBidReq.getName())
                    .pkgname(resolvePkgName(appFromBidReq))
                    .lang(resolveLang(appFromBidReq))
                    .country(resolveCountry(bidRequest))
                    .build();
        }
        return null;
    }

    private static HuaweiDevice resolveHuaweiDevice(BidRequest bidRequest, JacksonMapper mapper) {
        final Device device = bidRequest.getDevice();
        if (device != null) {

            final ExtUserDataDeviceIdHuaweiAds data = resolveDeviceData(bidRequest, mapper);
            final boolean dataNotNull = data != null;

            final String oaid = dataNotNull ? resolveId(data.getOaid()) : null;
            final String gaid = dataNotNull ? resolveId(data.getGaid()) : null;
            final String imei = dataNotNull ? resolveId(data.getImei()) : null;
            final String clientTime = dataNotNull ? resolveClientTime(data.getClientTime()) : null;

            if (oaid == null && gaid == null && imei == null) {
                throw new PreBidException("resolveHuaweiDevice: Imei, Oaid, Gaid are all empty or null");
            }

            final Integer dnt = device.getDnt();
            final String country = resolveCountry(bidRequest);

            return HuaweiDevice.builder()
                    .type(device.getDevicetype())
                    .useragent(device.getUa())
                    .os(device.getOs())
                    .version(device.getOsv())
                    .maker(device.getMake())
                    .model(StringUtils.isBlank(device.getModel())
                            ? DEFAULT_MODEL_NAME
                            : device.getModel())
                    .height(device.getH())
                    .width(device.getW())
                    .language(device.getLanguage())
                    .pxratio(device.getPxratio())
                    .belongCountry(country)
                    .localeCountry(country)
                    .ip(device.getIp())
                    .gaid(gaid)
                    .oaid(oaid)
                    .imei(imei)
                    .clientTime(clientTime)
                    .isTrackingEnabled(resolveIsTrackingEnabled(dnt, oaid))
                    .isGaidTrackingEnabled(resolveIsTrackingEnabled(dnt, gaid)).build();
        }
        return HuaweiDevice.builder().build();
    }

    private static String resolveId(List<String> listId) {
        if (listId == null) {
            return null;
        }
        return !listId.isEmpty() ? listId.get(0) : null;
    }

    private static ExtUserDataDeviceIdHuaweiAds resolveDeviceData(BidRequest request, JacksonMapper mapper) {
        final User user = request.getUser();
        final ExtUserDataHuaweiAds extUserDataHuaweiAds;
        if (user == null) {
            return null;
        }
        ExtUser userExt = user.getExt();
        if (userExt == null) {
            return null;
        }

        try {
            extUserDataHuaweiAds = mapper.mapper().readValue(userExt.getData().toString(),
                    ExtUserDataHuaweiAds.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Unmarshal: BidRequest.user.ext -> extUserDataHuaweiAds failed");
        }
        return extUserDataHuaweiAds.getData();
    }

    private static String resolveCountry(BidRequest bidRequest) {
        Geo geo;
        String country;

        Device device = bidRequest.getDevice();
        geo = device != null ? device.getGeo() : null;
        country = geo != null ? geo.getCountry() : null;
        if (!StringUtils.isBlank(country)) {
            return convertCountryCode(country);
        }

        User user = bidRequest.getUser();
        geo = user != null ? user.getGeo() : null;
        country = geo != null ? geo.getCountry() : null;
        if (!StringUtils.isBlank(country)) {
            return convertCountryCode(country);
        }

        return DEFAULT_COUNTRY_NAME;
    }

    private static String convertCountryCode(String country) {
        Map<String, String> mapCountryCodeAlpha3ToAlpha2 = new HashMap<>();
        mapCountryCodeAlpha3ToAlpha2.put("CHL", "CL");
        mapCountryCodeAlpha3ToAlpha2.put("CHN", "CN");
        mapCountryCodeAlpha3ToAlpha2.put("ARE", "AE");
        String countryPresentInMap = mapCountryCodeAlpha3ToAlpha2.keySet().stream()
                .filter(element -> element.equals(country))
                .findFirst()
                .orElse(null);

        if (countryPresentInMap != null) {
            return mapCountryCodeAlpha3ToAlpha2.get(countryPresentInMap);
        }
        if (country.length() > 2) {
            return country.substring(0, 2);
        }
        return DEFAULT_COUNTRY_NAME;
    }

    private static String resolvePkgName(App appFromBidReq) {
        String bundle = appFromBidReq.getBundle();
        if (StringUtils.isBlank(bundle)) {
            throw new PreBidException("resolvePkgName: app.bundle is empty");
        }
        return bundle;
    }

    private static String resolveLang(App appFromBidReq) {
        String lang = appFromBidReq.getContent() != null ? appFromBidReq.getContent().getLanguage() : null;
        if (StringUtils.isBlank(lang)) {
            return "en";
        }
        return lang;
    }

    private static HuaweiNetwork resolveHuaweiNetwork(BidRequest bidRequest) {
        Device device = bidRequest.getDevice();
        if (device != null) {
            return HuaweiNetwork.builder()
                    .type(device.getConnectiontype() != null
                            ? device.getConnectiontype()
                            : DEFAULT_UNKNOWN_NETWORK_TYPE)
                    .carrier(resolveCarrier(device))
                    .cellInfoList(resolveCellInfoList(device))
                    .build();
        }
        return null;
    }

    private static Integer resolveCarrier(Device device) {
        String[] arrMccmnc = device.getMccmnc().split("-");
        String str = arrMccmnc[0] + arrMccmnc[1];
        if (str.equals("46000") || str.equals("46002") || str.equals("46007")) {
            return 2;
        } else if (str.equals("46001") || str.equals("46006")) {
            return 1;
        } else if (str.equals("46003") || str.equals("46005") || str.equals("46011")) {
            return 3;
        } else {
            return 99;
        }
    }

    private static List<HuaweiCellInfo> resolveCellInfoList(Device device) {
        String[] arrMccmnc = device.getMccmnc().split("-");
        if (arrMccmnc.length > 2) {
            return List.of(HuaweiCellInfo.of(arrMccmnc[0], arrMccmnc[1]));
        }
        return null;
    }

    private static Regs resolveRegs(BidRequest bidRequest) {
        Regs regs = bidRequest.getRegs();
        if (regs != null && regs.getCoppa() >= 0) {
            return Regs.of(regs.getCoppa(), null);
        }
        return null;
    }

    private static Geo resolveGeo(BidRequest bidRequest) {
        Device device = bidRequest.getDevice();
        Geo geo = device != null ? device.getGeo() : null;
        if (geo != null) {
            return Geo.builder()
                    .lon(geo.getLon())
                    .lat(geo.getLat())
                    .accuracy(geo.getAccuracy())
                    .build();
        }
        return null;
    }

    private static String resolveClientTime(List<String> clientTimeList) {
        if (clientTimeList == null || clientTimeList.isEmpty()) {
            return null;
        }
        String clientTime = clientTimeList.get(0);

        String zone = DEFAULT_TIME_ZONE;
        String format = "yyyy-MM-dd'T'HH:mm:ssXXX";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        String t = ZonedDateTime.now().format(formatter);
        int index = t.contains("+") ? t.indexOf("+") : t.indexOf("-");
        if (index > 0 && t.length() - index == 5) {
            zone = t.substring(index);
        }
        if (StringUtils.isBlank(clientTime)) {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format)) + zone;
        }
        if (clientTime.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]{1}\\d{4}$")) {
            return clientTime;
        }
        if (clientTime.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}$")) {
            return clientTime + zone;
        }
        return ZonedDateTime.now().format(formatter) + zone;

    }

    private static String resolveIsTrackingEnabled(Integer dnt, String oaid) {
        if (dnt != null && !StringUtils.isBlank(oaid)) {
            return String.valueOf(1 - dnt);
        }
        return null;
    }

    private static <T> T checkOnNull(T object, String errorMessage) {
        if (object == null) {
            throw new PreBidException(errorMessage);
        }
        return object;
    }

}


