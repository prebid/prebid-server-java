package org.prebid.server.bidder.adnuntius;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.adnuntius.model.request.AdnuntiusAdUnit;
import org.prebid.server.bidder.adnuntius.model.request.AdnuntiusMetaData;
import org.prebid.server.bidder.adnuntius.model.request.AdnuntiusRequest;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusAd;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusAdsUnit;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusBid;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
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
import java.util.stream.Collectors;

public class AdnuntiusBidder implements Bidder<AdnuntiusRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdnuntius>> ADNUNTIUS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final int SECONDS_IN_MINUTE = 60;
    private static final String TARGET_ID_DELIMITER = "-";
    private static final String DEFAULT_PAGE = "unknown";
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

        for (Imp imp : request.getImp()) {
            final ExtImpAdnuntius extImpAdnuntius;
            try {
                validateImp(imp);
                extImpAdnuntius = parseImpExt(imp);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }

            final String key = StringUtils.stripToEmpty(extImpAdnuntius.getNetwork());
            final String auId = extImpAdnuntius.getAuId();
            networkToAdUnits.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(AdnuntiusAdUnit.of(auId, auId + TARGET_ID_DELIMITER + imp.getId()));
        }

        return Result.withValues(createHttpRequests(networkToAdUnits, request));
    }

    private static void validateImp(Imp imp) {
        if (imp.getBanner() == null) {
            throw new PreBidException(String.format("Fail on Imp.Id=%s: Adnuntius supports only Banner", imp.getId()));
        }
    }

    private ExtImpAdnuntius parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADNUNTIUS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("Unmarshalling error: %s", e.getMessage()));
        }
    }

    private List<HttpRequest<AdnuntiusRequest>> createHttpRequests(Map<String, List<AdnuntiusAdUnit>> networkToAdUnits,
                                                                   BidRequest request) {

        final List<HttpRequest<AdnuntiusRequest>> adnuntiusRequests = new ArrayList<>();

        final AdnuntiusMetaData metaData = createMetaData(request.getUser());
        final String page = extractPage(request.getSite());
        final String uri = createUri(request);
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

    private String createUri(BidRequest bidRequest) {
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
    public Result<List<BidderBid>> makeBids(HttpCall<AdnuntiusRequest> httpCall, BidRequest bidRequest) {
        try {
            final String body = httpCall.getResponse().getBody();
            final AdnuntiusResponse bidResponse = mapper.decodeValue(body, AdnuntiusResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(AdnuntiusResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getAdsUnits())) {
            return Collections.emptyList();
        }

        final List<AdnuntiusAdsUnit> validAdsUnits = bidResponse.getAdsUnits().stream()
                .filter(AdnuntiusBidder::validateAdsUnit).collect(Collectors.toList());

        if (validAdsUnits.isEmpty()) {
            return Collections.emptyList();
        }

        final String currency = extractCurrency(validAdsUnits);
        return validAdsUnits.stream().map(adsUnit -> makeBid(adsUnit, currency)).collect(Collectors.toList());
    }

    private static boolean validateAdsUnit(AdnuntiusAdsUnit adsUnit) {
        final List<AdnuntiusAd> ads = ObjectUtil.getIfNotNull(adsUnit, AdnuntiusAdsUnit::getAds);
        return CollectionUtils.isNotEmpty(ads) && ads.get(0) != null;
    }

    private static String extractCurrency(List<AdnuntiusAdsUnit> adsUnits) {
        final AdnuntiusBid bid = adsUnits.get(adsUnits.size() - 1).getAds().get(0).getBid();
        return ObjectUtil.getIfNotNull(bid, AdnuntiusBid::getCurrency);
    }

    private static BidderBid makeBid(AdnuntiusAdsUnit adsUnit, String currency) {
        final AdnuntiusAd ad = adsUnit.getAds().get(0);
        final String adId = ad.getAdId();
        final Bid bid = Bid.builder()
                .id(adId)
                .impid(extractImpId(adsUnit))
                .w(parseMeasure(ad.getCreativeWidth()))
                .h(parseMeasure(ad.getCreativeHeight()))
                .adid(adId)
                .cid(ad.getLineItemId())
                .crid(ad.getCreativeId())
                .price(extractPrice(ad))
                .adm(adsUnit.getHtml())
                .adomain(extractDomain(ad.getDestinationUrls()))
                .build();

        return BidderBid.of(bid, BidType.banner, currency);
    }

    private static String extractImpId(AdnuntiusAdsUnit adsUnit) {
        final String targetId = adsUnit.getTargetId();
        final String auId = adsUnit.getAuId();

        return ObjectUtils.allNotNull(targetId, auId) && targetId.startsWith(auId + TARGET_ID_DELIMITER)
                ? targetId.substring(auId.length() + TARGET_ID_DELIMITER.length())
                : null;
    }

    private static Integer parseMeasure(String measure) {
        try {
            return Integer.valueOf(measure);
        } catch (NumberFormatException e) {
            throw new PreBidException(String.format("Value of measure: %s can not be parsed.", measure));
        }
    }

    private static BigDecimal extractPrice(AdnuntiusAd ad) {
        final BigDecimal amount = ObjectUtil.getIfNotNull(ad.getBid(), AdnuntiusBid::getAmount);
        return amount != null ? amount.multiply(PRICE_MULTIPLIER) : BigDecimal.ZERO;
    }

    private static List<String> extractDomain(Map<String, String> destinationUrls) {
        return destinationUrls == null ? Collections.emptyList() : destinationUrls.values().stream()
                .filter(Objects::nonNull)
                .map(url -> url.split("/"))
                .filter(splintedUrl -> splintedUrl.length >= 2)
                .map(splintedUrl -> splintedUrl[2].replaceAll("www\\.", ""))
                .collect(Collectors.toList());
    }
}
