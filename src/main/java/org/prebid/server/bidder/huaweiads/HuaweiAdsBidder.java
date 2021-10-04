package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.ImageObject;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.huaweiads.model.HuaweiAdSlot;
import org.prebid.server.bidder.huaweiads.model.HuaweiFormat;
import org.prebid.server.bidder.huaweiads.model.HuaweiNativeRequest;
import org.prebid.server.bidder.huaweiads.model.HuaweiRequest;
import org.prebid.server.bidder.huaweiads.model.HuaweiResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuaweiAds;
import org.prebid.server.util.HttpUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class HuaweiAdsBidder implements Bidder<HuaweiRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpHuaweiAds>> HUAWEIADS_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpHuaweiAds>>() {
            };

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
        final HuaweiRequest huaweiAdsRequest = new HuaweiRequest();
        ExtImpHuaweiAds extImpHuaweiAds = null;
        for (Imp imp : request.getImp()) {
            try {
                extImpHuaweiAds = HuaweiUtilResponse.parseImpExt(imp, mapper, HUAWEIADS_EXT_TYPE_REFERENCE);
                multislot.add(getHuaweiAdsReqAdslot(extImpHuaweiAds, request, imp));
                huaweiAdsRequest.setMultislot(multislot);
                HuaweiUtilRequest.getHuaweiAdsReqJson(huaweiAdsRequest, request, mapper);
            } catch (PreBidException e) {
                return Result.withErrors(Collections.singletonList(BidderError.badInput(e.getMessage())));
            }
        }

        final String reqJson;
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
        final MultiMap headers = HttpUtil.headers();
        if (extImpHuaweiAds == null) {
            return headers;
        }
        headers.add("Authorization", getDigestAuthorization(extImpHuaweiAds, isTestAuthorization));
        Device device = request.getDevice();
        if (device != null && device.getUa().length() > 0) {
            headers.add("User-Agent", device.getUa());
        }
        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<HuaweiRequest> httpCall, BidRequest bidRequest) {
        final HuaweiResponse huaweiAdsResponse;
        final List<BidderBid> bidderResponse;
        try {
            huaweiAdsResponse = mapper.decodeValue(httpCall.getResponse().getBody(), HuaweiResponse.class);
            checkHuaweiAdsResponseRetcode(huaweiAdsResponse);
            bidderResponse = HuaweiUtilResponse.convertHuaweiAdsRespToBidderResp(huaweiAdsResponse, bidRequest, mapper,
                    HUAWEIADS_EXT_TYPE_REFERENCE).getSeatBid().getBids();
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
        return Result.of(bidderResponse, Collections.emptyList());
    }

    private HuaweiAdSlot getHuaweiAdsReqAdslot(ExtImpHuaweiAds extImpHuaweiAds, BidRequest request, Imp imp) {
        final String lowerAdType = StringUtils.lowerCase(extImpHuaweiAds.getAdtype());
        final Banner banner = imp.getBanner();
        if (banner == null) {
            throw new PreBidException("getHuaweiAdsReqAdslot: Imp.Banner is null");
        }

        final HuaweiAdSlot huaweiAdSlot = HuaweiAdSlot.builder()
                .slotId(extImpHuaweiAds.getSlotId())
                .adType(convertAdtypeString2Integer(lowerAdType))
                .test(request.getTest())
                .format(resolveFormat(banner))
                .w(resolveAdSlotWidth(banner))
                .h(resolveAdSlotHeight(banner))
                .totalDuration(resolveTotalDuration(imp, lowerAdType))
                .build();

        if (imp.getXNative() != null) {
            getNativeFormat(huaweiAdSlot, imp);
            return huaweiAdSlot;
        }

        return huaweiAdSlot;
    }

    private Integer resolveTotalDuration(Imp imp, String lowerAdType) {
        if (lowerAdType.equals("roll")) {
            final Video video = imp.getVideo();
            if (video != null && video.getMaxduration() != null && video.getMaxduration() >= 0) {
                return video.getMaxduration();
            } else {
                throw new PreBidException("GetHuaweiAdsReqAdslot: Video maxDuration is empty when adtype is roll");
            }
        }
        return null;
    }

    private Integer resolveAdSlotWidth(Banner banner) {
        if (banner.getW() != null && banner.getH() != null) {
            return banner.getW();
        }
        return 0;
    }

    private Integer resolveAdSlotHeight(Banner banner) {
        if (banner.getW() != null && banner.getH() != null) {
            return banner.getH();
        }
        return null;
    }

    private List<HuaweiFormat> resolveFormat(Banner banner) {
        if (banner.getFormat().size() != 0) {
            return banner.getFormat().stream()
                    .filter(oldFormat -> oldFormat.getH() != 0 && oldFormat.getW() != 0)
                    .map(oldFormat -> HuaweiFormat.of(oldFormat.getW(), oldFormat.getH()))
                    .collect(Collectors.toList());
        }
        return null;
    }

    private void getNativeFormat(HuaweiAdSlot huaweiAdSlot, Imp imp) {
        final String request = imp.getXNative().getRequest();
        if (StringUtils.isBlank(request)) {
            throw new PreBidException("getNativeFormat: imp.xNative.request is empty");
        }
        HuaweiNativeRequest nativePayload = null;
        try {
            nativePayload = mapper.mapper().readValue(request, HuaweiNativeRequest.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }
        final List<Asset> assets = nativePayload.getAssets();
        if (assets == null) {
            throw new PreBidException("getNativeFormat: nativePayload.Asset is null");
        }
        huaweiAdSlot.setH(resolveNativeHeight(nativePayload.getAssets()));
        huaweiAdSlot.setW(resolveNativeWidth(nativePayload.getAssets()));
        huaweiAdSlot.setDetailedCreativeTypeList(resolveDetailedCreativeList(nativePayload.getAssets()));
    }

    private List<String> resolveDetailedCreativeList(List<Asset> assets) {
        final ArrayList<String> detailedCreativeTypeList = new ArrayList<>();

        int numImage = 0;
        int numVideo = 0;

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

    private Integer resolveNativeWidth(List<Asset> assets) {
        int width = 0;
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

    private Integer resolveNativeHeight(List<Asset> assets) {
        int height = 0;
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

    private void checkHuaweiAdsResponseRetcode(HuaweiResponse response) {
        final Integer retcode = response.getRetcode();
        if (retcode == null) {
            throw new PreBidException("HuaweiAdsResponse.Retcode is null");
        }
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
        return "Digest username=" + publisherId + ","
                + "realm=ppsadx/getResult,"
                + "nonce=" + nonce + ","
                + "response=" + computeHmacSha256(nonce + ":POST:/ppsadx/getResult", apiKey) + ","
                + "algorithm=HmacSHA256,usertype=1,keyid=" + extImpHuaweiAds.getKeyId();
    }

    private String computeHmacSha256(String data, String apiKey) {
        String output;
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(apiKey.getBytes("UTF-8"), "HmacSHA256");
            sha256Hmac.init(secretKey);
            output = Hex.encodeHexString(sha256Hmac.doFinal(data.getBytes("UTF-8")));
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException | InvalidKeyException e) {
            throw new PreBidException(e.getMessage());
        }
        return output;
    }

    private static String generateNonce() {
        final String dateTimeString = Long.toString(new Date().getTime());
        final byte[] nonceByte = dateTimeString.getBytes();
        return Base64.getEncoder().encodeToString(nonceByte);
    }
}

