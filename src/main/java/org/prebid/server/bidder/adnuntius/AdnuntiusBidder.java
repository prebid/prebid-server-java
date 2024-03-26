package org.prebid.server.bidder.adnuntius;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
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
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
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
import java.util.function.Function;
import java.util.stream.Collectors;

public class AdnuntiusBidder implements Bidder<AdnuntiusRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdnuntius>> ADNUNTIUS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final int SECONDS_IN_MINUTE = 60;
    private static final String TARGET_ID_DELIMITER = "-";
    private static final String DEFAULT_PAGE = "unknown";
    private static final String DEFAULT_NETWORK = "default";
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

            noCookies = noCookies || resolveIsNoCookies(extImpAdnuntius);
            final String network = resolveNetwork(extImpAdnuntius);

            networkToAdUnits.computeIfAbsent(network, ignored -> new ArrayList<>())
                    .add(makeAdUnit(imp, extImpAdnuntius));
        }

        return Result.withValues(createHttpRequests(networkToAdUnits, request, noCookies));
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
                .map(BooleanUtils::isTrue)
                .orElse(false);
    }

    private static String resolveNetwork(ExtImpAdnuntius extImpAdnuntius) {
        return Optional.of(extImpAdnuntius)
                .map(ExtImpAdnuntius::getNetwork)
                .filter(StringUtils::isNotEmpty)
                .orElse(DEFAULT_NETWORK);
    }

    private static AdnuntiusAdUnit makeAdUnit(Imp imp, ExtImpAdnuntius extImpAdnuntius) {
        final String auId = StringUtils.defaultString(extImpAdnuntius.getAuId());
        return AdnuntiusAdUnit.builder()
                .auId(auId)
                .targetId(targetId(auId, imp.getId()))
                .dimensions(createDimensions(imp.getBanner()))
                .maxDeals(resolveMaxDeals(extImpAdnuntius))
                .build();
    }

    private static String targetId(String auId, String impId) {
        return auId + TARGET_ID_DELIMITER + impId;
    }

    private static List<List<Integer>> createDimensions(Banner banner) {
        final List<List<Integer>> formats = new ArrayList<>();

        final List<Format> bannerFormat = ListUtils.emptyIfNull(banner.getFormat());
        for (Format format : bannerFormat) {
            final Integer w = format.getW();
            final Integer h = format.getH();
            if (w != null && h != null) {
                formats.add(List.of(w, h));
            }
        }
        if (!formats.isEmpty()) {
            return formats;
        }

        final Integer w = banner.getW();
        final Integer h = banner.getH();
        if (w != null && h != null) {
            formats.add(List.of(w, h));
        }

        return formats.isEmpty() ? null : formats;
    }

    private static Integer resolveMaxDeals(ExtImpAdnuntius extImpAdnuntius) {
        final Integer maxDeals = extImpAdnuntius.getMaxDeals();
        return maxDeals != null && maxDeals > 0 ? maxDeals : null;
    }

    private List<HttpRequest<AdnuntiusRequest>> createHttpRequests(Map<String, List<AdnuntiusAdUnit>> networkToAdUnits,
                                                                   BidRequest request,
                                                                   boolean noCookies) {

        final Site site = request.getSite();

        final String uri = createUri(request, noCookies);
        final String page = extractPage(site);
        final ObjectNode data = extractData(site);
        final AdnuntiusMetaData metaData = createMetaData(request.getUser());

        final List<HttpRequest<AdnuntiusRequest>> adnuntiusRequests = new ArrayList<>();

        for (List<AdnuntiusAdUnit> adUnits : networkToAdUnits.values()) {
            final AdnuntiusRequest adnuntiusRequest = AdnuntiusRequest.builder()
                    .adUnits(adUnits)
                    .context(page)
                    .keyValue(data)
                    .metaData(metaData)
                    .build();
            adnuntiusRequests.add(createHttpRequest(adnuntiusRequest, uri, request.getDevice()));
        }

        return adnuntiusRequests;
    }

    private String createUri(BidRequest bidRequest, Boolean noCookies) {
        try {
            final URIBuilder uriBuilder = new URIBuilder(endpointUrl)
                    .addParameter("format", "json")
                    .addParameter("tzo", getTimeZoneOffset());

            final String gdpr = extractGdpr(bidRequest.getRegs());
            if (StringUtils.isNotEmpty(gdpr)) {
                uriBuilder.addParameter("gdpr", gdpr);
            }

            final String consent = extractConsent(bidRequest.getUser());
            if (StringUtils.isNotEmpty(consent)) {
                uriBuilder.addParameter("consentString", consent);
            }

            if (noCookies || extractNoCookies(bidRequest.getDevice())) {
                uriBuilder.addParameter("noCookies", "true");
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
        return Optional.ofNullable(regs)
                .map(Regs::getExt)
                .map(ExtRegs::getGdpr)
                .map(Objects::toString)
                .orElse(null);
    }

    private static String extractConsent(User user) {
        return Optional.ofNullable(user)
                .map(User::getExt)
                .map(ExtUser::getConsent)
                .orElse(null);
    }

    private static boolean extractNoCookies(Device device) {
        return Optional.ofNullable(device)
                .map(Device::getExt)
                .map(FlexibleExtension::getProperties)
                .map(properties -> properties.get("noCookies"))
                .filter(JsonNode::isBoolean)
                .map(JsonNode::booleanValue)
                .orElse(false);
    }

    private static String extractPage(Site site) {
        return Optional.ofNullable(site)
                .map(Site::getPage)
                .filter(StringUtils::isNotEmpty)
                .orElse(DEFAULT_PAGE);
    }

    private static ObjectNode extractData(Site site) {
        return Optional.ofNullable(site)
                .map(Site::getExt)
                .map(ExtSite::getData)
                .orElse(null);
    }

    private static AdnuntiusMetaData createMetaData(User user) {
        final Optional<User> userOptional = Optional.ofNullable(user);
        return userOptional
                .map(User::getId)
                .filter(StringUtils::isNotEmpty)
                .or(() -> userOptional
                        .map(User::getExt)
                        .map(ExtUser::getEids)
                        .filter(CollectionUtils::isNotEmpty)
                        .map(eids -> eids.get(0))
                        .map(Eid::getUids)
                        .filter(CollectionUtils::isNotEmpty)
                        .map(uids -> uids.get(0))
                        .map(Uid::getId))
                .map(AdnuntiusMetaData::of)
                .orElse(null);
    }

    private HttpRequest<AdnuntiusRequest> createHttpRequest(AdnuntiusRequest adnuntiusRequest,
                                                            String uri,
                                                            Device device) {

        return HttpRequest.<AdnuntiusRequest>builder()
                .method(HttpMethod.POST)
                .headers(headers(device))
                .uri(uri)
                .body(mapper.encodeToBytes(adnuntiusRequest))
                .payload(adnuntiusRequest)
                .build();
    }

    private MultiMap headers(Device device) {
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

        final Map<String, AdnuntiusAdsUnit> targetIdToAdsUnit = adnuntiusResponse.getAdsUnits().stream()
                .filter(AdnuntiusBidder::validateAdsUnit)
                .collect(Collectors.toMap(AdnuntiusAdsUnit::getTargetId, Function.identity()));

        String currency = null;
        final List<Bid> bids = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            final ExtImpAdnuntius extImpAdnuntius = parseImpExt(imp);
            final String targetId = targetId(StringUtils.defaultString(extImpAdnuntius.getAuId()), imp.getId());

            final AdnuntiusAdsUnit adsUnit = targetIdToAdsUnit.get(targetId);
            if (adsUnit == null) {
                continue;
            }

            final AdnuntiusAd ad = adsUnit.getAds().get(0);
            final String impId = imp.getId();
            final String bidType = extImpAdnuntius.getBidType();
            currency = ObjectUtil.getIfNotNull(ad.getBid(), AdnuntiusBid::getCurrency);

            bids.add(createBid(ad, adsUnit.getHtml(), impId, bidType));

            for (AdnuntiusAd deal : ListUtils.emptyIfNull(adsUnit.getDeals())) {
                bids.add(createBid(deal, deal.getHtml(), impId, bidType));
            }
        }

        final String lastCurrency = currency;
        return bids.stream()
                .map(bid -> BidderBid.of(bid, BidType.banner, lastCurrency))
                .toList();
    }

    private static boolean validateAdsUnit(AdnuntiusAdsUnit adsUnit) {
        final List<AdnuntiusAd> ads = adsUnit != null ? adsUnit.getAds() : null;
        return CollectionUtils.isNotEmpty(ads) && ads.get(0) != null;
    }

    private static Bid createBid(AdnuntiusAd ad, String adm, String impId, String bidType) {
        final String adId = ad.getAdId();

        return Bid.builder()
                .id(adId)
                .impid(impId)
                .w(parseMeasure(ad.getCreativeWidth()))
                .h(parseMeasure(ad.getCreativeHeight()))
                .adid(adId)
                .dealid(ad.getDealId())
                .cid(ad.getLineItemId())
                .crid(ad.getCreativeId())
                .price(resolvePrice(ad, bidType))
                .adm(adm)
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
            amount = ObjectUtil.getIfNotNull(ad.getNetBid(), AdnuntiusNetBid::getAmount);
        }
        if (StringUtils.endsWithIgnoreCase(bidType, "gross")) {
            amount = ObjectUtil.getIfNotNull(ad.getGrossBid(), AdnuntiusGrossBid::getAmount);
        }

        return amount != null ? amount.multiply(PRICE_MULTIPLIER) : BigDecimal.ZERO;
    }

    private static List<String> extractDomain(Map<String, String> destinationUrls) {
        return destinationUrls == null ? Collections.emptyList() : destinationUrls.values().stream()
                .filter(Objects::nonNull)
                .map(url -> url.split("/"))
                .filter(splintedUrl -> splintedUrl.length >= 2)
                .map(splintedUrl -> StringUtils.replace(splintedUrl[2], "www.", ""))
                .toList();
    }
}
