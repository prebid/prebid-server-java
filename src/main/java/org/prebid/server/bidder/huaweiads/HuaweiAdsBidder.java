package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Request;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.DataObject;
import com.iab.openrtb.response.ImageObject;
import com.iab.openrtb.response.Link;
import com.iab.openrtb.response.Response;
import com.iab.openrtb.response.TitleObject;
import com.iab.openrtb.response.VideoObject;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.huaweiads.model.request.AdSlot30;
import org.prebid.server.bidder.huaweiads.model.request.AdsType;
import org.prebid.server.bidder.huaweiads.model.request.App;
import org.prebid.server.bidder.huaweiads.model.request.CellInfo;
import org.prebid.server.bidder.huaweiads.model.request.ChineseSiteCountryCode;
import org.prebid.server.bidder.huaweiads.model.request.ClientTimeConverter;
import org.prebid.server.bidder.huaweiads.model.request.CountryCode;
import org.prebid.server.bidder.huaweiads.model.request.CreativeType;
import org.prebid.server.bidder.huaweiads.model.request.Device;
import org.prebid.server.bidder.huaweiads.model.request.EuropeanSiteCountryCode;
import org.prebid.server.bidder.huaweiads.model.request.Format;
import org.prebid.server.bidder.huaweiads.model.request.Geo;
import org.prebid.server.bidder.huaweiads.model.request.HuaweiAdsRequest;
import org.prebid.server.bidder.huaweiads.model.request.Network;
import org.prebid.server.bidder.huaweiads.model.request.PkgNameConvert;
import org.prebid.server.bidder.huaweiads.model.request.Regs;
import org.prebid.server.bidder.huaweiads.model.request.RussianSiteCountryCode;
import org.prebid.server.bidder.huaweiads.model.response.Ad30;
import org.prebid.server.bidder.huaweiads.model.response.Content;
import org.prebid.server.bidder.huaweiads.model.response.HuaweiAdsResponse;
import org.prebid.server.bidder.huaweiads.model.response.Icon;
import org.prebid.server.bidder.huaweiads.model.response.ImageInfo;
import org.prebid.server.bidder.huaweiads.model.response.InteractionType;
import org.prebid.server.bidder.huaweiads.model.response.MediaFile;
import org.prebid.server.bidder.huaweiads.model.response.MetaData;
import org.prebid.server.bidder.huaweiads.model.response.Monitor;
import org.prebid.server.bidder.huaweiads.model.response.VideoInfo;
import org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuaweiAds;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtUserDataDeviceIdHuaweiAds;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtUserDataHuaweiAds;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class HuaweiAdsBidder implements Bidder<HuaweiAdsRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpHuaweiAds>> HUAWEI_ADS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final JacksonMapper mapper;
    private final String endpointUrl;
    private final List<PkgNameConvert> packageNameConverter;
    private final String closeSiteSelectionByCountry;
    private final String chineseEndpoint;
    private final String russianEndpoint;
    private final String europeanEndpoint;
    private final String asianEndpoint;

    public HuaweiAdsBidder(String endpoint,
                           List<PkgNameConvert> packageNameConverter,
                           String closeSiteSelectionByCountry,
                           String chineseEndpoint,
                           String russianEndpoint,
                           String europeanEndpoint,
                           String asianEndpoint,
                           JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(endpoint);
        this.packageNameConverter = Objects.requireNonNull(packageNameConverter);
        this.closeSiteSelectionByCountry = Objects.requireNonNull(closeSiteSelectionByCountry);
        this.chineseEndpoint = Objects.requireNonNull(chineseEndpoint);
        this.russianEndpoint = Objects.requireNonNull(russianEndpoint);
        this.europeanEndpoint = Objects.requireNonNull(europeanEndpoint);
        this.asianEndpoint = Objects.requireNonNull(asianEndpoint);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<HuaweiAdsRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final HuaweiAdsRequest.HuaweiAdsRequestBuilder huaweiAdsRequestBuilder = HuaweiAdsRequest.builder();
        final List<AdSlot30> multislot = new ArrayList<>();

        ExtImpHuaweiAds extImpHuaweiAds = null;
        for (final Imp imp : bidRequest.getImp()) {
            try {
                extImpHuaweiAds = parseImpExt(imp);
                validateExtImpHuaweiAds(extImpHuaweiAds);
                final AdSlot30 adSlot30 = getReqAdslot30(extImpHuaweiAds, imp);
                multislot.add(adSlot30);
            } catch (IllegalArgumentException | PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            } catch (InvalidRequestException e) {
                return Result.withError(BidderError.badServerResponse(e.getMessage()));
            }
        }

        huaweiAdsRequestBuilder
                .multislot(multislot)
                .clientAdRequestId(bidRequest.getId());

        HuaweiAdsRequest huaweiAdsRequest;
        try {
            huaweiAdsRequest = getHuaweiAdsRequest(huaweiAdsRequestBuilder.build(), bidRequest);
        } catch (IllegalArgumentException | PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final String countryCode = huaweiAdsRequest.getApp().getCountry();

        return Result.withValue(
                HttpRequest.<HuaweiAdsRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(buildEndpoint(countryCode))
                        .headers(getHeaders(extImpHuaweiAds, bidRequest))
                        .body(mapper.encodeToBytes(huaweiAdsRequest))
                        .payload(huaweiAdsRequest)
                        .build());
    }

    private void validateExtImpHuaweiAds(ExtImpHuaweiAds huaweiAdsImpExt) {
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
    }

    private ExtImpHuaweiAds parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), HUAWEI_ADS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Unmarshalling error: " + e.getMessage());
        }
    }

    private AdSlot30 getReqAdslot30(ExtImpHuaweiAds huaweiAdsImpExt, Imp imp) {
        final int adtype = AdsType.fromString(huaweiAdsImpExt.getAdtype().toLowerCase()).getCode();
        final int testStatus = huaweiAdsImpExt.getIsTestAuthorization().equals("true") ? 1 : 0;

        final AdSlot30.AdSlot30Builder adSlot30Builder = AdSlot30.builder()
                .slotid(huaweiAdsImpExt.getSlotId())
                .adtype(adtype)
                .test(testStatus);

        return getOpenrtbFormat(adSlot30Builder.build(), adtype, huaweiAdsImpExt.getAdtype(), imp);
    }

    private AdSlot30 getOpenrtbFormat(AdSlot30 adslot30, int adtype, String impExtAdType, Imp imp) {
        final AdsType adsType = AdsType.fromAdsTypeCode(adtype);
        if (imp.getBanner() != null) {
            if (adsType != AdsType.BANNER && adsType != AdsType.INTERSTITIAL) {
                throw new PreBidException(
                        "check openrtb format: request has banner, doesn't correspond to huawei adtype "
                                + impExtAdType);
            }
            return getBannerFormat(adslot30, imp);
        } else if (imp.getXNative() != null) {
            if (adsType != AdsType.XNATIVE) {
                throw new PreBidException(
                        "check openrtb format: request has native, doesn't correspond to huawei adtype "
                                + impExtAdType);
            }
            return getNativeFormat(adslot30, imp);
        } else if (imp.getVideo() != null) {
            if (adsType != AdsType.BANNER
                    && adsType != AdsType.INTERSTITIAL
                    && adsType != AdsType.REWARDED
                    && adsType != AdsType.ROLL) {
                throw new PreBidException(
                        "check openrtb format: request has video, doesn't correspond to huawei adtype "
                                + impExtAdType);
            }
            return getVideoFormat(adslot30, adsType, imp);
        } else if (imp.getAudio() != null) {
            throw new PreBidException("check openrtb format: request has audio, not currently supported");
        } else {
            throw new PreBidException(
                    "check openrtb format: please choose one of our supported type banner, native, or video");
        }
    }

    private AdSlot30 getBannerFormat(AdSlot30 adslot30, Imp imp) {
        final AdSlot30.AdSlot30Builder adSlot30Builder = adslot30.toBuilder();
        if (imp.getBanner().getW() != null && imp.getBanner().getH() != null) {
            adSlot30Builder
                    .w(imp.getBanner().getW())
                    .h(imp.getBanner().getH());
        }

        if (CollectionUtils.isNotEmpty(imp.getBanner().getFormat())) {
            final List<Format> formats = imp.getBanner().getFormat()
                    .stream()
                    .filter(format -> format.getH() != 0 && format.getW() != 0)
                    .map(format -> Format.of(format.getW(), format.getH()))
                    .collect(Collectors.toList());

            adSlot30Builder.format(formats);
        }

        return adSlot30Builder.build();
    }

    private AdSlot30 getNativeFormat(AdSlot30 adslot30, Imp imp) {
        final AdSlot30.AdSlot30Builder adSlot30Builder = adslot30.toBuilder();

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

        adSlot30Builder.w(width).h(height);

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

        return adSlot30Builder
                .detailedCreativeTypeList(detailedCreativeTypeList)
                .build();
    }

    private AdSlot30 getVideoFormat(AdSlot30 adslot30, AdsType adsType, Imp openRTBImp) {
        final AdSlot30.AdSlot30Builder adSlot30Builder = adslot30.toBuilder()
                .w(openRTBImp.getVideo().getW())
                .h(openRTBImp.getVideo().getH());

        if (adsType == AdsType.ROLL) {
            if (openRTBImp.getVideo().getMaxduration() == null || openRTBImp.getVideo().getMaxduration() == 0) {
                throw new PreBidException(
                        "Extract openrtb video failed: MaxDuration is empty when huaweiads adtype is roll.");
            }
            adSlot30Builder.totalDuration(openRTBImp.getVideo().getMaxduration());
        }
        return adSlot30Builder.build();
    }

    private HuaweiAdsRequest getHuaweiAdsRequest(HuaweiAdsRequest request, BidRequest openRTBRequest) {
        return request.toBuilder()
                .version(HuaweiAdsConstants.API_VERSION)
                .app(getAppInfo(openRTBRequest))
                .device(getDeviceInfo(openRTBRequest))
                .network(getNetworkInfo(openRTBRequest))
                .regs(getRegsInfo(openRTBRequest))
                .geo(getGeoInfo(openRTBRequest))
                .consent(getConsentInfo(openRTBRequest))
                .build();
    }

    private App getAppInfo(BidRequest openRTBRequest) {
        final App.AppBuilder appBuilder = App.builder();
        final com.iab.openrtb.request.App app = openRTBRequest.getApp();
        if (app != null) {
            final String version = app.getVer();
            if (StringUtils.isNotEmpty(version)) {
                appBuilder.version(version);
            }
            final String name = app.getName();
            if (StringUtils.isNotEmpty(name)) {
                appBuilder.name(name);
            }

            final String bundle = app.getBundle();
            // bundle cannot be empty, we need package name.
            if (StringUtils.isNotEmpty(bundle)) {
                appBuilder.pkgname(getFinalPackageName(bundle));
            } else {
                throw new PreBidException("generate HuaweiAds AppInfo failed: openrtb BidRequest.App.Bundle is empty.");
            }

            final com.iab.openrtb.request.Content content = app.getContent();
            if (content != null
                    && StringUtils.isNotEmpty(content.getLanguage())) {

                appBuilder.lang(content.getLanguage());
            } else {
                appBuilder.lang("en");
            }
        }
        final String countryCode = getCountryCode(openRTBRequest);

        return appBuilder.country(countryCode).build();
    }

    private String getFinalPackageName(String bundleName) {
        for (PkgNameConvert convert : packageNameConverter) {
            final String convertedPkgName = convert.getConvertedPkgName();
            if (StringUtils.isNotEmpty(convertedPkgName)) {
                continue;
            }

            for (String name : convert.getExceptionPkgNames()) {
                if (name.equals(bundleName)) {
                    return bundleName;
                }
            }

            for (String name : convert.getUnconvertedPkgNames()) {
                if (name.equals(bundleName) || name.equals("*")) {
                    return convertedPkgName;
                }
            }

            for (String keyword : convert.getUnconvertedPkgNameKeyWords()) {
                if (bundleName.indexOf(keyword) > 0) {
                    return convertedPkgName;
                }
            }

            for (String prefix : convert.getUnconvertedPkgNamePrefixs()) {
                if (bundleName.startsWith(prefix)) {
                    return convertedPkgName;
                }
            }
        }

        return bundleName;
    }

    private Device getDeviceInfo(BidRequest openRTBRequest) {
        final Device.DeviceBuilder deviceBuilder = Device.builder();
        final com.iab.openrtb.request.Device deviceData = openRTBRequest.getDevice();

        if (deviceData != null) {
            String countryCode = getCountryCode(openRTBRequest);
            deviceBuilder.type(deviceData.getDevicetype())
                    .useragent(deviceData.getUa())
                    .os(deviceData.getOs())
                    .version(deviceData.getOsv())
                    .maker(deviceData.getMake())
                    .model(StringUtils.isEmpty(deviceData.getModel())
                            ? HuaweiAdsConstants.DEFAULT_MODEL_NAME : deviceData.getModel())
                    .height(deviceData.getH())
                    .width(deviceData.getW())
                    .language(deviceData.getLanguage())
                    .pxratio(deviceData.getPxratio())
                    .belongCountry(countryCode)
                    .localeCountry(countryCode)
                    .ip(deviceData.getIp())
                    .gaid(deviceData.getIfa());
        }

        return modifyDeviceWithUserExt(deviceBuilder.build(), openRTBRequest);
    }

    private String getCountryCode(BidRequest openRTBRequest) {
        final com.iab.openrtb.request.Device device = openRTBRequest.getDevice();
        final com.iab.openrtb.request.User user = openRTBRequest.getUser();
        if (device != null
                && device.getGeo() != null
                && StringUtils.isNotEmpty(device.getGeo().getCountry())) {
            return CountryCode.convertCountryCode(openRTBRequest.getDevice().getGeo().getCountry());
        } else if (user != null
                && user.getGeo() != null
                && StringUtils.isNotEmpty(user.getGeo().getCountry())) {
            return CountryCode.convertCountryCode(user.getGeo().getCountry());
        } else if (device != null
                && StringUtils.isNotEmpty(device.getMccmnc())) {
            return CountryCode.getCountryCodeFromMCC(device.getMccmnc()).toString().toUpperCase();
        } else {
            return HuaweiAdsConstants.DEFAULT_COUNTRY_NAME;
        }
    }

    private Device modifyDeviceWithUserExt(Device device, BidRequest bidRequest) {

        final Device.DeviceBuilder deviceBuilder = device.toBuilder();
        final User user = bidRequest.getUser();

        if (user != null && user.getExt() != null) {
            final ExtUserDataHuaweiAds extUserDataHuaweiAds = mapper.mapper()
                    .convertValue(user.getExt(), ExtUserDataHuaweiAds.class);

            final ExtUserDataDeviceIdHuaweiAds deviceId = extUserDataHuaweiAds.getData();
            final List<String> oaid = deviceId.getOaid();
            final List<String> gaid = deviceId.getGaid();
            final List<String> imei = deviceId.getImei();

            boolean isValidDeviceId = false;
            if (CollectionUtils.isNotEmpty(oaid)) {
                deviceBuilder.oaid(oaid.get(0));
                isValidDeviceId = true;
            }
            if (CollectionUtils.isNotEmpty(gaid)) {
                deviceBuilder.gaid(gaid.get(0));
                isValidDeviceId = true;
            }
            if (CollectionUtils.isNotEmpty(imei)) {
                deviceBuilder.imei(imei.get(0));
                isValidDeviceId = true;
            }
            if (!isValidDeviceId) {
                throw new PreBidException("getDeviceID: Imei, Oaid, Gaid are all empty.");
            }
            if (CollectionUtils.isNotEmpty(deviceId.getClientTime())) {
                deviceBuilder.clientTime(ClientTimeConverter.getClientTime(deviceId.getClientTime().get(0)));
            }

            final com.iab.openrtb.request.Device bidRequestDevice = bidRequest.getDevice();
            if (bidRequestDevice != null && bidRequestDevice.getDnt() != null) {
                if (CollectionUtils.isNotEmpty(oaid)) {
                    deviceBuilder.isTrackingEnabled(bidRequestDevice.getDnt() == 1 ? "0" : "1");
                }
                if (CollectionUtils.isNotEmpty(gaid)) {
                    deviceBuilder.gaidTrackingEnabled(bidRequestDevice.getDnt() == 1 ? "0" : "1");
                }
            }
        } else {
            if (StringUtils.isEmpty(device.getGaid())) {
                throw new PreBidException(
                        "getDeviceID: openRTBRequest.User.Ext is null and device.Gaid is not specified.");
            }
        }

        return deviceBuilder.build();
    }

    private Network getNetworkInfo(BidRequest openRTBRequest) {
        final com.iab.openrtb.request.Device device = openRTBRequest.getDevice();
        if (device != null) {
            final Network.NetworkBuilder network = Network.builder();
            if (device.getConnectiontype() != null) {
                network.type(device.getConnectiontype());
            } else {
                network.type(HuaweiAdsConstants.DEFAULT_UNKNOWN_NETWORK_TYPE);
            }

            final List<CellInfo> cellInfos = new ArrayList<>();
            if (StringUtils.isNotEmpty(device.getMccmnc())) {
                final String[] arr = device.getMccmnc().split("-");
                network.carrier(0);
                if (arr.length >= 2) {
                    cellInfos.add(CellInfo.of(arr[0], arr[1]));
                    final String str = arr[0] + arr[1];
                    switch (str) {
                        case "46000", "46002", "46007" -> network.carrier(2);
                        case "46001", "46006" -> network.carrier(1);
                        case "46003", "46005", "46011" -> network.carrier(3);
                        default -> network.carrier(99);
                    }
                }
            }
            return network.cellInfo(cellInfos).build();
        }
        return null;
    }

    private Regs getRegsInfo(BidRequest openRTBRequest) {
        final com.iab.openrtb.request.Regs regs = openRTBRequest.getRegs();
        if (regs != null && regs.getCoppa() >= 0) {
            return Regs.of(regs.getCoppa());
        }
        return null;
    }

    private Geo getGeoInfo(BidRequest openRTBRequest) {
        final com.iab.openrtb.request.Device device = openRTBRequest.getDevice();
        if (device != null && device.getGeo() != null) {
            final com.iab.openrtb.request.Geo geo = device.getGeo();

            final Float lon = geo.getLon();
            final Float lat = geo.getLat();

            return Geo.of(lon != null ? BigDecimal.valueOf(lon) : null,
                    lat != null ? BigDecimal.valueOf(lat) : null,
                    geo.getAccuracy(),
                    geo.getLastfix());
        }
        return null;
    }

    private String getConsentInfo(BidRequest openRtbRequest) {
        final User user = openRtbRequest.getUser();
        if (user != null && user.getExt() != null) {
            final ExtUser extUser = user.getExt();

            return extUser.getConsent();
        }
        return null;
    }

    private MultiMap getHeaders(ExtImpHuaweiAds huaweiAdsImpExt, BidRequest request) {
        final MultiMap headers = HttpUtil.headers();

        if (huaweiAdsImpExt == null) {
            return headers;
        }

        final String authorization = getDigestAuthorization(huaweiAdsImpExt);

        headers.set(HttpUtil.AUTHORIZATION_HEADER, authorization);

        Optional.ofNullable(request.getDevice())
                .map(com.iab.openrtb.request.Device::getUa)
                .filter(StringUtils::isNotEmpty)
                .ifPresent(ua -> headers.set(HttpUtil.USER_AGENT_HEADER, ua));

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
            final SecretKeySpec secretKeySpec = new SecretKeySpec(signKey.getBytes(StandardCharsets.UTF_8), algorithm);
            final Mac mac = Mac.getInstance(algorithm);
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
            return chineseEndpoint;
        } else if (RussianSiteCountryCode.isContainsByName(countryCode)) {
            return russianEndpoint;
        } else if (EuropeanSiteCountryCode.isContainsByName(countryCode)) {
            return europeanEndpoint;
        } else {
            return asianEndpoint;
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<HuaweiAdsRequest> httpCall, BidRequest bidRequest) {
        try {
            final HuaweiAdsResponse huaweiAdsResponse = parseBidResponse(httpCall.getResponse());
            checkHuaweiAdsResponseRetcode(huaweiAdsResponse);

            final List<BidderBid> bidderBids = convertHuaweiAdsResponse(huaweiAdsResponse, bidRequest);

            return Result.withValues(bidderBids);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private HuaweiAdsResponse parseBidResponse(HttpResponse response) {
        try {
            return mapper.decodeValue(response.getBody(), HuaweiAdsResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException("Unable to parse server response");
        }
    }

    private void checkHuaweiAdsResponseRetcode(HuaweiAdsResponse response) {
        if (response != null) {
            final Integer retcode = response.getRetcode();
            if (retcode == 200 || retcode == 204 || retcode == 206) {
                return;
            }
            if ((retcode < 600 && retcode >= 400) || (retcode < 300 && retcode > 200)) {
                throw new PreBidException(String.format("HuaweiAdsResponse retcode: %d, reason: %s",
                        retcode, response.getReason()));
            }
        }

    }

    private List<BidderBid> convertHuaweiAdsResponse(HuaweiAdsResponse huaweiAdsResponse, BidRequest bidRequest) {
        if (huaweiAdsResponse == null) {
            return Collections.emptyList();
        } else if (huaweiAdsResponse.getMultiad() == null || huaweiAdsResponse.getMultiad().size() == 0) {
            throw new PreBidException("convert huaweiads response to bidder response failed: multiad length is 0, "
                    + "get no ads from huawei side.");
        }

        final List<BidderBid> bidderBids = new ArrayList<>();

        final BidResponse.BidResponseBuilder bidResponse = BidResponse
                .builder()
                .cur(HuaweiAdsConstants.DEFAULT_CURRENCY);

        final Map<String, Imp> mapSlotIdImp = new HashMap<>();
        final Map<String, BidType> mapSlotIdMediaType = new HashMap<>();
        final List<Imp> impList = bidRequest.getImp();

        if (impList == null || impList.size() < 1) {
            throw new PreBidException(
                    "convert huaweiads response to bidder response failed: openRTBRequest.imp is null");
        }

        for (Imp imp : impList) {

            final ExtImpHuaweiAds huaweiAdsExt = parseImpExt(imp);
            validateExtImpHuaweiAds(huaweiAdsExt);
            mapSlotIdImp.put(huaweiAdsExt.getSlotId(), imp);

            BidType mediaType = BidType.banner;
            if (imp.getVideo() != null) {
                mediaType = BidType.video;
            } else if (imp.getXNative() != null) {
                mediaType = BidType.xNative;
            } else if (imp.getAudio() != null) {
                mediaType = BidType.audio;
            }
            mapSlotIdMediaType.put(huaweiAdsExt.getSlotId(), mediaType);
        }

        for (Ad30 ad30 : huaweiAdsResponse.getMultiad()) {
            final Imp imp = mapSlotIdImp.get(ad30.getSlotId());
            if (imp.getId() == null || imp.getId().equals("")) {
                continue;
            }
            final String impId = imp.getId();

            if (ad30.getRetCode30() != 200) {
                continue;
            }

            for (Content content : ad30.getContentList()) {
                Bid.BidBuilder bidBuilder = Bid.builder()
                        .id(impId)
                        .impid(impId)
                        .price(content.getPrice() != null ? BigDecimal.valueOf(content.getPrice()) : null)
                        .crid(content.getContentId());

                if (content.getCur() != null && !content.getCur().equals("")) {
                    bidResponse.cur(content.getCur());
                }

                bidBuilder = handleHuaweiAdsContent(ad30.getAdType(),
                        content,
                        mapSlotIdMediaType.get(ad30.getSlotId()),
                        imp,
                        bidBuilder.build())
                        .toBuilder();

                bidBuilder
                        .adomain(List.of("huaweiads"))
                        .nurl(getNrl(content));

                final Bid bid = bidBuilder.build();
                bidderBids.add(BidderBid.of(bid,
                        mapSlotIdMediaType.get(ad30.getSlotId()),
                        bidResponse.build().getCur()));
            }
        }
        return bidderBids;
    }

    private Bid handleHuaweiAdsContent(int adType, Content content, BidType bidType, Imp imp, Bid bid) {
        AdsType adsType = AdsType.fromAdsTypeCode(adType);
        switch (bidType) {
            case banner -> {
                return addAdmBanner(adsType, content, bidType, imp, bid);
            }
            case xNative -> {
                return addAdmNative(adsType, content, bidType, imp, bid);
            }
            case video -> {
                return addAdmVideo(adsType, content, bidType, imp, bid);
            }
            default -> throw new PreBidException("no support bidtype: audio");
        }
    }

    private Bid addAdmBanner(AdsType adsType, Content content, BidType bidType, Imp imp, Bid bid) {
        if (adsType != AdsType.BANNER && adsType != AdsType.INTERSTITIAL) {
            throw new PreBidException("openrtb banner should correspond to huaweiads adtype: banner or interstitial");
        }

        int creativeTypeCode = content.getCreativeType();
        if (creativeTypeCode > 100) {
            creativeTypeCode = creativeTypeCode - 100;
        }

        CreativeType creativeType = CreativeType.fromCreativeTypeCode(creativeTypeCode);

        if (creativeType == CreativeType.TEXT
                || creativeType == CreativeType.BIG_PICTURE
                || creativeType == CreativeType.BIG_PICTURE_2
                || creativeType == CreativeType.SMALL_PICTURE
                || creativeType == CreativeType.THREE_SMALL_PICTURES_TEXT
                || creativeType == CreativeType.ICON_TEXT
                || creativeType == CreativeType.GIF) {
            return addAdmPicture(content, bid);
        } else if (creativeType == CreativeType.VIDEO_TEXT
                || creativeType == CreativeType.VIDEO
                || creativeType == CreativeType.VIDEO_WITH_PICTURES_TEXT) {
            return addAdmVideo(adsType, content, bidType, imp, bid);
        } else {
            throw new PreBidException("no banner support creativetype");
        }
    }

    private Bid addAdmPicture(Content content, Bid bid) {
        final Bid.BidBuilder bidBuilder = bid.toBuilder();
        if (content == null) {
            throw new PreBidException("extract Adm failed: content is empty");
        }

        final String clickUrl = getClickUrl(content);

        String imageInfoUrl;
        int adHeight;
        int adWidth;

        final MetaData metaData = content.getMetaData();
        final List<ImageInfo> imageInfos = metaData.getImageInfoList();
        if (imageInfos != null) {
            imageInfoUrl = imageInfos.get(0).getUrl();
            adHeight = imageInfos.get(0).getHeight();
            adWidth = imageInfos.get(0).getWidth();

            bidBuilder.h(adHeight).w(adWidth);
        } else {
            throw new PreBidException("content.MetaData.ImageInfo is empty");
        }

        final String imageTitle = getDecodeValue(metaData.getTitle());

        final List<String> dspImpTrackings = new ArrayList<>();
        final StringBuilder dspClickTrackings = new StringBuilder();
        extractDspImpClickTrackings(content, dspImpTrackings, dspClickTrackings);

        final String dspImpTrackings2StrImg = dspImpTrackings.stream()
                .map(tracking -> "<img height=\"1\" width=\"1\" src='" + tracking + "' > ")
                .collect(Collectors.joining());

        final String adm = "<style> html, body  "
                + "{ margin: 0; padding: 0; width: 100%; height: 100%; vertical-align: middle; }  "
                + "html  "
                + "{ display: table; }  "
                + "body { display: table-cell; vertical-align: middle;"
                + " text-align: center; -webkit-text-size-adjust: none; }  "
                + "</style> "
                + "<span class=\"title-link advertiser_label\">" + imageTitle + "</span> "
                + "<a href='" + clickUrl + "' style=\"text-decoration:none\" onclick=sendGetReq()> "
                + "<img src='" + imageInfoUrl + "' width='" + adWidth + "' height='" + adHeight + "'/> "
                + "</a> "
                + dspImpTrackings2StrImg
                + "<script type=\"text/javascript\">"
                + "var dspClickTrackings = [" + String.join(",", dspClickTrackings) + "];"
                + "function sendGetReq() {"
                + "sendSomeGetReq(dspClickTrackings)"
                + "}"
                + "function sendOneGetReq(url) {"
                + "var req = new XMLHttpRequest();"
                + "req.open('GET', url, true);"
                + "req.send(null);"
                + "}"
                + "function sendSomeGetReq(urls) {for (var i = 0; i < urls.length; i++) {sendOneGetReq(urls[i]);}}"
                + "</script>";

        return bidBuilder.adm(adm).build();
    }

    private String getClickUrl(Content content) {
        String clickUrl = "";
        final MetaData metaData = content.getMetaData();

        if (Objects.equals(InteractionType.APP_PROMOTION.getCode(), content.getInteractionType())) {
            if (metaData != null && StringUtils.isNotEmpty(metaData.getIntent())) {
                clickUrl = getDecodeValue(metaData.getIntent());
            } else {
                throw new PreBidException(
                        "content.MetaData.Intent in huaweiads resopnse is empty when interactiontype is appPromotion");
            }
        } else {
            if (metaData != null && StringUtils.isNotEmpty(metaData.getIntent())) {
                clickUrl = getDecodeValue(metaData.getIntent());
            } else if (metaData != null && StringUtils.isNotEmpty(metaData.getClickUrl())) {
                clickUrl = metaData.getClickUrl();
            }
        }

        return clickUrl;
    }

    private String getDecodeValue(String str) {
        if (StringUtils.isEmpty(str)) {
            return "";
        }

        return URLDecoder.decode(str, StandardCharsets.UTF_8);
    }

    private void extractDspImpClickTrackings(Content content,
                                             List<String> dspImpTrackings,
                                             StringBuilder dspClickTrackings) {
        for (Monitor monitor : content.getMonitorList()) {
            if (CollectionUtils.isNotEmpty(monitor.getUrl())) {
                switch (monitor.getEventType()) {
                    case "imp" -> dspImpTrackings.addAll(monitor.getUrl());
                    case "click" -> dspClickTrackings.append(String.join(",", monitor.getUrl()));
                }
            }
        }
    }

    private Bid addAdmVideo(AdsType adsType, Content content, BidType bidType, Imp imp, Bid bid) {
        if (content == null) {
            throw new PreBidException("extract Adm for video failed: content is empty");
        }
        final Bid.BidBuilder bidBuilder = bid.toBuilder();
        final String clickUrl = getClickUrl(content);

        String mime = "video/mp4";
        String resourceUrl = null;
        String duration = null;

        Integer adWidth = 0;
        Integer adHeight = 0;

        final MetaData metaData = content.getMetaData();
        if (metaData != null && metaData.getMediaFile() != null && adsType == AdsType.ROLL) {
            final MediaFile mediaFile = metaData.getMediaFile();
            // roll ad get information from mediafile
            if (StringUtils.isNotEmpty(mediaFile.getMime())) {
                mime = mediaFile.getMime();
            }
            adWidth = mediaFile.getWidth();
            bidBuilder.w(mediaFile.getWidth());
            adHeight = mediaFile.getHeight();
            bidBuilder.h(mediaFile.getHeight());
            if (StringUtils.isNotEmpty(mediaFile.getUrl())) {
                resourceUrl = mediaFile.getUrl();
            } else {
                throw new PreBidException("extract Adm for video failed: Content.MetaData.MediaFile.Url is empty");
            }
            duration = convertDuration(metaData.getDuration());
        } else if (metaData != null && metaData.getVideoInfo() != null) {
            final VideoInfo videoInfo = metaData.getVideoInfo();
            if (StringUtils.isNotEmpty(videoInfo.getVideoDownloadUrl())) {
                resourceUrl = videoInfo.getVideoDownloadUrl();
            } else {
                throw new PreBidException(
                        "extract Adm for video failed: content.MetaData.VideoInfo.VideoDownloadUrl is empty");
            }
            if (videoInfo.getWidth() != 0 && videoInfo.getHeight() != 0) {
                adWidth = videoInfo.getWidth();
                adHeight = videoInfo.getHeight();
                bidBuilder
                        .w(metaData.getMediaFile().getWidth())
                        .h(metaData.getMediaFile().getHeight());
            } else if (bidType == BidType.video && imp.getVideo() != null
                    && imp.getVideo().getW() != 0 && imp.getVideo().getH() != 0) {
                final Video video = imp.getVideo();
                adWidth = video.getW();
                adHeight = video.getH();
                bidBuilder.w(video.getW()).h(video.getH());
            } else {
                throw new PreBidException("extract Adm for video failed: cannot get video width, height");
            }
            duration = convertDuration(metaData.getVideoInfo().getVideoDuration());
        }

        final String adTitle = getDecodeValue(metaData.getTitle());
        final String adId = content.getContentId();
        final String creativeId = content.getContentId();
        final StringBuilder trackingEvents = new StringBuilder();
        final StringBuilder dspImpTracking2Str = new StringBuilder();
        final StringBuilder dspClickTracking2Str = new StringBuilder();
        final StringBuilder errorTracking2Str = new StringBuilder();

        for (Monitor monitor : content.getMonitorList()) {
            if (CollectionUtils.isEmpty(monitor.getUrl())) {
                continue;
            }
            handleEvent(dspImpTracking2Str, dspClickTracking2Str, errorTracking2Str, trackingEvents, monitor);
        }

        String rewardedVideoPart = "";
        boolean isAddRewardedVideoPart = true;

        if (adsType == AdsType.REWARDED) {
            String staticImageUrl = "";
            String staticImageHeight = "";
            String staticImageWidth = "";

            if (metaData.getIconList() != null
                    && CollectionUtils.isNotEmpty(metaData.getIconList())
                    && StringUtils.isNotEmpty(metaData.getIconList().get(0).getUrl())) {
                final List<Icon> iconList = metaData.getIconList();
                staticImageUrl = iconList.get(0).getUrl();

                if (iconList.get(0).getHeight() > 0 && iconList.get(0).getWidth() > 0) {
                    staticImageHeight = String.valueOf(iconList.get(0).getHeight());
                    staticImageWidth = String.valueOf(iconList.get(0).getWidth());
                } else {
                    staticImageHeight = String.valueOf(adHeight);
                    staticImageWidth = String.valueOf(adWidth);
                }
            } else if (CollectionUtils.isNotEmpty(metaData.getImageInfoList())
                    && StringUtils.isNotEmpty(metaData.getImageInfoList().get(0).getUrl())) {
                final List<ImageInfo> imageInfos = metaData.getImageInfoList();
                staticImageUrl = imageInfos.get(0).getUrl();

                if (imageInfos.get(0).getHeight() > 0
                        && imageInfos.get(0).getWidth() > 0) {
                    staticImageHeight = String.valueOf(imageInfos.get(0).getHeight());
                    staticImageWidth = String.valueOf(imageInfos.get(0).getWidth());
                } else {
                    staticImageHeight = String.valueOf(adHeight);
                    staticImageWidth = String.valueOf(adWidth);
                }
            } else {
                isAddRewardedVideoPart = false;
            }

            if (isAddRewardedVideoPart) {
                rewardedVideoPart = buildRewardedVideoPart(clickUrl,
                        adId,
                        creativeId,
                        staticImageUrl,
                        staticImageHeight,
                        staticImageWidth
                );
            }
        }

        final String adm = buildAdm(clickUrl, mime, resourceUrl, duration, adWidth, adHeight, adTitle,
                adId, creativeId, trackingEvents, dspImpTracking2Str, dspClickTracking2Str,
                errorTracking2Str, rewardedVideoPart);

        return bidBuilder.adm(adm).build();
    }

    private static String buildRewardedVideoPart(String clickUrl, String adId, String creativeId, String staticImageUrl,
                                                 String staticImageHeight, String staticImageWidth) {
        return "<Creative adId=\"" + adId + "\" id=\"" + creativeId + "\">"
                + "<CompanionAds>"
                + "<Companion width=\"" + staticImageWidth + "\" height=\"" + staticImageHeight + "\">"
                + "<StaticResource creativeType=\"" + "image/png"
                + "\"><![CDATA[" + staticImageUrl + "]]></StaticResource>"
                + "<CompanionClickThrough><![CDATA[" + clickUrl + "]]></CompanionClickThrough>"
                + "</Companion>"
                + "</CompanionAds>"
                + "</Creative>";
    }

    private static String buildAdm(String clickUrl, String mime, String resourceUrl, String duration, Integer adWidth,
                                   Integer adHeight, String adTitle, String adId, String creativeId,
                                   StringBuilder trackingEvents, StringBuilder dspImpTracking2Str,
                                   StringBuilder dspClickTracking2Str, StringBuilder errorTracking2Str,
                                   String rewardedVideoPart) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<VAST version=\"3.0\">"
                + "<Ad id=\"" + adId + "\"><InLine>"
                + "<AdSystem>HuaweiAds</AdSystem>"
                + "<AdTitle>" + adTitle + "</AdTitle>"
                + errorTracking2Str + dspImpTracking2Str
                + "<Creatives>"
                + "<Creative adId=\"" + adId + "\" id=\"" + creativeId + "\">"
                + "<Linear>"
                + "<Duration>" + duration + "</Duration>"
                + "<TrackingEvents>" + trackingEvents + "</TrackingEvents>"
                + "<VideoClicks>"
                + "<ClickThrough><![CDATA[" + clickUrl + "]]></ClickThrough>"
                + dspClickTracking2Str
                + "</VideoClicks>"
                + "<MediaFiles>"
                + "<MediaFile delivery=\"progressive\" type=\"" + mime + "\" width=\"" + adWidth + "\" "
                + "height=\"" + adHeight + "\" scalable=\"true\" maintainAspectRatio=\"true\"> "
                + "<![CDATA[" + resourceUrl + "]]>"
                + "</MediaFile>"
                + "</MediaFiles>"
                + "</Linear>"
                + "</Creative>" + rewardedVideoPart
                + "</Creatives>"
                + "</InLine></Ad></VAST>";
    }

    private void handleEvent(StringBuilder dspImpTracking2Str,
                             StringBuilder dspClickTracking2Str,
                             StringBuilder errorTracking2Str,
                             StringBuilder trackingEvents,
                             Monitor monitor) {
        String event = "";
        switch (monitor.getEventType()) {
            case "vastError":
                errorTracking2Str.append(
                        getVastImpClickErrorTrackingUrls(monitor.getUrl(), "vastError"));
                break;
            case "imp":
                dspImpTracking2Str.append(
                        getVastImpClickErrorTrackingUrls(monitor.getUrl(), "imp"));
                break;
            case "click":
                dspClickTracking2Str.append(
                        getVastImpClickErrorTrackingUrls(monitor.getUrl(), "click"));
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
            default:
                break;
        }

        if (StringUtils.isNotEmpty(event) && !event.equals("skip&closeLinear")) {
            trackingEvents.append(getVastEventTrackingUrls(monitor.getUrl(), event));
        } else if (StringUtils.isNotEmpty(event)) {
            trackingEvents.append(getVastEventTrackingUrls(monitor.getUrl(), "skip&closeLinear"));
        }
    }

    private String convertDuration(Long duration) {
        final Duration dur = Duration.ofMillis(duration);
        final LocalTime time = LocalTime.MIDNIGHT.plus(dur);
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
        return time.format(formatter);
    }

    public String getVastImpClickErrorTrackingUrls(List<String> urls, String eventType) {
        return urls.stream()
                .map(url -> switch (eventType) {
                    case "click" -> "<ClickTracking><![CDATA[" + url + "]]></ClickTracking>";
                    case "imp" -> "<Impression><![CDATA[" + url + "]]></Impression>";
                    case "vastError" -> "<Error><![CDATA[" + url + "&et=[ERRORCODE]]]></Error>";
                    default -> "";
                })
                .collect(Collectors.joining());
    }

    public String getVastEventTrackingUrls(List<String> urls, String eventType) {
        return urls.stream()
                .map(eventUrl -> {
                    if (eventType.equals("skip&closeLinear")) {
                        return "<Tracking event=\"skip\"><![CDATA["
                                + eventUrl + "]]></Tracking><Tracking event=\"closeLinear\"><![CDATA["
                                + eventUrl + "]]></Tracking>";
                    } else {
                        return "<Tracking event=\"" + eventType + "\"><![CDATA[" + eventUrl + "]]></Tracking>";
                    }
                })
                .collect(Collectors.joining());
    }

    public Bid addAdmNative(AdsType adsType, Content content, BidType bidType, Imp imp, Bid bid) {
        final Bid.BidBuilder bidBuilder = bid.toBuilder();

        if (adsType != AdsType.XNATIVE) {
            throw new PreBidException("extract Adm for Native ad: huaweiads response is not a native ad");
        }
        if (imp.getXNative() == null) {
            throw new PreBidException("extract Adm for Native ad: imp.Native is null");
        }
        if (StringUtils.isEmpty(imp.getXNative().getRequest())) {
            throw new PreBidException("extract Adm for Native ad: imp.Native.Request is empty");
        }

        Request nativePayload;

        try {
            nativePayload = mapper.mapper().readValue(imp.getXNative().getRequest(), Request.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }

        final Response.ResponseBuilder nativeResult = Response.builder();
        final Link linkObject = Link.of(getClickUrl(content), new ArrayList<>(), null, null);
        final List<com.iab.openrtb.response.Asset> responseAssets = new ArrayList<>();

        int imgIndex = 0;
        int iconIndex = 0;
        for (Asset asset : nativePayload.getAssets()) {
            final com.iab.openrtb.response.Asset.AssetBuilder responseAsset = com.iab.openrtb.response.Asset.builder();
            if (asset.getTitle() != null) {
                responseAsset.title(extractTitleObject(content));
            } else if (asset.getVideo() != null) {
                responseAsset.video(extractVideoObject(adsType, content, bidType, imp, bid));
            } else if (asset.getImg() != null) {
                final ImageObject.ImageObjectBuilder imgObjectBuilder = ImageObject.builder();
                imgObjectBuilder.url("");
                imgObjectBuilder.type(asset.getImg().getType());
                if (Objects.equals(asset.getImg().getType(), HuaweiAdsConstants.IMAGE_ASSET_TYPE_ICON)) {
                    if (content.getMetaData().getIconList() != null
                            && content.getMetaData().getIconList().size() > iconIndex) {
                        final List<Icon> iconList = content.getMetaData().getIconList();
                        imgObjectBuilder.url(iconList.get(iconIndex).getUrl());
                        imgObjectBuilder.w(iconList.get(iconIndex).getWidth());
                        imgObjectBuilder.h(iconList.get(iconIndex).getHeight());
                        iconIndex++;
                    }
                } else {
                    if (content.getMetaData().getImageInfoList() != null
                            && content.getMetaData().getImageInfoList().size() > imgIndex) {
                        final List<ImageInfo> imageInfos = content.getMetaData().getImageInfoList();
                        imgObjectBuilder.url(imageInfos.get(imgIndex).getUrl());
                        imgObjectBuilder.w(imageInfos.get(imgIndex).getWidth());
                        imgObjectBuilder.h(imageInfos.get(imgIndex).getHeight());
                        imgIndex++;
                    }
                }
                final ImageObject imageObject = imgObjectBuilder.build();

                if (bid.getW() == null || bid.getH() == null || bid.getW() == 0 && bid.getH() == 0) {
                    bidBuilder
                            .h(imageObject.getH())
                            .w(imageObject.getW());
                }
                responseAsset.img(imageObject);
            } else if (asset.getData() != null) {
                responseAsset.data(extractDataObject(content, asset));
            }
            responseAsset.id(asset.getId());
            responseAssets.add(responseAsset.build());
        }
        nativeResult.assets(responseAssets);

        if (content.getMonitorList() != null) {
            for (Monitor monitor : content.getMonitorList()) {
                if (monitor.getUrl().size() == 0) {
                    continue;
                }
                if (monitor.getEventType().equals("click")) {
                    linkObject.getClicktrackers().addAll(monitor.getUrl());
                }
                if (monitor.getEventType().equals("imp")) {
                    nativeResult.imptrackers(monitor.getUrl());
                }
            }
        }
        nativeResult.link(linkObject);
        nativeResult.ver("1.1");
        if (nativePayload.getVer() != null && !nativePayload.getVer().equals("")) {
            nativeResult.ver(nativePayload.getVer());
        }

        try {
            final String result = mapper.mapper().writeValueAsString(nativeResult.build()).replace("\n", "");
            return bidBuilder.adm(result).build();
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private TitleObject extractTitleObject(Content content) {
        final TitleObject.TitleObjectBuilder titleObject = TitleObject.builder();
        final String text = getDecodeValue(content.getMetaData().getTitle());
        titleObject.text(text);
        titleObject.len(text.length());
        return titleObject.build();
    }

    private VideoObject extractVideoObject(AdsType adsType, Content content, BidType bidType, Imp imp, Bid bid) {
        final VideoObject.VideoObjectBuilder videoObject = VideoObject.builder();
        addAdmVideo(adsType, content, bidType, imp, bid);
        videoObject.vasttag(bid.getAdm());
        return videoObject.build();
    }

    private DataObject extractDataObject(Content content, Asset asset) {
        final DataObject.DataObjectBuilder dataObject = DataObject.builder();
        dataObject.value("");
        // TODO set label
        if (Objects.equals(asset.getData().getType(), HuaweiAdsConstants.DATA_ASSET_TYPE_DESC)
                || Objects.equals(asset.getData().getType(), HuaweiAdsConstants.DATA_ASSET_TYPE_DESC2)) {
            // TODO set label
            dataObject.value(getDecodeValue(content.getMetaData().getDescription()));
        }
        return dataObject.build();
    }

    public String getNrl(Content content) {
        if (CollectionUtils.isEmpty(content.getMonitorList())) {
            return null;
        }
        for (Monitor monitor : content.getMonitorList()) {
            if (monitor.getEventType().equals("win") && CollectionUtils.isNotEmpty(monitor.getUrl())) {
                return monitor.getUrl().get(0);
            }
        }
        return null;
    }
}
