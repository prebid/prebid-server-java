package org.prebid.server.bidder.adnuntius;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AdnuntiusBidder implements Bidder<AdnuntiusRequest> {

    private static final int SECONDS_IN_MINUTE = 60;
    private static final String TARGET_ID_DELIMITER = "-";
    private static final String DEFAULT_PAGE = "unknown";
    private static final BigDecimal PRICE_MULTIPLIER = BigDecimal.valueOf(1000);
    private static final TypeReference<ExtPrebid<?, ExtImpAdnuntius>> ADNUNTIUS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final ZoneId zoneId;
    private final JacksonMapper mapper;

    public AdnuntiusBidder(String endpointUrl, ZoneId zoneId, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.zoneId = Objects.requireNonNull(zoneId);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<AdnuntiusRequest>>> makeHttpRequests(BidRequest request) {
        final Map<String, List<AdnuntiusAdUnit>> networkToAdUnits = new HashMap<>();
        final List<HttpRequest<AdnuntiusRequest>> adnuntiusRequests = new ArrayList<>();

        try {
            for (Imp imp : request.getImp()) {
                validateImp(imp);

                final ExtImpAdnuntius extImpAdnuntius = parseImpExt(imp);

                final String key = StringUtils.stripToEmpty(extImpAdnuntius.getNetwork());
                final String auId = extImpAdnuntius.getAuId();
                networkToAdUnits.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(AdnuntiusAdUnit.of(auId, auId + TARGET_ID_DELIMITER + imp.getId()));
            }
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final String page = extractPage(request.getSite());
        final String userId = ObjectUtil.getIfNotNull(request.getUser(), User::getId);
        final String uri = createUri(request);

        for (List<AdnuntiusAdUnit> adUnits : networkToAdUnits.values()) {
            final AdnuntiusRequest adnuntiusRequest = AdnuntiusRequest.of(adUnits, AdnuntiusMetaData.of(userId), page);
            adnuntiusRequests.add(createHttpRequest(adnuntiusRequest, uri));
        }

        return Result.withValues(adnuntiusRequests);
    }

    private static void validateImp(Imp imp) throws PreBidException {
        if (imp.getBanner() == null) {
            throw new PreBidException(String.format("Fail on Imp.Id=%s: Adnuntius supports only Banner", imp.getId()));
        }
    }

    private ExtImpAdnuntius parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADNUNTIUS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("Unmarshalling error: %s", e.getMessage()));
        }
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
            if (gdpr != null && StringUtils.isNotBlank(consent)) {
                uriBuilder.addParameter("gdpr", gdpr);
                uriBuilder.addParameter("consentString", consent);
            }

            return uriBuilder.build().toString();
        } catch (URISyntaxException e) { // shouldn't really happen
            throw new PreBidException(e.getMessage());
        }
    }

    private String getTimeZoneOffset() {
        return String.valueOf(-OffsetDateTime.now(zoneId).getOffset().getTotalSeconds() / SECONDS_IN_MINUTE);
    }

    private static String extractGdpr(Regs regs) {
        final Integer gdpr = ObjectUtil.getIfNotNull(ObjectUtil.getIfNotNull(regs, Regs::getExt), ExtRegs::getGdpr);
        return gdpr != null ? gdpr.toString() : null;
    }

    private static String extractConsent(User user) {
        return ObjectUtil.getIfNotNull(ObjectUtil.getIfNotNull(user, User::getExt), ExtUser::getConsent);
    }

    private HttpRequest<AdnuntiusRequest> createHttpRequest(AdnuntiusRequest adnuntiusRequest, String uri) {
        return HttpRequest.<AdnuntiusRequest>builder()
                .method(HttpMethod.POST)
                .headers(HttpUtil.headers())
                .uri(uri)
                .body(mapper.encodeToBytes(adnuntiusRequest))
                .payload(adnuntiusRequest)
                .build();
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

    private static List<BidderBid> extractBids(AdnuntiusResponse bidResponse) throws PreBidException {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getAdUnits())) {
            return Collections.emptyList();
        }

        final List<AdnuntiusAdsUnit> adsUnits = bidResponse.getAdUnits().stream()
                .filter(AdnuntiusBidder::adsUnitFilter).collect(Collectors.toList());

        if (adsUnits.isEmpty()) {
            return Collections.emptyList();
        }

        final String currency = extractCurrency(adsUnits);
        return adsUnits.stream()
                .map(adsUnit -> makeBid(adsUnit.getAds().get(0), extractImpId(adsUnit), adsUnit.getHtml(), currency))
                .collect(Collectors.toList());
    }

    private static boolean adsUnitFilter(AdnuntiusAdsUnit adsUnit) {
        if (adsUnit == null) {
            return false;
        }

        final List<AdnuntiusAd> ads = adsUnit.getAds();
        return CollectionUtils.isNotEmpty(ads) && ads.get(0) != null;
    }

    private static String extractCurrency(List<AdnuntiusAdsUnit> adsUnits) {
        final AdnuntiusBid bid = adsUnits.get(adsUnits.size() - 1).getAds().get(0).getBid();
        return ObjectUtil.getIfNotNull(bid, AdnuntiusBid::getCurrency);
    }

    private static String extractImpId(AdnuntiusAdsUnit adsUnit) {
        final String targetId = adsUnit.getTargetId();
        final String auId = adsUnit.getAuId();

        if (targetId == null || auId == null) {
            return null;
        }

        if (!targetId.startsWith(auId + TARGET_ID_DELIMITER)) {
            return null;
        }

        return targetId.substring(auId.length() + TARGET_ID_DELIMITER.length());
    }

    private static BidderBid makeBid(AdnuntiusAd ad, String impId, String html, String currency)
            throws PreBidException {

        final String adId = ad.getAdId();
        final Bid bid = Bid.builder()
                .id(adId)
                .impid(impId)
                .w(extractMeasure(ad, AdnuntiusAd::getCreativeWidth))
                .h(extractMeasure(ad, AdnuntiusAd::getCreativeHeight))
                .adid(adId)
                .cid(ad.getLineItemId())
                .crid(ad.getCreativeId())
                .price(extractPrice(ad))
                .adm(html)
                .adomain(extractDomain(ad))
                .build();

        return BidderBid.of(bid, BidType.banner, currency);
    }

    private static Integer extractMeasure(AdnuntiusAd ad, Function<AdnuntiusAd, String> measureExtractor)
            throws PreBidException {

        final String measure = measureExtractor.apply(ad);
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

    private static List<String> extractDomain(AdnuntiusAd ad) {
        return ad.getDestinationUrls().values().stream()
                .map(url -> url.split("/")[3].replaceAll("www\\.", ""))
                .collect(Collectors.toList());
    }
}
