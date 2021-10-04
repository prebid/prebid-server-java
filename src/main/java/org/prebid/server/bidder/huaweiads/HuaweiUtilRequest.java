package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.huaweiads.model.ExtUserDataDeviceIdHuaweiAds;
import org.prebid.server.bidder.huaweiads.model.ExtUserDataHuaweiAds;
import org.prebid.server.bidder.huaweiads.model.HuaweiApp;
import org.prebid.server.bidder.huaweiads.model.HuaweiCellInfo;
import org.prebid.server.bidder.huaweiads.model.HuaweiDevice;
import org.prebid.server.bidder.huaweiads.model.HuaweiNetwork;
import org.prebid.server.bidder.huaweiads.model.HuaweiRequest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HuaweiUtilRequest {

    private static final String HUAWEI_ADX_API_VERSION = "3.4";
    private static final String DEFAULT_COUNTRY_NAME = "ZA";
    private static final String DEFAULT_MODEL_NAME = "HUAWEI";
    private static final int DEFAULT_UNKNOWN_NETWORK_TYPE = 0;
    private static final String DEFAULT_TIME_ZONE = "+0200";

    private HuaweiUtilRequest() {
    }

    public static void getHuaweiAdsReqJson(HuaweiRequest huaweiAdsRequest, BidRequest bidRequest,
                                           JacksonMapper mapper) {
        huaweiAdsRequest.setVersion(HUAWEI_ADX_API_VERSION);
        getHuaweiAdsReqAppInfo(huaweiAdsRequest, bidRequest);
        getHuaweiAdsReqDeviceInfo(huaweiAdsRequest, bidRequest, mapper);
        resolveHuaweiAdsReqGeoInfo(huaweiAdsRequest, bidRequest);
        resolveHuaweiAdsReqNetWorkInfo(huaweiAdsRequest, bidRequest);
        resolveHuaweiAdsReqRegsInfo(huaweiAdsRequest, bidRequest);
    }

    private static void getHuaweiAdsReqAppInfo(HuaweiRequest huaweiAdsRequest, BidRequest bidRequest) {
        final HuaweiApp huaweiAdsApp;
        final App appFromBidReq = bidRequest.getApp();

        if (appFromBidReq != null) {
            huaweiAdsApp = HuaweiApp.builder()
                    .version(StringUtils.isBlank(appFromBidReq.getVer()) ? null : appFromBidReq.getVer())
                    .name(StringUtils.isBlank(appFromBidReq.getName()) ? null : appFromBidReq.getName())
                    .pkgname(resolvePkgName(appFromBidReq))
                    .lang(resolveLang(appFromBidReq))
                    .country(resolveCountry(bidRequest))
                    .build();
            huaweiAdsRequest.setApp(huaweiAdsApp);
        }
    }

    private static void getHuaweiAdsReqDeviceInfo(HuaweiRequest huaweiAdsRequest, BidRequest bidRequest,
                                                  JacksonMapper mapper) {
        final HuaweiDevice huaweiAdsDevice;
        final Device bidReqDev = bidRequest.getDevice();
        final String country = resolveCountry(bidRequest);

        if (bidReqDev != null) {
            huaweiAdsDevice = HuaweiDevice.builder()
                    .type(bidReqDev.getDevicetype())
                    .useragent(bidReqDev.getUa())
                    .os(bidReqDev.getOs())
                    .version(bidReqDev.getOsv())
                    .maker(bidReqDev.getMake())
                    .model(StringUtils.isBlank(bidReqDev.getModel())
                            ? DEFAULT_MODEL_NAME
                            : bidReqDev.getModel())
                    .height(bidReqDev.getH())
                    .width(bidReqDev.getW())
                    .language(bidReqDev.getLanguage())
                    .pxratio(bidReqDev.getPxratio())
                    .belongCountry(country)
                    .localeCountry(country)
                    .ip(bidReqDev.getIp())
                    .build();
        } else {
            huaweiAdsDevice = new HuaweiDevice();
        }

        getDeviceId(huaweiAdsDevice, bidRequest, mapper);
        huaweiAdsRequest.setDevice(huaweiAdsDevice);
    }

    private static void getDeviceId(HuaweiDevice huaweiAdsDevice, BidRequest request, JacksonMapper mapper) {
        final User user = request.getUser();
        final ExtUserDataHuaweiAds extUserDataHuaweiAds;
        if (user == null) {
            throw new PreBidException("getDeviceID: BidRequest.user is null");
        }
        if (user.getExt() == null) {
            throw new PreBidException("getDeviceID: BidRequest.user.ext is null");
        }
        try {
            extUserDataHuaweiAds = mapper.mapper().readValue(request.getUser().getExt().getData().toString(),
                    ExtUserDataHuaweiAds.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Unmarshal: BidRequest.user.ext -> extUserDataHuaweiAds failed");
        }
        if (extUserDataHuaweiAds.getData() == null) {
            throw new PreBidException("Unmarshal: BidRequest.user.ext -> extUserDataHuaweiAds failed");
        }
        ExtUserDataDeviceIdHuaweiAds deviceId = extUserDataHuaweiAds.getData();
        String[] imei = deviceId.getImei();
        String[] oaid = deviceId.getOaid();
        String[] gaid = deviceId.getGaid();
        String[] clientTime = deviceId.getClientTime();

        if (imei.length == 0 && gaid.length == 0 && oaid.length == 0) {
            throw new PreBidException("getDeviceID: Imei ,Oaid, Gaid are all empty");
        }
        if (oaid.length > 0) {
            huaweiAdsDevice.setOaid(oaid[0]);
        }
        if (imei.length > 0) {
            huaweiAdsDevice.setImei(imei[0]);
        }
        if (gaid.length > 0) {
            huaweiAdsDevice.setGaid(gaid[0]);
        }
        if (clientTime.length > 0) {
            huaweiAdsDevice.setClientTime(resolveClientTime(clientTime[0]));
        }

        final Device device = request.getDevice();
        if (device != null && device.getDnt() != null) {
            if (!StringUtils.isBlank(huaweiAdsDevice.getOaid())) {
                huaweiAdsDevice.setIsTrackingEnabled(String.valueOf(1 - device.getDnt()));
            }
            if (!StringUtils.isBlank(huaweiAdsDevice.getGaid())) {
                huaweiAdsDevice.setGaidTrackingEnabled(String.valueOf(1 - device.getDnt()));
            }
        }
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

    // convertCountryCode: ISO 3166-1 Alpha3 -> Alpha2, Some countries may use
    private static String convertCountryCode(String country) {
        Map<String, String> mapCountryCodeAlpha3ToAlpha2 = new HashMap<>(); //TODO add Ukraine
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

    private static void resolveHuaweiAdsReqNetWorkInfo(HuaweiRequest huaweiAdsRequest,
                                                       BidRequest bidRequest) {
        Device device = bidRequest.getDevice();
        if (device != null) {
            huaweiAdsRequest.setNetwork(HuaweiNetwork.builder()
                    .type(device.getConnectiontype() != null
                            ? device.getConnectiontype()
                            : DEFAULT_UNKNOWN_NETWORK_TYPE)
                    .carrier(resolveCarrier(device))
                    .cellInfoList(resolveCellInfoList(device))
                    .build());
        }
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

    private static void resolveHuaweiAdsReqRegsInfo(HuaweiRequest huaweiAdsRequest, BidRequest bidRequest) {
        Regs regs = bidRequest.getRegs();
        if (regs != null && regs.getCoppa() >= 0) {
            huaweiAdsRequest.setRegs(Regs.of(regs.getCoppa(), null));
        }
    }

    private static void resolveHuaweiAdsReqGeoInfo(HuaweiRequest huaweiAdsRequest, BidRequest bidRequest) {
        Device device = bidRequest.getDevice();
        Geo geo = device != null ? device.getGeo() : null;
        if (geo != null) {
            huaweiAdsRequest.setGeo(Geo.builder()
                    .lon(geo.getLon())
                    .lat(geo.getLat())
                    .accuracy(geo.getAccuracy())
                    .build());
        }
    }

    private static String resolveClientTime(String clientTime) {
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

}
