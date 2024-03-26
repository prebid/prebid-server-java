package org.prebid.server.bidder.adnuntius;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.adnuntius.model.request.AdnuntiusAdUnit;
import org.prebid.server.bidder.adnuntius.model.request.AdnuntiusMetaData;
import org.prebid.server.bidder.adnuntius.model.request.AdnuntiusRequest;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusAd;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusAdsUnit;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusBid;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusGrossBid;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusNetBid;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusResponse;
import org.prebid.server.bidder.adnuntius.model.util.AdsUnitWithImpId;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.adnuntius.ExtImpAdnuntius;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class AdnuntiusBidder implements Bidder<AdnuntiusRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdnuntius>> ADNUNTIUS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final int SECONDS_IN_MINUTE = 60;
    private static final String TARGET_ID_DELIMITER = "-";
    private static final String DEFAULT_PAGE = "unknown";
    private static final String DEFAULT_NETWORK = "default";
    private static final String URL_NO_COOKIES_PARAMETER = "noCookies";
    private static final BigDecimal PRICE_MULTIPLIER = BigDecimal.valueOf(1000);

    private final String endpointUrl;
    private final Clock clock;
    private final JacksonMapper mapper;

    public AdnuntiusBidder(String endpointUrl, Clock clock, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.clock = Objects.requireNonNull(clock);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<AdnuntiusRequest>>> makeHttpRequests(BidRequest request) {
        final Map<String, List<AdnuntiusAdUnit>> networkToAdUnits = new HashMap<>();
        boolean noCookies = false;

        for (Imp imp : request.getImp()) {
            final ExtImpAdnuntius extImpAdnuntius;
            try {
                validateImp(imp);
                extImpAdnuntius = parseImpExt(imp);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }

            noCookies = resolveIsNoCookies(extImpAdnuntius);
            final String network = resolveNetwork(extImpAdnuntius);

            networkToAdUnits.computeIfAbsent(network, n -> new ArrayList<>())
                    .add(makeAdnuntiusAdUnit(imp, extImpAdnuntius));
        }

        return Result.withValues(createHttpRequests(networkToAdUnits, request, noCookies));
    }

    private static AdnuntiusAdUnit makeAdnuntiusAdUnit(Imp imp, ExtImpAdnuntius extImpAdnuntius) {
        final String auId = extImpAdnuntius.getAuId();
        return AdnuntiusAdUnit.builder()
                .auId(auId)
                .targetId(auId + TARGET_ID_DELIMITER + imp.getId())
                .dimensions(createDimensions(imp))
                .maxDeals(resolveMaxDeals(extImpAdnuntius))
                .build();
    }

    private static List<List<Integer>> createDimensions(Imp imp) {
        final Banner banner = imp.getBanner();

        if (CollectionUtils.isNotEmpty(banner.getFormat())) {
            final List<List<Integer>> formats = new ArrayList<>();
            for (Format format : banner.getFormat()) {
                if (format.getW() != null && format.getH() != null) {
                    formats.add(List.of(format.getW(), format.getH()));
                }
            }
            return formats;
        }

        if (banner.getW() != null && banner.getH() != null) {
            return Collections.singletonList(List.of(banner.getW(), banner.getH()));
        }

        return null;
    }

    private static Integer resolveMaxDeals(ExtImpAdnuntius extImpAdnuntius) {
        if (extImpAdnuntius.getMaxDeals() != null && extImpAdnuntius.getMaxDeals() > 0) {
            return extImpAdnuntius.getMaxDeals();
        }
        return null;
    }

    private static void validateImp(Imp imp) {
        if (imp.getBanner() == null) {
            throw new PreBidException("Fail on Imp.Id=%s: Adnuntius supports only Banner".formatted(imp.getId()));
        }
    }

    private ExtImpAdnuntius parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADNUNTIUS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Unmarshalling error: " + e.getMessage());
        }
    }

    private static boolean resolveIsNoCookies(ExtImpAdnuntius extImpAdnuntius) {
        return Optional.of(extImpAdnuntius)
                .map(ExtImpAdnuntius::getNoCookies)
                .filter(BooleanUtils::isTrue)
                .isPresent();
    }

    private static String resolveNetwork(ExtImpAdnuntius extImpAdnuntius) {
        return Optional.of(extImpAdnuntius)
                .map(ExtImpAdnuntius::getNetwork)
                .filter(StringUtils::isNoneEmpty)
                .orElse(DEFAULT_NETWORK);
    }

    private List<HttpRequest<AdnuntiusRequest>> createHttpRequests(Map<String, List<AdnuntiusAdUnit>> networkToAdUnits,
                                                                   BidRequest request, Boolean noCookies) {

        final List<HttpRequest<AdnuntiusRequest>> adnuntiusRequests = new ArrayList<>();

        final AdnuntiusMetaData metaData = createMetaData(request.getUser());
        final String page = extractPage(request.getSite());
        final String uri = createUri(request, noCookies);
        final Device device = request.getDevice();

        for (List<AdnuntiusAdUnit> adUnits : networkToAdUnits.values()) {
            final AdnuntiusRequest adnuntiusRequest = AdnuntiusRequest.of(adUnits, metaData, page);
            adnuntiusRequests.add(createHttpRequest(adnuntiusRequest, uri, device));
        }

        return adnuntiusRequests;
    }

    private static AdnuntiusMetaData createMetaData(User user) {
        final String userId = ObjectUtil.getIfNotNull(user, User::getId);
        return StringUtils.isNotBlank(userId) ? AdnuntiusMetaData.of(userId) : null;
    }

    private static String extractPage(Site site) {
        return StringUtils.defaultIfBlank(ObjectUtil.getIfNotNull(site, Site::getPage), DEFAULT_PAGE);
    }

    private String createUri(BidRequest bidRequest, Boolean noCookies) {
        try {
            final URIBuilder uriBuilder = new URIBuilder(endpointUrl)
                    .addParameter("format", "json")
                    .addParameter("tzo", getTimeZoneOffset());

            final String gdpr = extractGdpr(bidRequest.getRegs());
            final String consent = extractConsent(bidRequest.getUser());
            if (StringUtils.isNoneEmpty(gdpr, consent)) {
                uriBuilder.addParameter("gdpr", gdpr);
                uriBuilder.addParameter("consentString", consent);
            }

            if (noCookies || extractNoCookies(bidRequest.getDevice())) {
                uriBuilder.addParameter(URL_NO_COOKIES_PARAMETER, "true");
            }

            return uriBuilder.build().toString();
        } catch (URISyntaxException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private String getTimeZoneOffset() {
        return String.valueOf(-OffsetDateTime.now(clock).getOffset().getTotalSeconds() / SECONDS_IN_MINUTE);
    }

    private static String extractGdpr(Regs regs) {
        final Integer gdpr = ObjectUtil.getIfNotNull(ObjectUtil.getIfNotNull(regs, Regs::getExt), ExtRegs::getGdpr);
        return gdpr != null ? gdpr.toString() : null;
    }

    private static String extractConsent(User user) {
        return ObjectUtil.getIfNotNull(ObjectUtil.getIfNotNull(user, User::getExt), ExtUser::getConsent);
    }

    private static Boolean extractNoCookies(Device device) {
        return Optional.ofNullable(device)
                .map(Device::getExt)
                .map(FlexibleExtension::getProperties)
                .map(properties -> properties.get(URL_NO_COOKIES_PARAMETER))
                .filter(JsonNode::isBoolean)
                .map(JsonNode::asBoolean)
                .orElse(false);
    }

    private HttpRequest<AdnuntiusRequest> createHttpRequest(AdnuntiusRequest adnuntiusRequest, String uri,
                                                            Device device) {
        return HttpRequest.<AdnuntiusRequest>builder()
                .method(HttpMethod.POST)
                .headers(getHeaders(device))
                .uri(uri)
                .body(mapper.encodeToBytes(adnuntiusRequest))
                .payload(adnuntiusRequest)
                .build();
    }

    private MultiMap getHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers();

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<AdnuntiusRequest> httpCall, BidRequest bidRequest) {
        try {
            final String body = httpCall.getResponse().getBody();
            final AdnuntiusResponse adnuntiusResponse = mapper.decodeValue(body, AdnuntiusResponse.class);
            return Result.withValues(extractBids(bidRequest, adnuntiusResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, AdnuntiusResponse adnuntiusResponse) {
        if (adnuntiusResponse == null || CollectionUtils.isEmpty(adnuntiusResponse.getAdsUnits())) {
            return Collections.emptyList();
        }

        final List<AdnuntiusAdsUnit> adsUnits = adnuntiusResponse.getAdsUnits();
        final List<Imp> imps = bidRequest.getImp();
        if (adsUnits.size() > imps.size()) {
            throw new PreBidException("Impressions count is less then ads units count.");
        }

        final List<AdsUnitWithImpId> validAdsUnitToImp = IntStream.range(0, adsUnits.size())
                .mapToObj(i -> AdsUnitWithImpId.of(adsUnits.get(i), imps.get(i), parseImpExt(imps.get(i))))
                .filter(adsUnitWithImpId -> validateAdsUnit(adsUnitWithImpId.getAdsUnit()))
                .toList();

        if (validAdsUnitToImp.isEmpty()) {
            return Collections.emptyList();
        }

        final String currency = extractCurrency(validAdsUnitToImp);
        final Stream<BidderBid> generalBids = validAdsUnitToImp.stream()
                .map(adsUnitWithImpId -> makeGeneralBid(adsUnitWithImpId, currency));

        final Stream<BidderBid> dealBids = validAdsUnitToImp.stream()
                .filter(adsUnitWithImpId -> CollectionUtils.isNotEmpty(adsUnitWithImpId.getAdsUnit().getDeals()))
                .map(adsUnitWithImpId -> makeDealsBid(adsUnitWithImpId, currency))
                .filter(Objects::nonNull);

        return Stream.concat(generalBids, dealBids).toList();
    }

    private static boolean validateAdsUnit(AdnuntiusAdsUnit adsUnit) {
        final List<AdnuntiusAd> ads = ObjectUtil.getIfNotNull(adsUnit, AdnuntiusAdsUnit::getAds);
        return CollectionUtils.isNotEmpty(ads) && ads.get(0) != null;
    }

    private static String extractCurrency(List<AdsUnitWithImpId> adsUnits) {
        final AdnuntiusBid bid = adsUnits.get(adsUnits.size() - 1).getAdsUnit().getAds().get(0).getBid();
        return ObjectUtil.getIfNotNull(bid, AdnuntiusBid::getCurrency);
    }

    private BidderBid makeGeneralBid(AdsUnitWithImpId adsUnitWithImpId, String currency) {
        final AdnuntiusAdsUnit adsUnit = adsUnitWithImpId.getAdsUnit();
        final AdnuntiusAd ad = adsUnit.getAds().get(0);
        final Bid bid = createBid(adsUnit, adsUnitWithImpId.getImp(), adsUnitWithImpId.getExtImpAdnuntius(), ad);
        return BidderBid.of(bid, BidType.banner, currency);
    }

    private BidderBid makeDealsBid(AdsUnitWithImpId adsUnitWithImpId, String currency) {
        final AdnuntiusAdsUnit adsUnit = adsUnitWithImpId.getAdsUnit();
        return adsUnit.getDeals().stream()
                .map(adnuntiusAd ->
                        createBid(adsUnit,
                                adsUnitWithImpId.getImp(),
                                adsUnitWithImpId.getExtImpAdnuntius(),
                                adnuntiusAd))
                .map(bid -> BidderBid.of(bid, BidType.banner, currency))
                .findAny()
                .orElse(null);
    }

    private static Bid createBid(AdnuntiusAdsUnit adsUnit, Imp imp, ExtImpAdnuntius extImpAdnuntius, AdnuntiusAd ad) {
        final String adId = ad.getAdId();
        return Bid.builder()
                .id(adId)
                .impid(imp.getId())
                .w(parseMeasure(ad.getCreativeWidth()))
                .h(parseMeasure(ad.getCreativeHeight()))
                .adid(adId)
                .cid(ad.getLineItemId())
                .crid(ad.getCreativeId())
                .price(resolvePrice(ad, extImpAdnuntius.getBidType()))
                .dealid(ad.getDealId())
                .adm(adsUnit.getHtml())
                .adomain(extractDomain(ad.getDestinationUrls()))
                .build();
    }

    private static Integer parseMeasure(String measure) {
        try {
            return Integer.valueOf(measure);
        } catch (NumberFormatException e) {
            throw new PreBidException("Value of measure: %s can not be parsed.".formatted(measure));
        }
    }

    private static BigDecimal resolvePrice(AdnuntiusAd ad, String bidType) {
        BigDecimal amount = null;

        if (StringUtils.isEmpty(bidType)) {
            amount = ObjectUtil.getIfNotNull(ad.getBid(), AdnuntiusBid::getAmount);
        }
        if (StringUtils.endsWithIgnoreCase(bidType, "net")) {
            amount = ObjectUtil.getIfNotNull(ad.getAdnuntiusNetBid(), AdnuntiusNetBid::getAmount);
        }
        if (StringUtils.endsWithIgnoreCase(bidType, "gross")) {
            amount = ObjectUtil.getIfNotNull(ad.getAdnuntiusGrossBid(), AdnuntiusGrossBid::getAmount);
        }

        return amount != null ? amount.multiply(PRICE_MULTIPLIER) : BigDecimal.ZERO;
    }

    private static List<String> extractDomain(Map<String, String> destinationUrls) {
        return destinationUrls == null ? Collections.emptyList() : destinationUrls.values().stream()
                .filter(Objects::nonNull)
                .map(url -> url.split("/"))
                .filter(splintedUrl -> splintedUrl.length >= 2)
                .map(splintedUrl -> splintedUrl[2].replaceAll("www\\.", ""))
                .toList();
    }
}
