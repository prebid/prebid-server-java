package org.prebid.server.bidder.adnuntius;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.adnuntius.model.request.AdnuntiusMetaData;
import org.prebid.server.bidder.adnuntius.model.request.AdnuntiusNativeRequest;
import org.prebid.server.bidder.adnuntius.model.request.AdnuntiusRequest;
import org.prebid.server.bidder.adnuntius.model.request.AdnuntiusRequestAdUnit;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusAd;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusAdUnit;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusAdvertiser;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusBid;
import org.prebid.server.bidder.adnuntius.model.response.AdnuntiusBidExt;
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
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.adnuntius.ExtImpAdnuntius;
import org.prebid.server.proto.openrtb.ext.request.adnuntius.ExtImpAdnuntiusTargeting;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidDsa;
import org.prebid.server.util.BidderUtil;
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

public class AdnuntiusBidder implements Bidder<AdnuntiusRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdnuntius>> ADNUNTIUS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final int SECONDS_IN_MINUTE = 60;
    private static final String DEFAULT_PAGE = "unknown";
    private static final String DEFAULT_NETWORK = "default";
    private static final BigDecimal PRICE_MULTIPLIER = BigDecimal.valueOf(1000);
    private static final int BANNER_MTYPE = 1;
    private static final int NATIVE_MTYPE = 4;

    private final String endpointUrl;
    private final String euEndpoint;
    private final Clock clock;
    private final JacksonMapper mapper;

    public AdnuntiusBidder(String endpointUrl,
                           String euEndpoint,
                           Clock clock,
                           JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.euEndpoint = euEndpoint == null ? null : HttpUtil.validateUrl(euEndpoint);
        this.clock = Objects.requireNonNull(clock);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<AdnuntiusRequest>>> makeHttpRequests(BidRequest request) {
        try {
            final Map<String, List<AdnuntiusRequestAdUnit>> networkToAdUnits = new HashMap<>();
            boolean noCookies = false;
            for (Imp imp : request.getImp()) {
                validateImp(imp);
                final ExtImpAdnuntius extImpAdnuntius = parseImpExt(imp);
                noCookies = noCookies || resolveIsNoCookies(extImpAdnuntius);
                final String network = resolveNetwork(extImpAdnuntius);

                final List<AdnuntiusRequestAdUnit> adUnits = networkToAdUnits.computeIfAbsent(
                        network,
                        ignored -> new ArrayList<>());

                if (imp.getBanner() != null) {
                    adUnits.add(makeBannerAdUnit(imp, extImpAdnuntius));
                }

                if (imp.getXNative() != null) {
                    adUnits.add(makeNativeAdUnit(imp, extImpAdnuntius));
                }
            }

            return Result.withValues(createHttpRequests(networkToAdUnits, request, noCookies));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
    }

    private static void validateImp(Imp imp) {
        if (imp.getBanner() == null && imp.getXNative() == null) {
            throw new PreBidException("ignoring imp id=%s: Adnuntius supports only native and banner"
                    .formatted(imp.getId()));
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

    private static AdnuntiusRequestAdUnit makeBannerAdUnit(Imp imp, ExtImpAdnuntius extImpAdnuntius) {
        return makeAdUnitBuilder(imp, extImpAdnuntius, "banner")
                .dimensions(createDimensions(imp.getBanner()))
                .build();
    }

    private static String targetId(String auId, String impId, String bidType) {
        return "%s-%s:%s".formatted(auId, impId, bidType);
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

    private AdnuntiusRequestAdUnit makeNativeAdUnit(Imp imp, ExtImpAdnuntius extImpAdnuntius) {
        return makeAdUnitBuilder(imp, extImpAdnuntius, "native")
                .nativeRequest(AdnuntiusNativeRequest.of(parseNativeRequest(imp)))
                .adType("NATIVE")
                .build();
    }

    private ObjectNode parseNativeRequest(Imp imp) {
        try {
            return mapper.mapper().readValue(imp.getXNative().getRequest(), ObjectNode.class);
        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw new PreBidException("Unmarshalling Native error " + e.getMessage());
        }
    }

    private static AdnuntiusRequestAdUnit.AdnuntiusRequestAdUnitBuilder makeAdUnitBuilder(
            Imp imp,
            ExtImpAdnuntius extImpAdnuntius,
            String bidType) {

        final String auId = extImpAdnuntius.getAuId();
        final ExtImpAdnuntiusTargeting targeting = ObjectUtils.defaultIfNull(
                extImpAdnuntius.getTargeting(),
                ExtImpAdnuntiusTargeting.builder().build());
        return AdnuntiusRequestAdUnit.builder()
                .auId(auId)
                .targetId(targetId(auId, imp.getId(), bidType))
                .maxDeals(resolveMaxDeals(extImpAdnuntius))
                .category(targeting.getCategory())
                .segments(targeting.getSegments())
                .keywords(targeting.getKeywords())
                .keyValues(targeting.getKeyValues())
                .adUnitMatchingLabel(targeting.getAdUnitMatchingLabel());
    }

    private static Integer resolveMaxDeals(ExtImpAdnuntius extImpAdnuntius) {
        final Integer maxDeals = extImpAdnuntius.getMaxDeals();
        return maxDeals != null && maxDeals > 0 ? maxDeals : null;
    }

    private List<HttpRequest<AdnuntiusRequest>> createHttpRequests(
            Map<String, List<AdnuntiusRequestAdUnit>> networkToAdUnits,
            BidRequest request,
            boolean noCookies) {

        final Site site = request.getSite();

        final String uri = makeEndpoint(request, noCookies);
        final String page = extractPage(site);
        final ObjectNode data = extractData(site);
        final AdnuntiusMetaData metaData = createMetaData(request.getUser());

        final List<HttpRequest<AdnuntiusRequest>> adnuntiusRequests = new ArrayList<>();

        for (List<AdnuntiusRequestAdUnit> adUnits : networkToAdUnits.values()) {
            final AdnuntiusRequest adnuntiusRequest = AdnuntiusRequest.builder()
                    .adUnits(adUnits)
                    .context(page)
                    .keyValue(data)
                    .metaData(metaData)
                    .build();
            adnuntiusRequests.add(createHttpRequest(request, adnuntiusRequest, uri, request.getDevice()));
        }

        return adnuntiusRequests;
    }

    private String makeEndpoint(BidRequest bidRequest, Boolean noCookies) {
        try {
            final String gdpr = extractGdpr(bidRequest.getRegs());
            final String url = StringUtils.isNotBlank(gdpr) ? euEndpoint : endpointUrl;

            if (url == null) {
                throw new PreBidException("an EU endpoint is required but invalid");
            }

            final URIBuilder uriBuilder = new URIBuilder(url)
                    .addParameter("format", "prebidServer")
                    .addParameter("tzo", getTimeZoneOffset());

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
        } catch (URISyntaxException | IllegalArgumentException e) {
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
                        .map(List::getFirst)
                        .map(Eid::getUids)
                        .filter(CollectionUtils::isNotEmpty)
                        .map(List::getFirst)
                        .map(Uid::getId))
                .map(AdnuntiusMetaData::of)
                .orElse(null);
    }

    private HttpRequest<AdnuntiusRequest> createHttpRequest(BidRequest request,
                                                            AdnuntiusRequest adnuntiusRequest,
                                                            String uri,
                                                            Device device) {

        return HttpRequest.<AdnuntiusRequest>builder()
                .method(HttpMethod.POST)
                .headers(headers(device))
                .uri(uri)
                .body(mapper.encodeToBytes(adnuntiusRequest))
                .payload(adnuntiusRequest)
                .impIds(BidderUtil.impIds(request))
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
        } catch (EncodeException | DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static Map<String, AdnuntiusAdUnit> parseAdUnits(AdnuntiusResponse adnuntiusResponse) {
        final Map<String, AdnuntiusAdUnit> targetIdToAdsUnit = new HashMap<>();
        for (AdnuntiusAdUnit adUnit : adnuntiusResponse.getAdUnits()) {
            if (isValid(adUnit)) {
                final String targetId = extractTargetId(adUnit.getTargetId());
                final AdnuntiusAdUnit existingAdUnit = targetIdToAdsUnit.get(targetId);
                if (existingAdUnit == null || getBidAmount(adUnit).compareTo(getBidAmount(existingAdUnit)) >= 0) {
                    targetIdToAdsUnit.put(targetId, adUnit);
                }
            }
        }

        return targetIdToAdsUnit;
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, AdnuntiusResponse adnuntiusResponse) {
        if (adnuntiusResponse == null || CollectionUtils.isEmpty(adnuntiusResponse.getAdUnits())) {
            return Collections.emptyList();
        }

        final Map<String, AdnuntiusAdUnit> targetIdToAdsUnit = parseAdUnits(adnuntiusResponse);

        String currency = null;
        final List<Bid> bids = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            final ExtImpAdnuntius extImpAdnuntius = parseImpExt(imp);
            final String targetId = targetIdForBids(extImpAdnuntius.getAuId(), imp.getId());

            final AdnuntiusAdUnit adUnit = targetIdToAdsUnit.get(targetId);
            if (adUnit == null) {
                continue;
            }

            final AdnuntiusAd ad = adUnit.getAds().getFirst();
            final String impId = imp.getId();
            final String bidType = extImpAdnuntius.getBidType();

            currency = ObjectUtil.getIfNotNull(ad.getBid(), AdnuntiusBid::getCurrency);
            final JsonNode nativeRequest = Optional.ofNullable(adUnit.getNativeJson())
                    .map(AdnuntiusNativeRequest::getOrtb)
                    .orElse(null);
            final int mType = nativeRequest == null ? BANNER_MTYPE : NATIVE_MTYPE;
            final String html = nativeRequest == null ? adUnit.getHtml() : mapper.encodeToString(nativeRequest);

            bids.add(createBid(ad, bidRequest, html, impId, bidType, mType));

            for (AdnuntiusAd deal : ListUtils.emptyIfNull(adUnit.getDeals())) {
                bids.add(createBid(deal, bidRequest, deal.getHtml(), impId, bidType, BANNER_MTYPE));
            }
        }

        final String lastCurrency = currency;
        return bids.stream()
                .map(bid -> BidderBid.of(
                        bid,
                        bid.getMtype() == BANNER_MTYPE ? BidType.banner : BidType.xNative,
                        lastCurrency))
                .toList();
    }

    private static BigDecimal getBidAmount(AdnuntiusAdUnit adUnit) {
        return adUnit.getAds().getFirst().getBid().getAmount();
    }

    private static boolean isValid(AdnuntiusAdUnit adsUnit) {
        if (adsUnit == null) {
            return false;
        }

        final String targetId = extractTargetId(adsUnit.getTargetId());
        final int matchedCount = ObjectUtils.defaultIfNull(adsUnit.getMatchedAdCount(), 0);
        final List<AdnuntiusAd> ads = adsUnit.getAds();
        final BigDecimal bidAmount = CollectionUtils.emptyIfNull(ads).stream()
                .findFirst()
                .map(AdnuntiusAd::getBid)
                .map(AdnuntiusBid::getAmount)
                .orElse(null);

        return targetId != null && matchedCount > 0 && bidAmount != null;
    }

    private static String extractTargetId(String targetId) {
        return targetId == null ? null : targetId.split(":")[0];
    }

    private static String targetIdForBids(String auId, String impId) {
        return "%s-%s".formatted(auId, impId);
    }

    private Bid createBid(AdnuntiusAd ad, BidRequest bidRequest, String adm, String impId, String bidType, int mtype) {
        final String adId = ad.getAdId();
        final AdnuntiusBidExt bidExt = prepareBidExt(ad, bidRequest);

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
                .adomain(ad.getAdvertiserDomains())
                .mtype(mtype)
                .ext(bidExt == null ? null : mapper.mapper().valueToTree(bidExt))
                .build();
    }

    private static AdnuntiusBidExt prepareBidExt(AdnuntiusAd ad, BidRequest bidRequest) {
        final ExtRegsDsa extRegsDsa = Optional.ofNullable(bidRequest.getRegs())
                .map(Regs::getExt)
                .map(ExtRegs::getDsa)
                .orElse(null);

        final AdnuntiusAdvertiser advertiser = ad.getAdvertiser();

        if (advertiser != null && advertiser.getName() != null && extRegsDsa != null) {
            final String legalName = ObjectUtils.firstNonNull(advertiser.getLegalName(), advertiser.getName());
            final ExtBidDsa dsa = ExtBidDsa.builder()
                    .adRender(0)
                    .paid(legalName)
                    .behalf(legalName)
                    .build();

            return AdnuntiusBidExt.of(dsa);
        }

        return null;
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
}
