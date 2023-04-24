package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Request;
import com.iab.openrtb.request.User;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.huaweiads.model.request.AdSlot30;
import org.prebid.server.bidder.huaweiads.model.request.AdsType;
import org.prebid.server.bidder.huaweiads.model.request.App;
import org.prebid.server.bidder.huaweiads.model.request.CellInfo;
import org.prebid.server.bidder.huaweiads.model.request.ChineseSiteCountryCode;
import org.prebid.server.bidder.huaweiads.model.request.ClientTimeConverter;
import org.prebid.server.bidder.huaweiads.model.request.CountryCode;
import org.prebid.server.bidder.huaweiads.model.request.Device;
import org.prebid.server.bidder.huaweiads.model.request.EuropeanSiteCountryCode;
import org.prebid.server.bidder.huaweiads.model.request.Format;
import org.prebid.server.bidder.huaweiads.model.request.Geo;
import org.prebid.server.bidder.huaweiads.model.request.HuaweiAdsRequest;
import org.prebid.server.bidder.huaweiads.model.request.Network;
import org.prebid.server.bidder.huaweiads.model.request.PkgNameConvert;
import org.prebid.server.bidder.huaweiads.model.request.Regs;
import org.prebid.server.bidder.huaweiads.model.request.RussianSiteCountryCode;
import org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuaweiAds;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtUserDataDeviceIdHuaweiAds;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtUserDataHuaweiAds;
import org.prebid.server.util.HttpUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HuaweiAdsBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpHuaweiAds>> HUAWEI_ADS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final JacksonMapper mapper;
    private final String endpointUrl;
    private final List<PkgNameConvert> pkgNameConvert;
    private final String closeSiteSelectionByCountry;

    public HuaweiAdsBidder(String endpoint,
                           List<PkgNameConvert> pkgNameConvert,
                           String closeSiteSelectionByCountry,
                           JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(endpoint);
        this.pkgNameConvert = pkgNameConvert;
        this.closeSiteSelectionByCountry = closeSiteSelectionByCountry;
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        HuaweiAdsRequest.HuaweiAdsRequestBuilder huaweiAdsRequest = HuaweiAdsRequest.builder();
        final List<AdSlot30> multislot = new ArrayList<>();

        ExtImpHuaweiAds extImpHuaweiAds = null;

        for (final Imp imp : bidRequest.getImp()) {
            extImpHuaweiAds = unmarshalExtImpHuaweiAds(imp);

            final AdSlot30.AdSlot30Builder adSlot30 = getReqAdslot30(extImpHuaweiAds, imp);
            multislot.add(adSlot30.build());
        }

        huaweiAdsRequest.multislot(multislot);
        huaweiAdsRequest.clientAdRequestId(bidRequest.getId());

        final String countryCode = getReqJson(huaweiAdsRequest, bidRequest);

        return Result.withValue(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(buildEndpoint(countryCode))
                        .headers(getHeaders(extImpHuaweiAds, bidRequest))
                        .body(mapper.encodeToBytes(huaweiAdsRequest))
                        .payload(bidRequest)
                        .build());
    }

    private ExtImpHuaweiAds unmarshalExtImpHuaweiAds(Imp openRTBImp) {
        final ExtImpHuaweiAds huaweiAdsImpExt = parseImpExt(openRTBImp);

        if (StringUtils.isBlank(huaweiAdsImpExt.getSlotId())) {
            throw new PreBidException("ExtImpHuaweiAds: slotid is empty.");
        }
        if (StringUtils.isBlank(huaweiAdsImpExt.getAdtype())) {
            throw new PreBidException("ExtImpHuaweiAds: adtype is empty.");
        }
        if (StringUtils.isBlank(huaweiAdsImpExt.getPublisherId())) {
            throw new PreBidException("ExtHuaweiAds: publisherid is empty.");
        }
        if (StringUtils.isBlank(huaweiAdsImpExt.getSignKey())) {
            throw new PreBidException("ExtHuaweiAds: signkey is empty.");
        }
        if (StringUtils.isBlank(huaweiAdsImpExt.getKeyId())) {
            throw new PreBidException("ExtImpHuaweiAds: keyid is empty.");
        }
        return huaweiAdsImpExt;
    }

    private ExtImpHuaweiAds parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), HUAWEI_ADS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Unmarshalling error: " + e.getMessage());
        }
    }

    private AdSlot30.AdSlot30Builder getReqAdslot30(ExtImpHuaweiAds huaweiAdsImpExt, Imp imp) {
        final int adtype = convertAdtypeStringToInteger(huaweiAdsImpExt.getAdtype().toLowerCase());
        final int testStatus = huaweiAdsImpExt.getIsTestAuthorization().equals("true") ? 1 : 0;

        final AdSlot30.AdSlot30Builder adslot30 = AdSlot30.builder();
        adslot30.slotid(huaweiAdsImpExt.getSlotId());
        adslot30.adtype(adtype);
        adslot30.test(testStatus);

        checkAndExtractOpenrtbFormat(adslot30, adtype, huaweiAdsImpExt.getAdtype(), imp);

        return adslot30;
    }

    private static int convertAdtypeStringToInteger(String adtypeLower) {
        return switch (adtypeLower) {
            case "native" -> AdsType.XNATIVE.getCode();
            case "rewarded" -> AdsType.REWARDED.getCode();
            case "interstitial" -> AdsType.INTERSTITIAL.getCode();
            case "roll" -> AdsType.ROLL.getCode();
            case "splash" -> AdsType.SPLASH.getCode();
            case "magazinelock" -> AdsType.MAGAZINE_LOCK.getCode();
            case "audio" -> AdsType.AUDIO.getCode();
            default -> AdsType.BANNER.getCode();
        };
    }

    private void checkAndExtractOpenrtbFormat(AdSlot30.AdSlot30Builder adslot30,
                                              int adtype,
                                              String impExtAdType,
                                              Imp imp) {

        if (imp.getBanner() != null) {
            if (adtype != AdsType.BANNER.getCode() && adtype != AdsType.INTERSTITIAL.getCode()) {
                throw new PreBidException(
                        "check openrtb format: request has banner, doesn't correspond to huawei adtype "
                                + impExtAdType);
            }
            getBannerFormat(adslot30, imp);
        } else if (imp.getXNative() != null) {
            if (adtype != AdsType.XNATIVE.getCode()) {
                throw new PreBidException(
                        "check openrtb format: request has native, doesn't correspond to huawei adtype "
                                + impExtAdType);
            }
            getNativeFormat(adslot30, imp);
        } else if (imp.getVideo() != null) {
            if (adtype != AdsType.BANNER.getCode()
                    && adtype != AdsType.INTERSTITIAL.getCode()
                    && adtype != AdsType.REWARDED.getCode()
                    && adtype != AdsType.ROLL.getCode()) {
                throw new PreBidException(
                        "check openrtb format: request has video, doesn't correspond to huawei adtype " + impExtAdType);
            }
            getVideoFormat(adslot30, adtype, imp);
        } else if (imp.getAudio() != null) {
            throw new PreBidException("check openrtb format: request has audio, not currently supported");
        } else {
            throw new PreBidException(
                    "check openrtb format: please choose one of our supported type banner, native, or video");
        }
    }

    private void getBannerFormat(AdSlot30.AdSlot30Builder adslot30, Imp imp) {
        if (imp.getBanner().getW() != null && imp.getBanner().getH() != null) {

            adslot30.w(imp.getBanner().getW());
            adslot30.h(imp.getBanner().getH());
        }

        if (CollectionUtils.isNotEmpty(imp.getBanner().getFormat())) {
            final List<Format> formats = imp.getBanner().getFormat()
                    .stream()
                    .filter(f -> f.getH() != 0 && f.getW() != 0)
                    .map(f -> Format.of(f.getW(), f.getH()))
                    .collect(Collectors.toList());

            adslot30.format(formats);
        }
    }

    private void getNativeFormat(AdSlot30.AdSlot30Builder adslot30, Imp imp) {
        if (StringUtils.isBlank(imp.getXNative().getRequest())) {
            throw new PreBidException("extract openrtb native failed: imp.Native.Request is empty");
        }

        Request nativePayload;
        try {
            nativePayload = mapper.mapper().readValue(imp.getXNative().getRequest(), Request.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("Error decoding native: " + e.getMessage());
        }

        int numMainImage = 0;
        int numVideo = 0;
        int width = 0;
        int height = 0;

        for (Asset asset : nativePayload.getAssets()) {
            if (asset.getVideo() != null) {
                numVideo++;
                continue;
            }
            if (asset.getImg() != null
                    && Objects.equals(asset.getImg().getType(), HuaweiAdsConstants.IMAGE_ASSET_TYPE_MAIN)) {

                numMainImage++;
                if (asset.getImg().getH() != null && asset.getImg().getW() != null) {
                    width = asset.getImg().getW();
                    height = asset.getImg().getH();
                } else if (asset.getImg().getWmin() != null && asset.getImg().getHmin() != null) {
                    width = asset.getImg().getWmin();
                    height = asset.getImg().getHmin();
                }
            }
        }

        adslot30.w(width);
        adslot30.h(height);

        final List<String> detailedCreativeTypeList = new ArrayList<>();

        if (numVideo >= 1) {
            detailedCreativeTypeList.add("903");
        } else if (numMainImage > 1) {
            detailedCreativeTypeList.add("904");
        } else if (numMainImage == 1) {
            detailedCreativeTypeList.add("901");
        } else {
            detailedCreativeTypeList.add("913");
            detailedCreativeTypeList.add("914");
        }

        adslot30.detailedCreativeTypeList(detailedCreativeTypeList);
    }

    private void getVideoFormat(AdSlot30.AdSlot30Builder adslot30, int adtype, Imp openRTBImp) {
        adslot30.w(openRTBImp.getVideo().getW());
        adslot30.h(openRTBImp.getVideo().getH());

        if (adtype == AdsType.ROLL.getCode()) {
            if (openRTBImp.getVideo().getMaxduration() == 0) {
                throw new PreBidException(
                        "Extract openrtb video failed: MaxDuration is empty when huaweiads adtype is roll.");
            }
            adslot30.totalDuration(openRTBImp.getVideo().getMaxduration());
        }
    }

    private String getReqJson(HuaweiAdsRequest.HuaweiAdsRequestBuilder request, BidRequest openRTBRequest) {
        request.version(HuaweiAdsConstants.API_VERSION);
        final String countryCode = getReqAppInfo(request, openRTBRequest);
        getReqDeviceInfo(request, openRTBRequest);
        getReqNetworkInfo(request, openRTBRequest);
        getReqRegsInfo(request, openRTBRequest);
        getReqGeoInfo(request, openRTBRequest);
        getReqConsentInfo(request, openRTBRequest);
        return countryCode;
    }

    private String getReqAppInfo(HuaweiAdsRequest.HuaweiAdsRequestBuilder request, BidRequest openRTBRequest) {
        final App.AppBuilder app = App.builder();
        if (openRTBRequest.getApp() != null) {
            if (!openRTBRequest.getApp().getVer().isEmpty()) {
                app.version(openRTBRequest.getApp().getVer());
            }
            if (!openRTBRequest.getApp().getName().isEmpty()) {
                app.name(openRTBRequest.getApp().getName());
            }

            // bundle cannot be empty, we need package name.
            if (!openRTBRequest.getApp().getBundle().isEmpty()) {
                app.pkgname(getFinalPkgName(openRTBRequest.getApp().getBundle()));
            } else {
                throw new PreBidException("generate HuaweiAds AppInfo failed: openrtb BidRequest.App.Bundle is empty.");
            }

            if (openRTBRequest.getApp().getContent() != null
                    && !openRTBRequest.getApp().getContent().getLanguage().isEmpty()) {

                app.lang(openRTBRequest.getApp().getContent().getLanguage());
            } else {
                app.lang("en");
            }
        }
        final String countryCode = getCountryCode(openRTBRequest);
        app.country(countryCode);
        request.app(app.build());
        return countryCode;
    }

    private String getFinalPkgName(String bundleName) {
        for (PkgNameConvert convert : pkgNameConvert) {
            if (convert.getConvertedPkgName().isEmpty()) {
                continue;
            }

            for (String name : convert.getExceptionPkgNames()) {
                if (name.equals(bundleName)) {
                    return bundleName;
                }
            }

            for (String name : convert.getUnconvertedPkgNames()) {
                if (name.equals(bundleName) || name.equals("*")) {
                    return convert.getConvertedPkgName();
                }
            }

            for (String keyword : convert.getUnconvertedPkgNameKeyWords()) {
                if (bundleName.indexOf(keyword) > 0) {
                    return convert.getConvertedPkgName();
                }
            }

            for (String prefix : convert.getUnconvertedPkgNamePrefixs()) {
                if (bundleName.startsWith(prefix)) {
                    return convert.getConvertedPkgName();
                }
            }
        }

        return bundleName;
    }

    private void getReqDeviceInfo(HuaweiAdsRequest.HuaweiAdsRequestBuilder request, BidRequest openRTBRequest) {
        final Device.DeviceBuilder deviceBuilder = Device.builder();
        Optional.ofNullable(openRTBRequest.getDevice())
                .ifPresent(deviceData -> {
                    deviceBuilder.type(deviceData.getDevicetype());
                    deviceBuilder.useragent(deviceData.getUa());
                    deviceBuilder.os(deviceData.getOs());
                    deviceBuilder.version(deviceData.getOsv());
                    deviceBuilder.maker(deviceData.getMake());
                    deviceBuilder.model(deviceData.getModel().isEmpty()
                            ? HuaweiAdsConstants.DEFAULT_MODEL_NAME : deviceData.getModel());
                    deviceBuilder.height(deviceData.getH());
                    deviceBuilder.width(deviceData.getW());
                    deviceBuilder.language(deviceData.getLanguage());
                    deviceBuilder.pxratio(deviceData.getPxratio().floatValue());
                    String countryCode = getCountryCode(openRTBRequest);
                    deviceBuilder.belongCountry(countryCode);
                    deviceBuilder.localeCountry(countryCode);
                    deviceBuilder.ip(deviceData.getIp());
                    deviceBuilder.gaid(deviceData.getIfa());
                });

        getDeviceIDFromUserExt(deviceBuilder, openRTBRequest);

        final Device tempDevice = deviceBuilder.build();

        Optional.ofNullable(openRTBRequest.getDevice())
                .map(com.iab.openrtb.request.Device::getDnt)
                .ifPresent(dnt -> {
                    if (!tempDevice.getOaid().isEmpty()) {
                        deviceBuilder.isTrackingEnabled(dnt == 1 ? "0" : "1");
                    }
                    if (!tempDevice.getGaid().isEmpty()) {
                        deviceBuilder.gaidTrackingEnabled(dnt == 1 ? "0" : "1");
                    }
                });

        request.device(deviceBuilder.build());
    }

    private String getCountryCode(BidRequest openRTBRequest) {
        return Stream.of(openRTBRequest)
                .filter(request -> request.getDevice() != null
                        && request.getDevice().getGeo() != null
                        && !request.getDevice().getGeo().getCountry().isEmpty())
                .findFirst()
                .map(request -> CountryCode.convertCountryCode(request.getDevice().getGeo().getCountry()))
                .orElseGet(() -> Stream.of(openRTBRequest)
                        .filter(request -> request.getUser() != null
                                && request.getUser().getGeo() != null
                                && !request.getUser().getGeo().getCountry().isEmpty())
                        .findFirst()
                        .map(request -> CountryCode.convertCountryCode(request.getUser().getGeo().getCountry()))
                        .orElseGet(() -> Stream.of(openRTBRequest)
                                .filter(request -> request.getDevice() != null
                                        && !request.getDevice().getMccmnc().isEmpty())
                                .findFirst()
                                .map(request -> CountryCode.getCountryCodeFromMCC(request.getDevice().getMccmnc()))
                                .orElse(HuaweiAdsConstants.DEFAULT_COUNTRY_NAME)));
    }

    private void getDeviceIDFromUserExt(Device.DeviceBuilder device, BidRequest bidRequest) {
        final Optional<User> userOptional = Optional.ofNullable(bidRequest.getUser());
        final Device tempDevice = device.build();
        if (userOptional.isPresent() && userOptional.get().getExt() != null) {
            final ExtUserDataHuaweiAds extUserDataHuaweiAds;
            extUserDataHuaweiAds = mapper.mapper()
                    .convertValue(userOptional.get().getExt().getData(), ExtUserDataHuaweiAds.class);

            final ExtUserDataDeviceIdHuaweiAds deviceId = extUserDataHuaweiAds.getData();

            boolean isValidDeviceId = false;
            if (ArrayUtils.isEmpty(deviceId.getOaid())) {
                device.oaid(deviceId.getOaid()[0]);
                isValidDeviceId = true;
            }
            if (ArrayUtils.isEmpty(deviceId.getGaid())) {
                device.gaid(deviceId.getGaid()[0]);
                isValidDeviceId = true;
            }
            if (ArrayUtils.isEmpty(deviceId.getImei())) {
                device.imei(deviceId.getImei()[0]);
                isValidDeviceId = true;
            }
            if (!isValidDeviceId) {
                throw new PreBidException("getDeviceID: Imei, Oaid, Gaid are all empty.");
            }
            if (ArrayUtils.isEmpty(deviceId.getClientTime())) {
                device.clientTime(ClientTimeConverter.getClientTime(deviceId.getClientTime()[0]));
            }
        } else {
            if (tempDevice.getGaid() == null || tempDevice.getGaid().isEmpty()) {
                throw new PreBidException(
                        "getDeviceID: openRTBRequest.User.Ext is null and device.Gaid is not specified.");
            }
        }
    }

    private void getReqNetworkInfo(HuaweiAdsRequest.HuaweiAdsRequestBuilder request, BidRequest openRTBRequest) {
        Optional.ofNullable(openRTBRequest.getDevice())
                .ifPresent(device -> {
                    Network.NetworkBuilder network = Network.builder();
                    network.type(device.getConnectiontype() != null
                            ? device.getConnectiontype()
                            : HuaweiAdsConstants.DEFAULT_UNKNOWN_NETWORK_TYPE);

                    String[] arr = device.getMccmnc().split("-");
                    List<CellInfo> cellInfos = device.getMccmnc().isEmpty()
                            ? List.of()
                            : List.of(CellInfo.of(arr[0], arr[1]));

                    switch (arr[0] + arr[1]) {
                        case "46000", "46002", "46007" -> network.carrier(2);
                        case "46001", "46006" -> network.carrier(1);
                        case "46003", "46005", "46011" -> network.carrier(3);
                        default -> network.carrier(99);
                    }

                    network.cellInfo(cellInfos);
                    request.network(network.build());
                });
    }

    private void getReqRegsInfo(HuaweiAdsRequest.HuaweiAdsRequestBuilder request, BidRequest openRTBRequest) {
        if (openRTBRequest.getRegs() != null && openRTBRequest.getRegs().getCoppa() >= 0) {
            request.regs(Regs.of(openRTBRequest.getRegs().getCoppa()));
        }
    }

    private void getReqGeoInfo(HuaweiAdsRequest.HuaweiAdsRequestBuilder request, BidRequest openRTBRequest) {
        if (openRTBRequest.getDevice() != null && openRTBRequest.getDevice().getGeo() != null) {
            Geo geo = Geo.of(openRTBRequest.getDevice().getGeo().getLon(),
                    openRTBRequest.getDevice().getGeo().getLat(),
                    openRTBRequest.getDevice().getGeo().getAccuracy(),
                    openRTBRequest.getDevice().getGeo().getLastfix());

            request.geo(geo);
        }
    }

    private void getReqConsentInfo(HuaweiAdsRequest.HuaweiAdsRequestBuilder request, BidRequest openRtbRequest) {
        if (openRtbRequest.getUser() != null && openRtbRequest.getUser().getExt() != null) {
            ExtUser extUser = openRtbRequest.getUser().getExt();

            request.consent(extUser.getConsent());
        }
    }

    private MultiMap getHeaders(ExtImpHuaweiAds huaweiAdsImpExt, BidRequest request) {
        final MultiMap headers = HttpUtil.headers();

        if (huaweiAdsImpExt == null) {
            return headers;
        }

        final String authorization = getDigestAuthorization(huaweiAdsImpExt);

        headers.set(HttpUtil.AUTHORIZATION_HEADER, authorization);

        if (request.getDevice() != null
                && request.getDevice().getUa() != null
                && !request.getDevice().getUa().isEmpty()) {

            headers.set(HttpUtil.USER_AGENT_HEADER, request.getDevice().getUa());
        }

        return headers;
    }

    private String getDigestAuthorization(ExtImpHuaweiAds huaweiAdsImpExt) {
        final String nonce = String.valueOf(System.currentTimeMillis());

        final String apiKey = huaweiAdsImpExt.getPublisherId() + ":ppsadx/getResult:" + huaweiAdsImpExt.getSignKey();

        final String data = nonce + ":POST:/ppsadx/getResult";
        final String hmacSha256 = computeHmacSha256(data, apiKey);

        return "Digest "
                + "username=" + huaweiAdsImpExt.getPublisherId()
                + ",realm=ppsadx/getResult,"
                + "nonce=" + nonce
                + ",response=" + hmacSha256 + ","
                + "algorithm=HmacSHA256,usertype=1,keyid=" + huaweiAdsImpExt.getKeyId();
    }

    private String computeHmacSha256(String message, String signKey) {
        final String algorithm = "HmacSHA256";

        try {
            final Mac mac = Mac.getInstance(algorithm);
            final SecretKeySpec secretKeySpec = new SecretKeySpec(signKey.getBytes(StandardCharsets.UTF_8), algorithm);
            mac.init(secretKeySpec);

            final byte[] hmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private String bytesToHex(byte[] bytes) {
        final StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private String buildEndpoint(String countryCode) {
        if (countryCode == null || countryCode.length() > 2 || "1".equals(closeSiteSelectionByCountry)) {
            return endpointUrl;
        }

        // choose site
        if (ChineseSiteCountryCode.isContainsByName(countryCode)) {
            return HuaweiAdsConstants.CHINESE_SITE_ENDPOINT;
        } else if (RussianSiteCountryCode.isContainsByName(countryCode)) {
            return HuaweiAdsConstants.RUSSIAN_SITE_ENDPOINT;
        } else if (EuropeanSiteCountryCode.isContainsByName(countryCode)) {
            return HuaweiAdsConstants.EUROPEAN_SITE_ENDPOINT;
        } else {
            return HuaweiAdsConstants.ASIAN_SITE_ENDPOINT;
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        return null;
    }

}
