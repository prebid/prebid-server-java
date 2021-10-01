package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.*;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.huaweiads.model.*;
import org.prebid.server.bidder.huaweiads.model.HuaweiApp;
import org.prebid.server.bidder.huaweiads.model.HuaweiFormat;
import org.prebid.server.bidder.model.*;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuaweiAds;
import org.prebid.server.util.HttpUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;


public class HuaweiAdsBidder implements Bidder<HuaweiRequest> {

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
    public Result<List<HttpRequest<HuaweiRequest>>> makeHttpRequests(BidRequest request) {
        final List<HuaweiAdSlot> multislot = new ArrayList<>();
        HuaweiRequest huaweiAdsRequest = new HuaweiRequest();
        ExtImpHuaweiAds extImpHuaweiAds = null;
        for (Imp imp : request.getImp()) {
            try {
                extImpHuaweiAds = HuaweiUtil.parseImpExt(imp, mapper, IMP_EXT_TYPE_REFERENCE, HUAWEIADS_EXT_TYPE_REFERENCE);
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
        return Result.withValue(HttpRequest.<HuaweiRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(resolveHeaders(extImpHuaweiAds, request, isTestAuthorization))
                .payload(huaweiAdsRequest)
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
    public Result<List<BidderBid>> makeBids(HttpCall<HuaweiRequest> httpCall, BidRequest bidRequest) {
        HuaweiResponse huaweiAdsResponse;
        List<BidderBid> bidderResponse;
        try {
            huaweiAdsResponse = mapper.decodeValue(httpCall.getResponse().getBody(), HuaweiResponse.class);
            checkHuaweiAdsResponseRetcode(huaweiAdsResponse);
            bidderResponse = HuaweiUtil.convertHuaweiAdsRespToBidderResp(huaweiAdsResponse, bidRequest, mapper, IMP_EXT_TYPE_REFERENCE, HUAWEIADS_EXT_TYPE_REFERENCE).getSeatBid().getBids();
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
        return Result.of(bidderResponse, Collections.emptyList());
    }

    private HuaweiAdSlot getHuaweiAdsReqAdslot(ExtImpHuaweiAds extImpHuaweiAds, BidRequest request, Imp imp) {
        String lowerAdType = StringUtils.lowerCase(extImpHuaweiAds.getAdtype());

        HuaweiAdSlot huaweiAdSlot = HuaweiAdSlot.builder()
                .slotId(extImpHuaweiAds.getSlotId())
                .adType(convertAdtypeString2Integer(lowerAdType))
                .test(request.getTest())
                .build();
        Banner banner = imp.getBanner();
        if (banner != null) {
            if (banner.getW() != null && banner.getH() != null) {
                huaweiAdSlot.setW(banner.getW());
                huaweiAdSlot.setH(banner.getH());
            }
            if (banner.getFormat().size() != 0) {
                List<HuaweiFormat> newFormats = banner.getFormat().stream()
                        .filter(oldFormat -> oldFormat.getH() != 0 && oldFormat.getW() != 0)
                        .map(oldFormat -> HuaweiFormat.of(oldFormat.getW(), oldFormat.getH()))
                        .collect(Collectors.toList());
                huaweiAdSlot.setFormat(newFormats);
            }
        } else if (imp.getXNative() != null) {
            getNativeFormat(huaweiAdSlot, imp);
            return huaweiAdSlot;
        }
        if (lowerAdType.equals("roll")) {
            Video video = imp.getVideo();
            if (video != null && video.getMaxduration()!= null && video.getMaxduration() >= 0) {
                huaweiAdSlot.setTotalDuration(video.getMaxduration());
            } else {
                throw new PreBidException("GetHuaweiAdsReqAdslot: Video maxDuration is empty when adtype is roll");
            }
        }
        return huaweiAdSlot;
    }

    private void getNativeFormat(HuaweiAdSlot huaweiAdSlot, Imp imp) {
        String request = imp.getXNative().getRequest();
        if (StringUtils.isBlank(request)) {
            throw new PreBidException("getNativeFormat: imp.xNative.request is empty");
        }
        HuaweiNativeRequest nativePayload = null;
        try {
            nativePayload = mapper.mapper().readValue(request, HuaweiNativeRequest.class);
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

        huaweiAdSlot.setH(height);
        huaweiAdSlot.setW(width);

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
        huaweiAdSlot.setDetailedCreativeTypeList(detailedCreativeTypeList);
    }

    private int convertAdtypeString2Integer(String adType) {
        switch (adType) { //cant use enum cause of jackson
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

    private void getHuaweiAdsReqJson(HuaweiRequest huaweiAdsRequest, BidRequest bidRequest, ExtImpHuaweiAds extImpHuaweiAds) {
        huaweiAdsRequest.setVersion(HUAWEI_ADX_API_VERSION);
        getHuaweiAdsReqAppInfo(huaweiAdsRequest, bidRequest);
        getHuaweiAdsReqDeviceInfo(huaweiAdsRequest, bidRequest);
        resolveHuaweiAdsReqGeoInfo(huaweiAdsRequest, bidRequest);
        resolveHuaweiAdsReqNetWorkInfo(huaweiAdsRequest, bidRequest);
        resolveHuaweiAdsReqRegsInfo(huaweiAdsRequest, bidRequest);
    }

    private void getHuaweiAdsReqAppInfo(HuaweiRequest huaweiAdsRequest, BidRequest bidRequest) {
        HuaweiApp huaweiAdsApp;
        App appFromBidReq = bidRequest.getApp();

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

    private void getHuaweiAdsReqDeviceInfo(HuaweiRequest huaweiAdsRequest, BidRequest bidRequest) {
        HuaweiDevice huaweiAdsDevice;
        Device bidRequestDevice = bidRequest.getDevice();
        String country = resolveCountry(bidRequest);

        if (bidRequestDevice != null) {
            huaweiAdsDevice = HuaweiDevice.builder()
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
            huaweiAdsDevice = new HuaweiDevice();
        }

        getDeviceId(huaweiAdsDevice, bidRequest);
        huaweiAdsRequest.setDevice(huaweiAdsDevice);
    }

    private void getDeviceId(HuaweiDevice huaweiAdsDevice, BidRequest request) {
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

    private static String resolveCountry(BidRequest bidRequest) {
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
    private static String convertCountryCode(String country) {
        Map<String, String> mapCountryCodeAlpha3ToAlpha2 = new HashMap<>(); //TODO add Ukraine
        mapCountryCodeAlpha3ToAlpha2.put("CHL",  "CL");
        mapCountryCodeAlpha3ToAlpha2.put("CHN", "CN");
        mapCountryCodeAlpha3ToAlpha2.put( "ARE", "AE");
        String countryPresentInMap = mapCountryCodeAlpha3ToAlpha2.keySet().stream()
                .filter(element -> element.equals(country))
                .findFirst()
                .orElse(null);

        if(countryPresentInMap != null) {
            return mapCountryCodeAlpha3ToAlpha2.get(countryPresentInMap);
        }
        if(country.length() > 2) {
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

    private void resolveHuaweiAdsReqNetWorkInfo (HuaweiRequest huaweiAdsRequest, BidRequest bidRequest) {
        Device device = bidRequest.getDevice();
        if (device != null ) {
            HuaweiNetwork network = new HuaweiNetwork();
            network.setType(device.getConnectiontype() != null
                    ? device.getConnectiontype()
                    : DEFAULT_UNKNOWN_NETWORK_TYPE);
            network.setCarrier(0);

            String[] arrMccmnc = device.getMccmnc().split("-");
            List<HuaweiCellInfo> cellInfos = new ArrayList<>();

            if(arrMccmnc.length > 2) {
                cellInfos.add(HuaweiCellInfo.of(arrMccmnc[0], arrMccmnc[1]));
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

    private void resolveHuaweiAdsReqRegsInfo(HuaweiRequest huaweiAdsRequest, BidRequest bidRequest) {
        Regs regs = bidRequest.getRegs();
        if(regs != null && regs.getCoppa() >= 0) {
            huaweiAdsRequest.setRegs(Regs.of(regs.getCoppa(), null));
        }
    }

    private void resolveHuaweiAdsReqGeoInfo(HuaweiRequest huaweiAdsRequest, BidRequest bidRequest) {
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

    private void checkHuaweiAdsResponseRetcode(HuaweiResponse response)  {
        Integer retcode = response.getRetcode(); //check retcode on null
        if ((retcode < 600 && retcode >= 400) || (retcode < 300 && retcode > 200)) {
            throw new PreBidException("HuaweiAdsResponse retcode: " + retcode);
            }
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

