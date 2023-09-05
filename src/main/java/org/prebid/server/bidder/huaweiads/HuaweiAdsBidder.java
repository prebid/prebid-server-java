package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.huaweiads.model.AdsType;
import org.prebid.server.bidder.huaweiads.model.request.AdSlot30;
import org.prebid.server.bidder.huaweiads.model.request.Geo;
import org.prebid.server.bidder.huaweiads.model.request.HuaweiAdsRequest;
import org.prebid.server.bidder.huaweiads.model.request.Regs;
import org.prebid.server.bidder.huaweiads.model.response.Ad30;
import org.prebid.server.bidder.huaweiads.model.response.Content;
import org.prebid.server.bidder.huaweiads.model.response.HuaweiAdm;
import org.prebid.server.bidder.huaweiads.model.response.HuaweiAdsResponse;
import org.prebid.server.bidder.huaweiads.model.response.Monitor;
import org.prebid.server.bidder.huaweiads.model.response.MonitorEventType;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuaweiAds;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class HuaweiAdsBidder implements Bidder<HuaweiAdsRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpHuaweiAds>> EXT_IMP_TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final String HUAWEI_ADX_API_VERSION = "3.4";
    private static final String DEFAULT_COUNTRY_CODE = "ZA";
    private static final String DEFAULT_BID_CURRENCY = "CNY";
    private static final String CLOSE_COUNTRY = "1";
    private static final Set<String> CHINESE_COUNTRY_CODES = Set.of("CN");
    private static final Set<String> RUSSIAN_COUNTRY_CODES = Set.of("RU");
    private static final Set<String> EUROPEAN_COUNTRY_CODES = Set.of(
            "AX", "AL", "AD", "AU", "AT", "BE", "BA", "BG", "CA", "HR", "CY", "CZ",
            "DK", "EE", "FO", "FI", "FR", "DE", "GI", "GR", "GL", "GG", "VA", "HU",
            "IS", "IE", "IM", "IL", "IT", "JE", "YK", "LV", "LI", "LT", "LU", "MT",
            "MD", "MC", "ME", "NL", "AN", "NZ", "NO", "PL", "PT", "RO", "MF", "VC",
            "SM", "RS", "SX", "SK", "SI", "ES", "SE", "CH", "TR", "UA", "GB", "US",
            "MK", "SJ", "BQ", "PM", "CW");
    private static final List<String> HUAWEIADS_DOMAIN = List.of("huaweiads");

    private final JacksonMapper mapper;
    private final String endpointUrl;
    private final String closeSiteSelectionByCountry;
    private final String chineseEndpoint;
    private final String russianEndpoint;
    private final String europeanEndpoint;
    private final String asianEndpoint;
    private final HuaweiAdSlotBuilder adSlotBuilder;
    private final HuaweiAppBuilder appBuilder;
    private final HuaweiDeviceBuilder deviceBuilder;
    private final HuaweiNetworkBuilder networkBuilder;
    private final HuaweiAdmBuilder admBuilder;

    public HuaweiAdsBidder(String endpoint,
                           String chineseEndpoint,
                           String russianEndpoint,
                           String europeanEndpoint,
                           String asianEndpoint,
                           String closeSiteSelectionByCountry,
                           JacksonMapper mapper,
                           HuaweiAdSlotBuilder adSlotBuilder,
                           HuaweiAppBuilder appBuilder,
                           HuaweiDeviceBuilder deviceBuilder,
                           HuaweiNetworkBuilder networkBuilder,
                           HuaweiAdmBuilder admBuilder) {

        this.endpointUrl = HttpUtil.validateUrl(endpoint);
        this.closeSiteSelectionByCountry = Objects.requireNonNull(closeSiteSelectionByCountry);
        this.chineseEndpoint = HttpUtil.validateUrl(chineseEndpoint);
        this.russianEndpoint = HttpUtil.validateUrl(russianEndpoint);
        this.europeanEndpoint = HttpUtil.validateUrl(europeanEndpoint);
        this.asianEndpoint = HttpUtil.validateUrl(asianEndpoint);
        this.mapper = Objects.requireNonNull(mapper);
        this.adSlotBuilder = Objects.requireNonNull(adSlotBuilder);
        this.appBuilder = Objects.requireNonNull(appBuilder);
        this.deviceBuilder = Objects.requireNonNull(deviceBuilder);
        this.networkBuilder = Objects.requireNonNull(networkBuilder);
        this.admBuilder = Objects.requireNonNull(admBuilder);
    }

    @Override
    public Result<List<HttpRequest<HuaweiAdsRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final HuaweiAdsRequest huaweiAdsRequest;
        final String countryCode = CountryCodeResolver.resolve(bidRequest).orElse(DEFAULT_COUNTRY_CODE);

        ExtImpHuaweiAds impExt = null;
        try {
            final List<AdSlot30> adSlots = new ArrayList<>();
            for (Imp imp : bidRequest.getImp()) {
                impExt = parseImpExt(imp);
                validate(impExt);
                final AdSlot30 adSlot30 = adSlotBuilder.build(imp, impExt);
                adSlots.add(adSlot30);
            }
            huaweiAdsRequest = makeHuaweiAdsRequest(bidRequest, adSlots, countryCode);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final HttpRequest<HuaweiAdsRequest> httpRequest = HttpRequest.<HuaweiAdsRequest>builder()
                .method(HttpMethod.POST)
                .uri(makeEndpointUrl(countryCode))
                .headers(makeHeaders(bidRequest, impExt))
                .impIds(BidderUtil.impIds(bidRequest))
                .body(mapper.encodeToBytes(huaweiAdsRequest))
                .payload(huaweiAdsRequest)
                .build();
        return Result.withValue(httpRequest);
    }

    private ExtImpHuaweiAds parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), EXT_IMP_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private void validate(ExtImpHuaweiAds impExt) {
        if (impExt == null) {
            throw new PreBidException("ExtImpHuaweiAds is null.");
        }

        if (StringUtils.isBlank(impExt.getSlotId())) {
            throw new PreBidException("ExtImpHuaweiAds: slotid is empty.");
        }

        if (StringUtils.isBlank(impExt.getAdType())) {
            throw new PreBidException("ExtImpHuaweiAds: adtype is empty.");
        }

        if (StringUtils.isBlank(impExt.getPublisherId())) {
            throw new PreBidException("ExtImpHuaweiAds: publisherid is empty.");
        }

        if (StringUtils.isBlank(impExt.getSignKey())) {
            throw new PreBidException("ExtImpHuaweiAds: signkey is empty.");
        }

        if (StringUtils.isBlank(impExt.getKeyId())) {
            throw new PreBidException("ExtImpHuaweiAds: keyid is empty.");
        }
    }

    private HuaweiAdsRequest makeHuaweiAdsRequest(BidRequest bidRequest, List<AdSlot30> adSlots, String countryCode) {
        return HuaweiAdsRequest.builder()
                .clientAdRequestId(bidRequest.getId())
                .multislot(adSlots)
                .version(HUAWEI_ADX_API_VERSION)
                .app(appBuilder.build(bidRequest.getApp(), countryCode))
                .device(deviceBuilder.build(bidRequest.getDevice(), bidRequest.getUser(), countryCode))
                .network(networkBuilder.build(bidRequest.getDevice()))
                //todo: does it make sense to extract making Regs, Geo and Consent?
                .regs(makeRegs(bidRequest.getRegs()))
                .geo(makeGeo(bidRequest.getDevice()))
                .consent(makeConsent(bidRequest.getUser()))
                .build();
    }

    private Regs makeRegs(com.iab.openrtb.request.Regs regs) {
        return Optional.ofNullable(regs)
                .map(com.iab.openrtb.request.Regs::getCoppa)
                .filter(coppa -> coppa >= 0)
                .map(Regs::of)
                .orElse(null);
    }

    private Geo makeGeo(com.iab.openrtb.request.Device device) {
        return Optional.ofNullable(device)
                .map(com.iab.openrtb.request.Device::getGeo)
                .map(geo -> Geo.of(geo.getLon(), geo.getLat(), geo.getAccuracy(), geo.getLastfix()))
                .orElse(null);
    }

    private String makeConsent(User user) {
        return Optional.ofNullable(user).map(User::getExt).map(ExtUser::getConsent).orElse(null);
    }

    private String makeEndpointUrl(String countryCode) {
        if (CLOSE_COUNTRY.equals(closeSiteSelectionByCountry)) {
            return endpointUrl;
        }

        if (CHINESE_COUNTRY_CODES.contains(countryCode)) {
            return chineseEndpoint;
        } else if (RUSSIAN_COUNTRY_CODES.contains(countryCode)) {
            return russianEndpoint;
        } else if (EUROPEAN_COUNTRY_CODES.contains(countryCode)) {
            return europeanEndpoint;
        } else {
            return asianEndpoint;
        }
    }

    private static MultiMap makeHeaders(BidRequest bidRequest, ExtImpHuaweiAds extImp) {
        final MultiMap headers = HttpUtil.headers();
        headers.set(HttpUtil.AUTHORIZATION_HEADER, makeAuthorization(extImp));

        Optional.ofNullable(bidRequest.getDevice())
                .map(Device::getUa)
                .filter(StringUtils::isNotBlank)
                .ifPresent(ua -> headers.set(HttpUtil.USER_AGENT_HEADER, ua));

        return headers;
    }

    private static String makeAuthorization(ExtImpHuaweiAds extImp) {
        final String nonce = String.valueOf(System.currentTimeMillis());
        final String publisherId = extImp.getPublisherId().trim();
        final String apiKey = publisherId + ":ppsadx/getResult:" + extImp.getSignKey().trim();
        final String data = nonce + ":POST:/ppsadx/getResult";
        final String encryptedData = encrypt(data, apiKey);

        return "Digest username=%s,realm=ppsadx/getResult,nonce=%s,response=%s,algorithm=%s,usertype=1,keyid=%s"
                .formatted(
                        publisherId,
                        nonce,
                        encryptedData,
                        HmacAlgorithms.HMAC_SHA_256.getName(),
                        extImp.getKeyId().trim());
    }

    private static String encrypt(String message, String key) {
        try {
            final Mac mac = HmacUtils.getInitializedMac(
                    HmacAlgorithms.HMAC_SHA_256,
                    key.getBytes(StandardCharsets.UTF_8));
            final byte[] hmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(hmac);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<HuaweiAdsRequest> httpCall, BidRequest bidRequest) {
        try {
            final HuaweiAdsResponse response = mapper.decodeValue(
                    httpCall.getResponse().getBody(),
                    HuaweiAdsResponse.class);
            validateRetcode(response.getRetcode(), response.getReason());
            return Result.withValues(extractBids(response, bidRequest));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse("Bad Server Response"));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static void validateRetcode(Integer retcode, String reason) {
        final boolean isBetween200And300Exclusively = retcode > 200 && retcode < 300;
        final boolean isBetween400And599Inclusively = retcode >= 400 && retcode < 600;
        if ((isBetween200And300Exclusively && retcode != 204 && retcode != 206) || isBetween400And599Inclusively) {
            throw new PreBidException("HuaweiAdsResponse retcode: " + retcode + " , reason: " + reason);
        }
    }

    private List<BidderBid> extractBids(HuaweiAdsResponse response, BidRequest bidRequest) {
        if (CollectionUtils.isEmpty(response.getMultiad())) {
            throw new PreBidException("convert huaweiads response to bidder response failed: "
                    + "multiad length is 0, get no ads from huawei side.");
        }

        if (CollectionUtils.isEmpty(bidRequest.getImp())) {
            throw new PreBidException("convert huaweiads response to bidder response failed: "
                    + "openRTBRequest.imp is empty");
        }

        final Map<String, Imp> slotIdToImpMap = new HashMap<>();
        for (Imp imp : bidRequest.getImp()) {
            final ExtImpHuaweiAds impExt = parseImpExt(imp);
            validate(impExt);
            slotIdToImpMap.put(impExt.getSlotId(), imp);
        }

        return makeBidderBids(response, slotIdToImpMap);
    }

    private List<BidderBid> makeBidderBids(HuaweiAdsResponse response, Map<String, Imp> slotIdToImpMap) {
        final List<BidderBid> bids = new ArrayList<>();
        for (Ad30 ad : response.getMultiad()) {
            final Optional<String> optionalImpId = Optional.ofNullable(ad.getSlotId())
                    .map(slotIdToImpMap::get)
                    .map(Imp::getId)
                    .filter(StringUtils::isNotBlank);

            if (optionalImpId.isEmpty() || !Objects.equals(ad.getRetCode(), 200)) {
                continue;
            }

            final Imp imp = slotIdToImpMap.get(ad.getSlotId());
            final BidType mediaType = getMediaType(imp);
            final String currency = getCurrency(ad.getContentList()).orElse(DEFAULT_BID_CURRENCY);

            for (Content content : ad.getContentList()) {
                if (content == null) {
                    throw new PreBidException("extract Adm failed: content is empty");
                }
                final Bid bid = makeBid(ad, imp, mediaType, content);
                bids.add(BidderBid.of(bid, mediaType, currency));
            }
        }
        return bids;
    }

    private static BidType getMediaType(Imp imp) {
        if (imp.getVideo() != null) {
            return BidType.video;
        } else if (imp.getXNative() != null) {
            return BidType.xNative;
        } else if (imp.getAudio() != null) {
            return BidType.audio;
        } else {
            return BidType.banner;
        }
    }

    private static Optional<String> getCurrency(List<Content> contentList) {
        // All currencies should be the same
        return contentList.stream()
                .filter(Objects::nonNull)
                .map(Content::getCur)
                .filter(StringUtils::isNotBlank)
                .findFirst();
    }

    private Bid makeBid(Ad30 ad, Imp imp, BidType mediaType, Content content) {
        final AdsType adType = AdsType.ofTypeNumber(ad.getAdType());
        final HuaweiAdm huaweiAdm = switch (mediaType) {
            case banner -> admBuilder.buildBanner(adType, content);
            case video -> admBuilder.buildVideo(adType, content, imp.getVideo());
            case xNative -> admBuilder.buildNative(adType, content, imp.getXNative());
            default -> throw new PreBidException("no support bidtype: audio");
        };

        return Bid.builder()
                .id(imp.getId())
                .impid(imp.getId())
                // The bidder has already helped us automatically convert the currency price, here only the CNY price is filled in
                .price(content.getPrice())
                .crid(content.getContentId())
                .adm(huaweiAdm.getAdm())
                .w(huaweiAdm.getWidth())
                .h(huaweiAdm.getHeight())
                .adomain(HUAWEIADS_DOMAIN)
                .nurl(getNurl(content.getMonitorList()))
                .build();
    }

    private String getNurl(List<Monitor> monitorList) {
        return Optional.ofNullable(monitorList)
                .flatMap(monitors -> monitors.stream()
                        .filter(monitor -> MonitorEventType.of(monitor.getEventType()) == MonitorEventType.WIN
                                && CollectionUtils.isNotEmpty(monitor.getUrlList()))
                        .flatMap(monitor -> monitor.getUrlList().stream())
                        .findFirst())
                .orElse("");
    }

}
