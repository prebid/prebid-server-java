package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Request;
import com.iab.openrtb.request.User;
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
import org.prebid.server.bidder.huaweiads.model.response.InteractionType;
import org.prebid.server.bidder.huaweiads.model.response.Monitor;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final List<PkgNameConvert> packageNameConverter;
    private final String closeSiteSelectionByCountry;

    public HuaweiAdsBidder(String endpoint,
                           List<PkgNameConvert> packageNameConverter,
                           String closeSiteSelectionByCountry,
                           JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(endpoint);
        this.packageNameConverter = Objects.requireNonNull(packageNameConverter);
        this.closeSiteSelectionByCountry = Objects.requireNonNull(closeSiteSelectionByCountry);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
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

        huaweiAdsRequestBuilder.multislot(multislot);
        huaweiAdsRequestBuilder.clientAdRequestId(bidRequest.getId());

        HuaweiAdsRequest huaweiAdsRequest;
        try {
            huaweiAdsRequest = getHuaweiAdsRequest(huaweiAdsRequestBuilder.build(), bidRequest);
        } catch (IllegalArgumentException | PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final String countryCode = huaweiAdsRequest.getApp().getCountry();

        return Result.withValue(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(buildEndpoint(countryCode))
                        .headers(getHeaders(extImpHuaweiAds, bidRequest))
                        .body(mapper.encodeToBytes(huaweiAdsRequest))
                        .payload(bidRequest)
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
        final int adtype = parseAdsType(huaweiAdsImpExt.getAdtype().toLowerCase()).getCode();
        final int testStatus = huaweiAdsImpExt.getIsTestAuthorization().equals("true") ? 1 : 0;

        final AdSlot30.AdSlot30Builder adSlot30Builder = AdSlot30.builder();
        adSlot30Builder.slotid(huaweiAdsImpExt.getSlotId());
        adSlot30Builder.adtype(adtype);
        adSlot30Builder.test(testStatus);

        return getOpenrtbFormat(adSlot30Builder.build(), adtype, huaweiAdsImpExt.getAdtype(), imp);
    }

    private AdsType parseAdsType(String adtypeLower) {
        return switch (adtypeLower) {
            case "native" -> AdsType.XNATIVE;
            case "rewarded" -> AdsType.REWARDED;
            case "interstitial" -> AdsType.INTERSTITIAL;
            case "roll" -> AdsType.ROLL;
            case "splash" -> AdsType.SPLASH;
            case "magazinelock" -> AdsType.MAGAZINE_LOCK;
            case "audio" -> AdsType.AUDIO;
            default -> AdsType.BANNER;
        };
    }

    private AdSlot30 getOpenrtbFormat(AdSlot30 adslot30, int adtype, String impExtAdType, Imp imp) {

        if (imp.getBanner() != null) {
            if (adtype != AdsType.BANNER.getCode() && adtype != AdsType.INTERSTITIAL.getCode()) {
                throw new PreBidException(
                        "check openrtb format: request has banner, doesn't correspond to huawei adtype "
                                + impExtAdType);
            }
            return getBannerFormat(adslot30, imp);
        } else if (imp.getXNative() != null) {
            if (adtype != AdsType.XNATIVE.getCode()) {
                throw new PreBidException(
                        "check openrtb format: request has native, doesn't correspond to huawei adtype "
                                + impExtAdType);
            }
            return getNativeFormat(adslot30, imp);
        } else if (imp.getVideo() != null) {
            if (adtype != AdsType.BANNER.getCode()
                    && adtype != AdsType.INTERSTITIAL.getCode()
                    && adtype != AdsType.REWARDED.getCode()
                    && adtype != AdsType.ROLL.getCode()) {
                throw new PreBidException(
                        "check openrtb format: request has video, doesn't correspond to huawei adtype "
                                + impExtAdType);
            }
            return getVideoFormat(adslot30, adtype, imp);
        } else if (imp.getAudio() != null) {
            throw new PreBidException("check openrtb format: request has audio, not currently supported");
        } else {
            throw new PreBidException(
                    "check openrtb format: please choose one of our supported type banner, native, or video");
        }
    }

    private AdSlot30 getBannerFormat(AdSlot30 adslot30, Imp imp) {
        AdSlot30.AdSlot30Builder adSlot30Builder = adslot30.toBuilder();
        if (imp.getBanner().getW() != null && imp.getBanner().getH() != null) {

            adSlot30Builder.w(imp.getBanner().getW());
            adSlot30Builder.h(imp.getBanner().getH());
        }

        if (CollectionUtils.isNotEmpty(imp.getBanner().getFormat())) {
            final List<Format> formats = imp.getBanner().getFormat()
                    .stream()
                    .filter(f -> f.getH() != 0 && f.getW() != 0)
                    .map(f -> Format.of(f.getW(), f.getH()))
                    .collect(Collectors.toList());

            adSlot30Builder.format(formats);
        }

        return adSlot30Builder.build();
    }

    private AdSlot30 getNativeFormat(AdSlot30 adslot30, Imp imp) {
        AdSlot30.AdSlot30Builder adSlot30Builder = adslot30.toBuilder();

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

        adSlot30Builder.w(width);
        adSlot30Builder.h(height);

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

        adSlot30Builder.detailedCreativeTypeList(detailedCreativeTypeList);

        return adSlot30Builder.build();
    }

    private AdSlot30 getVideoFormat(AdSlot30 adslot30, int adtype, Imp openRTBImp) {
        AdSlot30.AdSlot30Builder adSlot30Builder = adslot30.toBuilder();
        adSlot30Builder.w(openRTBImp.getVideo().getW());
        adSlot30Builder.h(openRTBImp.getVideo().getH());

        if (adtype == AdsType.ROLL.getCode()) {
            if (openRTBImp.getVideo().getMaxduration() == null || openRTBImp.getVideo().getMaxduration() == 0) {
                throw new PreBidException(
                        "Extract openrtb video failed: MaxDuration is empty when huaweiads adtype is roll.");
            }
            adSlot30Builder.totalDuration(openRTBImp.getVideo().getMaxduration());
        }
        return adSlot30Builder.build();
    }

    private HuaweiAdsRequest getHuaweiAdsRequest(HuaweiAdsRequest request, BidRequest openRTBRequest) {
        HuaweiAdsRequest.HuaweiAdsRequestBuilder huaweiAdsRequestBuilder = request.toBuilder();
        huaweiAdsRequestBuilder.version(HuaweiAdsConstants.API_VERSION);

        huaweiAdsRequestBuilder.app(getAppInfo(openRTBRequest));
        huaweiAdsRequestBuilder.device(getDeviceInfo(openRTBRequest));
        huaweiAdsRequestBuilder.network(getNetworkInfo(openRTBRequest));
        huaweiAdsRequestBuilder.regs(getRegsInfo(openRTBRequest));
        huaweiAdsRequestBuilder.geo(getGeoInfo(openRTBRequest));
        huaweiAdsRequestBuilder.consent(getConsentInfo(openRTBRequest));

        return huaweiAdsRequestBuilder.build();
    }

    private App getAppInfo(BidRequest openRTBRequest) {
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
                app.pkgname(getFinalPackageName(openRTBRequest.getApp().getBundle()));
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
        return app.build();
    }

    private String getFinalPackageName(String bundleName) {
        for (PkgNameConvert convert : packageNameConverter) {
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

    private Device getDeviceInfo(BidRequest openRTBRequest) {
        final Device.DeviceBuilder deviceBuilder = Device.builder();
        Optional.ofNullable(openRTBRequest.getDevice())
                .ifPresent(deviceData -> {
                    deviceBuilder.type(deviceData.getDevicetype());
                    deviceBuilder.useragent(deviceData.getUa());
                    deviceBuilder.os(deviceData.getOs());
                    deviceBuilder.version(deviceData.getOsv());
                    deviceBuilder.maker(deviceData.getMake());
                    deviceBuilder.model(StringUtils.isEmpty(deviceData.getModel())
                            ? HuaweiAdsConstants.DEFAULT_MODEL_NAME : deviceData.getModel());
                    deviceBuilder.height(deviceData.getH());
                    deviceBuilder.width(deviceData.getW());
                    deviceBuilder.language(deviceData.getLanguage());
                    deviceBuilder.pxratio(deviceData.getPxratio());
                    String countryCode = getCountryCode(openRTBRequest);
                    deviceBuilder.belongCountry(countryCode);
                    deviceBuilder.localeCountry(countryCode);
                    deviceBuilder.ip(deviceData.getIp());
                    deviceBuilder.gaid(deviceData.getIfa());
                });

        Device device = getDeviceIdFromUserExt(deviceBuilder.build(), openRTBRequest);

        Optional.ofNullable(openRTBRequest.getDevice())
                .map(com.iab.openrtb.request.Device::getDnt)
                .ifPresent(dnt -> {
                    if (!device.getOaid().isEmpty()) {
                        deviceBuilder.isTrackingEnabled(dnt == 1 ? "0" : "1");
                    }
                    if (!device.getGaid().isEmpty()) {
                        deviceBuilder.gaidTrackingEnabled(dnt == 1 ? "0" : "1");
                    }
                });

        return device;
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

    private Device getDeviceIdFromUserExt(Device device, BidRequest bidRequest) {
        Device.DeviceBuilder deviceBuilder = device.toBuilder();
        final Optional<User> userOptional = Optional.ofNullable(bidRequest.getUser());
        if (userOptional.isPresent() && userOptional.get().getExt() != null) {
            final ExtUserDataHuaweiAds extUserDataHuaweiAds = mapper.mapper()
                    .convertValue(userOptional.get().getExt().getData(), ExtUserDataHuaweiAds.class);

            final ExtUserDataDeviceIdHuaweiAds deviceId = extUserDataHuaweiAds.getData();

            boolean isValidDeviceId = false;
            if (CollectionUtils.isNotEmpty(deviceId.getOaid())) {
                deviceBuilder.oaid(deviceId.getOaid().get(0));
                isValidDeviceId = true;
            }
            if (CollectionUtils.isNotEmpty(deviceId.getGaid())) {
                deviceBuilder.gaid(deviceId.getGaid().get(0));
                isValidDeviceId = true;
            }
            if (CollectionUtils.isNotEmpty(deviceId.getImei())) {
                deviceBuilder.imei(deviceId.getImei().get(0));
                isValidDeviceId = true;
            }
            if (!isValidDeviceId) {
                throw new PreBidException("getDeviceID: Imei, Oaid, Gaid are all empty.");
            }
            if (CollectionUtils.isNotEmpty(deviceId.getClientTime())) {
                deviceBuilder.clientTime(ClientTimeConverter.getClientTime(deviceId.getClientTime().get(0)));
            }
        } else {
            if (device.getGaid() == null || device.getGaid().isEmpty()) {
                throw new PreBidException(
                        "getDeviceID: openRTBRequest.User.Ext is null and device.Gaid is not specified.");
            }
        }

        return deviceBuilder.build();
    }

    private Network getNetworkInfo(BidRequest openRTBRequest) {
        if (openRTBRequest.getDevice() != null) {
            Network.NetworkBuilder network = Network.builder();
            if (openRTBRequest.getDevice().getConnectiontype() != null) {
                network.type(openRTBRequest.getDevice().getConnectiontype());
            } else {
                network.type(HuaweiAdsConstants.DEFAULT_UNKNOWN_NETWORK_TYPE);
            }

            List<CellInfo> cellInfos = new ArrayList<>();
            if (StringUtils.isNotEmpty(openRTBRequest.getDevice().getMccmnc())) {
                String[] arr = openRTBRequest.getDevice().getMccmnc().split("-");
                network.carrier(0);
                if (arr.length >= 2) {
                    cellInfos.add(CellInfo.of(arr[0], arr[1]));
                    String str = arr[0] + arr[1];
                    switch (str) {
                        case "46000", "46002", "46007" -> network.carrier(2);
                        case "46001", "46006" -> network.carrier(1);
                        case "46003", "46005", "46011" -> network.carrier(3);
                        default -> network.carrier(99);
                    }
                }
            }
            network.cellInfo(cellInfos);
            return network.build();
        }
        return null;
    }

    private Regs getRegsInfo(BidRequest openRTBRequest) {
        if (openRTBRequest.getRegs() != null && openRTBRequest.getRegs().getCoppa() >= 0) {
            return Regs.of(openRTBRequest.getRegs().getCoppa());
        }
        return null;
    }

    private Geo getGeoInfo(BidRequest openRTBRequest) {
        if (openRTBRequest.getDevice() != null && openRTBRequest.getDevice().getGeo() != null) {

            return Geo.of(BigDecimal.valueOf(openRTBRequest.getDevice().getGeo().getLon()),
                    BigDecimal.valueOf(openRTBRequest.getDevice().getGeo().getLat()),
                    openRTBRequest.getDevice().getGeo().getAccuracy(),
                    openRTBRequest.getDevice().getGeo().getLastfix());
        }

        return null;
    }

    private String getConsentInfo(BidRequest openRtbRequest) {
        if (openRtbRequest.getUser() != null && openRtbRequest.getUser().getExt() != null) {
            final ExtUser extUser = openRtbRequest.getUser().getExt();

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
        final HuaweiAdsResponse huaweiAdsResponse = parseBidResponse(httpCall.getResponse());
        checkHuaweiAdsResponseRetcode(huaweiAdsResponse);

        final List<BidderBid> bidderBids = convertHuaweiAdsResponse(huaweiAdsResponse, bidRequest);

        return Result.withValues(bidderBids);
    }

    private HuaweiAdsResponse parseBidResponse(HttpResponse response) {
        try {
            return mapper.decodeValue(response.getBody(), HuaweiAdsResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException("Unable to parse server response");
        }
    }

    private void checkHuaweiAdsResponseRetcode(HuaweiAdsResponse response) {
        if (response.getRetcode() == 200
                || response.getRetcode() == 204
                || response.getRetcode() == 206) {
            return;
        }
        if ((response.getRetcode() < 600
                && response.getRetcode() >= 400)
                || (response.getRetcode() < 300
                && response.getRetcode() > 200)) {
            throw new PreBidException(String.format("HuaweiAdsResponse retcode: %d, reason: %s",
                    response.getRetcode(), response.getReason()));
        }
    }

    private List<BidderBid> convertHuaweiAdsResponse(HuaweiAdsResponse huaweiAdsResponse, BidRequest bidRequest) {
        if (huaweiAdsResponse.getMultiad().size() == 0) {
            throw new PreBidException("convert huaweiads response to bidder response failed: multiad length is 0, "
                    + "get no ads from huawei side.");
        }

        final List<BidderBid> bidderBids = new ArrayList<>();

        final BidResponse.BidResponseBuilder bidResponse = BidResponse.builder();

        bidResponse.cur(HuaweiAdsConstants.DEFAULT_CURRENCY);

        final Map<String, Imp> mapSlotIdImp = new HashMap<>();
        final Map<String, BidType> mapSlotIdMediaType = new HashMap<>();
        for (Imp imp : bidRequest.getImp()) {

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

        if (mapSlotIdMediaType.size() < 1 || mapSlotIdImp.size() < 1) {
            throw new PreBidException(
                    "convert huaweiads response to bidder response failed: openRTBRequest.imp is nil");
        }

        for (Ad30 ad30 : huaweiAdsResponse.getMultiad()) {
            if (mapSlotIdImp.get(ad30.getSlotId()).getId().equals("")) {
                continue;
            }

            if (ad30.getRetCode30() != 200) {
                continue;
            }

            for (Content content : ad30.getContentList()) {
                Bid.BidBuilder bidBuilder = Bid.builder();
                bidBuilder.id(mapSlotIdImp.get(ad30.getSlotId()).getId());
                bidBuilder.impid(mapSlotIdImp.get(ad30.getSlotId()).getId());

                bidBuilder.price(BigDecimal.valueOf(content.getPrice()));
                bidBuilder.crid(content.getContentId());

                if (!content.getCur().equals("")) {
                    bidResponse.cur(content.getCur());
                }

                bidBuilder = handleHuaweiAdsContent(ad30.getAdType(),
                        content,
                        mapSlotIdMediaType.get(ad30.getSlotId()),
                        mapSlotIdImp.get(ad30.getSlotId()),
                        bidBuilder.build())
                        .toBuilder();

                bidBuilder.adomain(List.of("huaweiads"));
                bidBuilder.nurl(getNrl(content));
                final Bid bid = bidBuilder.build();
                bidderBids.add(BidderBid.of(bid,
                        mapSlotIdMediaType.get(ad30.getSlotId()),
                        bidResponse.build().getCur()));
            }
        }
        return bidderBids;
    }

    private Bid handleHuaweiAdsContent(int adType, Content content, BidType bidType, Imp imp, Bid bid) {
        switch (bidType) {
            case banner -> {
                return addAdmBanner(adType, content, bidType, imp, bid);
            }
            case xNative -> {
                return addAdmNative(adType, content, bidType, imp, bid);
            }
            case video -> {
                return addAdmVideo(adType, content, bidType, imp, bid);
            }
            default -> throw new PreBidException("no support bidtype: audio");
        }
    }

    private Bid addAdmBanner(int adType, Content content, BidType bidType, Imp imp, Bid bid) {

        if (adType != AdsType.BANNER.getCode() && adType != AdsType.INTERSTITIAL.getCode()) {
            throw new PreBidException("openrtb banner should correspond to huaweiads adtype: banner or interstitial");
        }

        int creativeType = content.getCreativeType();
        if (content.getCreativeType() > 100) {
            creativeType = creativeType - 100;
        }

        if (creativeType == CreativeType.TEXT.getCode()
                || creativeType == CreativeType.BIG_PICTURE.getCode()
                || creativeType == CreativeType.BIG_PICTURE_2.getCode()
                || creativeType == CreativeType.SMALL_PICTURE.getCode()
                || creativeType == CreativeType.THREE_SMALL_PICTURES_TEXT.getCode()
                || creativeType == CreativeType.ICON_TEXT.getCode() || creativeType == CreativeType.GIF.getCode()) {
            return addAdmPicture(content, bid);
        } else if (creativeType == CreativeType.VIDEO_TEXT.getCode()
                || creativeType == CreativeType.VIDEO.getCode()
                || creativeType == CreativeType.VIDEO_WITH_PICTURES_TEXT.getCode()) {
            return addAdmVideo(adType, content, bidType, imp, bid);
        } else {
            throw new PreBidException("no banner support creativetype");
        }
    }

    private Bid addAdmPicture(Content content, Bid bid) {
        Bid.BidBuilder bidBuilder = bid.toBuilder();
        if (content == null) {
            throw new PreBidException("extract Adm failed: content is empty");
        }

        final String clickUrl = getClickUrl(content);

        String imageInfoUrl;
        long adHeight = 0;
        long adWidth = 0;

        if (content.getMetaData().getImageInfoList() != null) {
            imageInfoUrl = content.getMetaData().getImageInfoList().get(0).getUrl();
            bidBuilder.h(content.getMetaData().getImageInfoList().get(0).getHeight());
            bidBuilder.w(content.getMetaData().getImageInfoList().get(0).getWidth());
        } else {
            throw new PreBidException("content.MetaData.ImageInfo is empty");
        }

        final String imageTitle = getDecodeValue(content.getMetaData().getTitle());

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
                + "function sendSomeGetReq(urls) {"
                + "urls.forEach(sendOneGetReq);"
                + "}"
                + "</script>";

        return bidBuilder.adm(adm).build();
    }

    private String getClickUrl(Content content) {
        String clickUrl = "";

        if (Objects.equals(InteractionType.APP_PROMOTION.getCode(), content.getInteractionType())) {
            if (!content.getMetaData().getIntent().isEmpty()) {
                clickUrl = getDecodeValue(content.getMetaData().getIntent());
            } else {
                throw new PreBidException(
                        "content.MetaData.Intent in huaweiads resopnse is empty when interactiontype is appPromotion");
            }
        } else {
            if (!content.getMetaData().getClickUrl().isEmpty()) {
                clickUrl = content.getMetaData().getClickUrl();
            } else if (!content.getMetaData().getIntent().isEmpty()) {
                clickUrl = getDecodeValue(content.getMetaData().getIntent());
            }
        }

        return clickUrl;
    }

    private String getDecodeValue(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }

        return URLDecoder.decode(str, StandardCharsets.UTF_8);
    }

    private void extractDspImpClickTrackings(Content content,
                                             List<String> dspImpTrackings,
                                             StringBuilder dspClickTrackings) {
        for (Monitor monitor : content.getMonitorList()) {
            if (!monitor.getUrl().isEmpty()) {
                switch (monitor.getEventType()) {
                    case "imp" -> dspImpTrackings.addAll(monitor.getUrl());
                    case "click" -> dspClickTrackings.append(String.join(",", monitor.getUrl()));
                }
            }
        }
    }

    private Bid addAdmVideo(int adType, Content content, BidType bidType, Imp imp, Bid bid) {
        if (content == null) {
            throw new PreBidException("extract Adm for video failed: content is empty");
        }
        Bid.BidBuilder bidBuilder = bid.toBuilder();
        final String clickUrl = getClickUrl(content);

        String mime = "video/mp4";
        String resourceUrl;
        String duration;

        Integer adWidth = 0;
        Integer adHeight = 0;
        if (adType == AdsType.ROLL.getCode()) {
            // roll ad get information from mediafile
            if (!content.getMetaData().getMediaFile().getMime().isEmpty()) {
                mime = content.getMetaData().getMediaFile().getMime();
            }
            adWidth = content.getMetaData().getMediaFile().getWidth();
            bidBuilder.w(content.getMetaData().getMediaFile().getWidth());
            adHeight = content.getMetaData().getMediaFile().getHeight();
            bidBuilder.h(content.getMetaData().getMediaFile().getHeight());
            if (!content.getMetaData().getMediaFile().getUrl().isEmpty()) {
                resourceUrl = content.getMetaData().getMediaFile().getUrl();
            } else {
                throw new PreBidException("extract Adm for video failed: Content.MetaData.MediaFile.Url is empty");
            }
            duration = getDuration(content.getMetaData().getDuration());
        } else {
            if (!content.getMetaData().getVideoInfo().getVideoDownloadUrl().isEmpty()) {
                resourceUrl = content.getMetaData().getVideoInfo().getVideoDownloadUrl();
            } else {
                throw new PreBidException(
                        "extract Adm for video failed: content.MetaData.VideoInfo.VideoDownloadUrl is empty");
            }
            if (content.getMetaData().getVideoInfo().getWidth() != 0
                    && content.getMetaData().getVideoInfo().getHeight() != 0) {
                bidBuilder.w(content.getMetaData().getVideoInfo().getWidth());
                bidBuilder.h(content.getMetaData().getVideoInfo().getHeight());
            } else if (bidType == BidType.video && imp.getVideo() != null
                    && imp.getVideo().getW() != 0 && imp.getVideo().getH() != 0) {
                adWidth = imp.getVideo().getW();
                bidBuilder.w(imp.getVideo().getW());
                adHeight = imp.getVideo().getH();
                bidBuilder.h(imp.getVideo().getH());
            } else {
                throw new PreBidException("extract Adm for video failed: cannot get video width, height");
            }
            duration = getDuration(content.getMetaData().getVideoInfo().getVideoDuration());
        }

        final String adTitle = getDecodeValue(content.getMetaData().getTitle());
        final String adId = content.getContentId();
        final String creativeId = content.getContentId();
        final StringBuilder trackingEvents = new StringBuilder();
        final StringBuilder dspImpTracking2Str = new StringBuilder();
        final StringBuilder dspClickTracking2Str = new StringBuilder();
        final StringBuilder errorTracking2Str = new StringBuilder();

        for (Monitor monitor : content.getMonitorList()) {
            if (monitor.getUrl().isEmpty()) {
                continue;
            }
            String event = getEvent(dspImpTracking2Str, dspClickTracking2Str, errorTracking2Str, monitor);

            if (!event.isEmpty()) {
                trackingEvents.append(getVastEventTrackingUrls(monitor.getUrl(), event));
            }
        }

        String rewardedVideoPart = "";
        boolean isAddRewardedVideoPart = true;

        if (adType == AdsType.REWARDED.getCode()) {
            String staticImageUrl = "";
            String staticImageHeight = "";
            String staticImageWidth = "";
            final String staticImageType = "image/png";

            if (!content.getMetaData().getIconList().isEmpty()
                    && !content.getMetaData().getIconList().get(0).getUrl().isEmpty()) {
                staticImageUrl = content.getMetaData().getIconList().get(0).getUrl();

                if (content.getMetaData().getIconList().get(0).getHeight() > 0
                        && content.getMetaData().getIconList().get(0).getWidth() > 0) {
                    staticImageHeight = String.valueOf(content.getMetaData().getIconList().get(0).getHeight());
                    staticImageWidth = String.valueOf(content.getMetaData().getIconList().get(0).getWidth());
                } else {
                    staticImageHeight = String.valueOf(adHeight);
                    staticImageWidth = String.valueOf(adWidth);
                }
            } else if (!content.getMetaData().getImageInfoList().isEmpty()
                    && !content.getMetaData().getImageInfoList().get(0).getUrl().isEmpty()) {
                staticImageUrl = content.getMetaData().getImageInfoList().get(0).getUrl();

                if (content.getMetaData().getImageInfoList().get(0).getHeight() > 0
                        && content.getMetaData().getImageInfoList().get(0).getWidth() > 0) {
                    staticImageHeight = String.valueOf(content.getMetaData().getImageInfoList().get(0).getHeight());
                    staticImageWidth = String.valueOf(content.getMetaData().getImageInfoList().get(0).getWidth());
                } else {
                    staticImageHeight = String.valueOf(adHeight);
                    staticImageWidth = String.valueOf(adWidth);
                }
            } else {
                isAddRewardedVideoPart = false;
            }

            if (isAddRewardedVideoPart) {
                rewardedVideoPart = "<Creative adId=\"" + adId + "\" id=\"" + creativeId + "\">"
                        + "<CompanionAds>"
                        + "<Companion width=\"" + staticImageWidth + "\" height=\"" + staticImageHeight + "\">"
                        + "<StaticResource creativeType=\"" + staticImageType
                        + "\"><![CDATA[" + staticImageUrl + "]]></StaticResource>"
                        + "<CompanionClickThrough><![CDATA[" + clickUrl + "]]></CompanionClickThrough>"
                        + "</Companion>"
                        + "</CompanionAds>"
                        + "</Creative>";
            }
        }

        final String adm = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
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
                + "</InLine>"
                + "</Ad>"
                + "</VAST>";

        return bidBuilder.adm(adm).build();
    }

    private String getEvent(StringBuilder dspImpTracking2Str,
                            StringBuilder dspClickTracking2Str,
                            StringBuilder errorTracking2Str,
                            Monitor monitor) {
        String event = "";
        switch (monitor.getEventType()) {
            case "vastError":
                event = errorTracking2Str.append(
                        getVastImpClickErrorTrackingUrls(monitor.getUrl(), "vastError")).toString();
                break;
            case "imp":
                event = dspImpTracking2Str.append(
                        getVastImpClickErrorTrackingUrls(monitor.getUrl(), "imp")).toString();
                break;
            case "click":
                event = dspClickTracking2Str.append(
                        getVastImpClickErrorTrackingUrls(monitor.getUrl(), "click")).toString();
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
        return event;
    }

    private String getDuration(long duration) {
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

    public Bid addAdmNative(int adType, Content content, BidType bidType, Imp imp, Bid bid) {
        Bid.BidBuilder bidBuilder = bid.toBuilder();

        if (adType != AdsType.XNATIVE.getCode()) {
            throw new PreBidException("extract Adm for Native ad: huaweiads response is not a native ad");
        }
        if (imp.getXNative() == null) {
            throw new PreBidException("extract Adm for Native ad: imp.Native is null");
        }
        if (imp.getXNative().getRequest() == null || imp.getXNative().getRequest().isEmpty()) {
            throw new PreBidException("extract Adm for Native ad: imp.Native.Request is empty");
        }

        Request nativePayload;

        try {
            nativePayload = mapper.mapper().readValue(imp.getXNative().getRequest(), Request.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }

        final Response.ResponseBuilder nativeResult = Response.builder();
        final Link linkObject = Link.of(getClickUrl(content), null, null, null);
        final List<com.iab.openrtb.response.Asset> responseAssets = new ArrayList<>();

        int imgIndex = 0;
        int iconIndex = 0;
        for (Asset asset : nativePayload.getAssets()) {
            final com.iab.openrtb.response.Asset.AssetBuilder responseAsset = com.iab.openrtb.response.Asset.builder();
            if (asset.getTitle() != null) {
                final TitleObject.TitleObjectBuilder titleObject = TitleObject.builder();
                final String text = getDecodeValue(content.getMetaData().getTitle());
                titleObject.text(text);
                titleObject.len(text.length());
                responseAsset.title(titleObject.build());
            } else if (asset.getVideo() != null) {
                final VideoObject.VideoObjectBuilder videoObject = VideoObject.builder();
                addAdmVideo(adType, content, bidType, imp, bid);
                videoObject.vasttag(bid.getAdm());
                responseAsset.video(videoObject.build());
            } else if (asset.getImg() != null) {
                final ImageObject.ImageObjectBuilder imgObjectBuilder = ImageObject.builder();
                imgObjectBuilder.url("");
                imgObjectBuilder.type(asset.getImg().getType());
                if (Objects.equals(asset.getImg().getType(), HuaweiAdsConstants.IMAGE_ASSET_TYPE_ICON)) {
                    if (content.getMetaData().getIconList().size() > iconIndex) {
                        imgObjectBuilder.url(content.getMetaData().getIconList().get(iconIndex).getUrl());
                        imgObjectBuilder.w(content.getMetaData().getIconList().get(iconIndex).getWidth());
                        imgObjectBuilder.h(content.getMetaData().getIconList().get(iconIndex).getHeight());
                        iconIndex++;
                    }
                } else {
                    if (content.getMetaData().getImageInfoList().size() > imgIndex) {
                        imgObjectBuilder.url(content.getMetaData().getImageInfoList().get(imgIndex).getUrl());
                        imgObjectBuilder.w(content.getMetaData().getImageInfoList().get(imgIndex).getWidth());
                        imgObjectBuilder.h(content.getMetaData().getImageInfoList().get(imgIndex).getHeight());
                        imgIndex++;
                    }
                }
                final ImageObject imageObject = imgObjectBuilder.build();

                if (bid.getW() == 0 && bid.getH() == 0) {
                    bidBuilder.h(imageObject.getH());
                    bidBuilder.w(imageObject.getW());
                }
                responseAsset.img(imageObject);
            } else if (asset.getData() != null) {
                final DataObject.DataObjectBuilder dataObject = DataObject.builder();
                dataObject.value("");
                // TODO set label
                if (Objects.equals(asset.getData().getType(), HuaweiAdsConstants.DATA_ASSET_TYPE_DESC)
                        || Objects.equals(asset.getData().getType(), HuaweiAdsConstants.DATA_ASSET_TYPE_DESC2)) {
                    // TODO set label
                    dataObject.value(getDecodeValue(content.getMetaData().getDescription()));
                }
                responseAsset.data(dataObject.build());
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
            final String result = mapper.mapper().writeValueAsString(nativeResult).replace("\n", "");
            return bidBuilder.adm(result).build();
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    public String getNrl(Content content) {
        if (content.getMonitorList() == null || content.getMonitorList().isEmpty()) {
            return "";
        }
        for (Monitor monitor : content.getMonitorList()) {
            if (monitor.getEventType().equals("win") && !monitor.getUrl().isEmpty()) {
                return monitor.getUrl().get(0);
            }
        }
        return "";
    }
}
