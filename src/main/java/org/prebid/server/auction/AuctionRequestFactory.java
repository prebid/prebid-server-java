package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Used in OpenRTB request processing.
 */
public class AuctionRequestFactory {

    private static final Logger logger = LoggerFactory.getLogger(AuctionRequestFactory.class);

    private final TimeoutResolver timeoutResolver;
    private final long maxRequestSize;
    private final String adServerCurrency;
    private final StoredRequestProcessor storedRequestProcessor;
    private final ImplicitParametersExtractor paramsExtractor;
    private final UidsCookieService uidsCookieService;
    private final BidderCatalog bidderCatalog;
    private final RequestValidator requestValidator;
    private final InterstitialProcessor interstitialProcessor;

    public AuctionRequestFactory(
            TimeoutResolver timeoutResolver, long maxRequestSize, String adServerCurrency,
            StoredRequestProcessor storedRequestProcessor, ImplicitParametersExtractor paramsExtractor,
            UidsCookieService uidsCookieService, BidderCatalog bidderCatalog, RequestValidator requestValidator,
            InterstitialProcessor interstitialProcessor) {

        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.maxRequestSize = maxRequestSize;
        this.adServerCurrency = validateCurrency(adServerCurrency);
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.paramsExtractor = Objects.requireNonNull(paramsExtractor);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.requestValidator = Objects.requireNonNull(requestValidator);
        this.interstitialProcessor = Objects.requireNonNull(interstitialProcessor);
    }

    /**
     * Validates ISO-4217 currency code.
     */
    private static String validateCurrency(String code) {
        if (StringUtils.isBlank(code)) {
            return code;
        }

        try {
            Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Currency code supplied is not valid: %s", code), e);
        }
        return code;
    }

    /**
     * Method determines {@link BidRequest} properties which were not set explicitly by the client, but can be
     * updated by values derived from headers and other request attributes.
     */
    public Future<BidRequest> fromRequest(RoutingContext context) {
        final BidRequest bidRequest;
        try {
            bidRequest = parseRequest(context);
        } catch (InvalidRequestException e) {
            return Future.failedFuture(e);
        }

        return storedRequestProcessor.processStoredRequests(bidRequest)
                .map(resolvedBidRequest -> fillImplicitParameters(resolvedBidRequest, context, timeoutResolver))
                .map(this::validateRequest)
                .map(interstitialProcessor::process);
    }


    /**
     * Parses request body to bid request. Throws {@link InvalidRequestException} if body is empty, exceeds max
     * request size or couldn't be deserialized to {@link BidRequest}.
     */
    private BidRequest parseRequest(RoutingContext context) {
        final Buffer body = context.getBody();
        if (body == null) {
            throw new InvalidRequestException("Incoming request has no body");
        } else if (body.length() > maxRequestSize) {
            throw new InvalidRequestException(
                    String.format("Request size exceeded max size of %d bytes.", maxRequestSize));
        } else {
            try {
                return Json.decodeValue(body, BidRequest.class);
            } catch (DecodeException e) {
                throw new InvalidRequestException(e.getMessage());
            }
        }
    }

    /**
     * If needed creates a new {@link BidRequest} which is a copy of original but with some fields set with values
     * derived from request parameters (headers, cookie etc.).
     */
    BidRequest fillImplicitParameters(BidRequest bidRequest, RoutingContext context, TimeoutResolver timeoutResolver) {
        final BidRequest result;

        final HttpServerRequest request = context.request();
        final List<Imp> imps = bidRequest.getImp();

        final Device populatedDevice = populateDevice(bidRequest.getDevice(), request);
        final Site populatedSite = bidRequest.getApp() == null ? populateSite(bidRequest.getSite(), request) : null;
        final User populatedUser = populateUser(bidRequest.getUser(), context);
        final List<Imp> populatedImps = populateImps(imps, request);
        final Integer at = bidRequest.getAt();
        final boolean updateAt = at == null || at == 0;
        final ObjectNode ext = bidRequest.getExt();
        final ObjectNode populatedExt = ext != null
                ? populateBidRequestExtension(ext, ObjectUtils.defaultIfNull(populatedImps, imps))
                : null;
        final boolean updateCurrency = CollectionUtils.isEmpty(bidRequest.getCur()) && adServerCurrency != null;
        final Long resolvedTmax = resolveTmax(bidRequest.getTmax(), timeoutResolver);

        if (populatedDevice != null || populatedSite != null || populatedUser != null || populatedImps != null
                || updateAt || populatedExt != null || updateCurrency || resolvedTmax != null) {

            result = bidRequest.toBuilder()
                    .device(populatedDevice != null ? populatedDevice : bidRequest.getDevice())
                    .site(populatedSite != null ? populatedSite : bidRequest.getSite())
                    .user(populatedUser != null ? populatedUser : bidRequest.getUser())
                    .imp(populatedImps != null ? populatedImps : imps)
                    // set the auction type to 1 if it wasn't on the request,
                    // since header bidding is generally a first-price auction.
                    .at(updateAt ? Integer.valueOf(1) : at)
                    .ext(populatedExt != null ? populatedExt : ext)
                    .cur(updateCurrency ? Collections.singletonList(adServerCurrency) : bidRequest.getCur())
                    .tmax(resolvedTmax != null ? resolvedTmax : bidRequest.getTmax())
                    .build();
        } else {
            result = bidRequest;
        }
        return result;
    }

    /**
     * Populates the request body's 'device' section from the incoming http request if the original is partially filled
     * and the request contains necessary info (User-Agent, IP-address).
     */
    private Device populateDevice(Device device, HttpServerRequest request) {
        final Device result;

        final String ip = device != null ? device.getIp() : null;
        final String ua = device != null ? device.getUa() : null;

        if (StringUtils.isBlank(ip) || StringUtils.isBlank(ua)) {
            final Device.DeviceBuilder builder = device == null ? Device.builder() : device.toBuilder();
            builder.ip(StringUtils.isNotBlank(ip) ? ip : paramsExtractor.ipFrom(request));
            builder.ua(StringUtils.isNotBlank(ua) ? ua : paramsExtractor.uaFrom(request));

            result = builder.build();
        } else {
            result = null;
        }
        return result;
    }

    /**
     * Populates the request body's 'site' section from the incoming http request if the original is partially filled
     * and the request contains necessary info (domain, page).
     */
    private Site populateSite(Site site, HttpServerRequest request) {
        Site result = null;

        final String page = site != null ? site.getPage() : null;
        final String domain = site != null ? site.getDomain() : null;
        final ObjectNode siteExt = site != null ? site.getExt() : null;
        final ObjectNode data = siteExt != null ? (ObjectNode) siteExt.get("data") : null;
        final boolean shouldSetExtAmp = siteExt == null || siteExt.get("amp") == null;
        final ObjectNode modifiedSiteExt = shouldSetExtAmp ? Json.mapper.valueToTree(
                ExtSite.of(0, data)) : null;

        String referer = null;
        String parsedDomain = null;
        if (StringUtils.isBlank(page) || StringUtils.isBlank(domain)) {
            referer = paramsExtractor.refererFrom(request);
            if (StringUtils.isNotBlank(referer)) {
                try {
                    parsedDomain = paramsExtractor.domainFrom(referer);
                } catch (PreBidException e) {
                    logger.warn("Error occurred while populating bid request", e);
                }
            }
        }
        final boolean shouldModifyPageOrDomain = referer != null && parsedDomain != null;

        if (shouldModifyPageOrDomain || shouldSetExtAmp) {
            final Site.SiteBuilder builder = site == null ? Site.builder() : site.toBuilder();
            if (shouldModifyPageOrDomain) {
                builder.domain(StringUtils.isNotBlank(domain) ? domain : parsedDomain);
                builder.page(StringUtils.isNotBlank(page) ? page : referer);
            }
            if (shouldSetExtAmp) {
                builder.ext(modifiedSiteExt);
            }
            result = builder.build();
        }
        return result;
    }

    /**
     * Populates the request body's 'user' section from the incoming http request if the original is partially filled
     * and the request contains necessary info (id).
     */
    private User populateUser(User user, RoutingContext context) {
        User result = null;

        final String id = user != null ? user.getId() : null;
        if (StringUtils.isBlank(id)) {
            final String parsedId = uidsCookieService.parseHostCookie(HttpUtil.cookiesAsMap(context));
            if (StringUtils.isNotBlank(parsedId)) {
                final User.UserBuilder builder = user == null ? User.builder() : user.toBuilder();
                builder.id(parsedId);
                result = builder.build();
            }
        }
        return result;
    }

    /**
     * Updates imps with security 1, when secured request was received and imp security was not defined.
     */
    private List<Imp> populateImps(List<Imp> imps, HttpServerRequest request) {
        List<Imp> result = null;

        if (Objects.equals(paramsExtractor.secureFrom(request), 1)
                && imps.stream().map(Imp::getSecure).anyMatch(Objects::isNull)) {
            result = imps.stream()
                    .map(imp -> imp.getSecure() == null ? imp.toBuilder().secure(1).build() : imp)
                    .collect(Collectors.toList());
        }
        return result;
    }

    /**
     * Returns updated {@link ExtBidRequest} if required or null otherwise.
     */
    private ObjectNode populateBidRequestExtension(ObjectNode extNode, List<Imp> imps) {
        final ExtBidRequest extBidRequest = extBidRequest(extNode);
        final ExtRequestPrebid prebid = extBidRequest.getPrebid();

        final ExtRequestTargeting updatedTargeting = targetingOrNull(prebid);
        final Map<String, String> updatedAliases = aliasesOrNull(prebid, imps);

        final ObjectNode result;
        if (updatedTargeting != null || updatedAliases != null) {
            final ExtRequestPrebid.ExtRequestPrebidBuilder prebidBuilder = prebid != null
                    ? prebid.toBuilder()
                    : ExtRequestPrebid.builder();

            result = Json.mapper.valueToTree(ExtBidRequest.of(prebidBuilder
                    .aliases(ObjectUtils.defaultIfNull(updatedAliases,
                            getIfNotNull(prebid, ExtRequestPrebid::getAliases)))
                    .targeting(ObjectUtils.defaultIfNull(updatedTargeting,
                            getIfNotNull(prebid, ExtRequestPrebid::getTargeting)))
                    .build()));
        } else {
            result = null;
        }
        return result;
    }

    /**
     * Extracts {@link ExtBidRequest} from bidrequest.ext {@link ObjectNode}.
     */
    private static ExtBidRequest extBidRequest(ObjectNode extBidRequestNode) {
        try {
            return Json.mapper.treeToValue(extBidRequestNode, ExtBidRequest.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error decoding bidRequest.ext: %s", e.getMessage()));
        }
    }

    /**
     * Returns populated {@link ExtRequestTargeting} or null if no changes were applied.
     */
    private static ExtRequestTargeting targetingOrNull(ExtRequestPrebid prebid) {
        final ExtRequestTargeting targeting = prebid != null ? prebid.getTargeting() : null;

        final boolean isTargetingNotNull = targeting != null;
        final boolean isPriceGranularityNull = isTargetingNotNull && targeting.getPricegranularity().isNull();
        final boolean isPriceGranularityTextual = isTargetingNotNull && targeting.getPricegranularity().isTextual();
        final boolean isIncludeWinnersNull = isTargetingNotNull && targeting.getIncludewinners() == null;
        final boolean isIncludeBidderKeysNull = isTargetingNotNull && targeting.getIncludebidderkeys() == null;

        final ExtRequestTargeting result;
        if (isPriceGranularityNull || isPriceGranularityTextual || isIncludeWinnersNull || isIncludeBidderKeysNull) {
            result = ExtRequestTargeting.of(
                    populatePriceGranularity(targeting.getPricegranularity(), isPriceGranularityNull,
                            isPriceGranularityTextual),
                    targeting.getMediatypepricegranularity(),
                    targeting.getCurrency(),
                    isIncludeWinnersNull ? true : targeting.getIncludewinners(),
                    isIncludeBidderKeysNull ? true : targeting.getIncludebidderkeys());
        } else {
            result = null;
        }
        return result;
    }

    /**
     * Populates priceGranularity with converted value.
     * <p>
     * In case of valid string price granularity replaced it with appropriate custom view.
     * In case of invalid string value throws {@link InvalidRequestException}.
     * In case of missing Json node sets default custom value.
     */
    private static JsonNode populatePriceGranularity(JsonNode priceGranularityNode, boolean isPriceGranularityNull,
                                                     boolean isPriceGranularityTextual) {
        if (isPriceGranularityNull) {
            return Json.mapper.valueToTree(ExtPriceGranularity.from(PriceGranularity.DEFAULT));
        } else if (isPriceGranularityTextual) {
            final PriceGranularity priceGranularity;
            try {
                priceGranularity = PriceGranularity.createFromString(priceGranularityNode.textValue());
            } catch (PreBidException ex) {
                throw new InvalidRequestException(ex.getMessage());
            }
            return Json.mapper.valueToTree(ExtPriceGranularity.from(priceGranularity));
        }
        return priceGranularityNode;
    }

    /**
     * Returns aliases according to request.imp[i].ext.{bidder}
     * or null (if no aliases at all or they are already presented in request).
     */
    private Map<String, String> aliasesOrNull(ExtRequestPrebid prebid, List<Imp> imps) {
        final Map<String, String> aliases = getIfNotNullOrDefault(prebid, ExtRequestPrebid::getAliases,
                Collections.emptyMap());

        // go through imps' bidders and figure out preconfigured aliases
        final Map<String, String> resolvedAliases = imps.stream()
                .flatMap(imp -> asStream(imp.getExt().fieldNames())
                        .filter(bidder -> !aliases.containsKey(bidder))
                        .filter(bidderCatalog::isAlias))
                .distinct()
                .collect(Collectors.toMap(Function.identity(), bidderCatalog::nameByAlias));

        final Map<String, String> result;
        if (resolvedAliases.isEmpty()) {
            result = null;
        } else {
            result = new HashMap<>(aliases);
            result.putAll(resolvedAliases);
        }
        return result;
    }

    /**
     * Determines request timeout with the help of {@link TimeoutResolver}.
     * Returns resolved new value or null if existing request timeout doesn't need to update.
     */
    private static Long resolveTmax(Long requestTimeout, TimeoutResolver timeoutResolver) {
        final long timeout = timeoutResolver.resolve(requestTimeout);
        return !Objects.equals(requestTimeout, timeout) ? timeout : null;
    }

    private static <T> Stream<T> asStream(Iterator<T> iterator) {
        final Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    private static <T, R> R getIfNotNull(T target, Function<T, R> getter) {
        return target != null ? getter.apply(target) : null;
    }

    private static <T, R> R getIfNotNullOrDefault(T target, Function<T, R> getter, R defaultValue) {
        return ObjectUtils.defaultIfNull(getIfNotNull(target, getter), defaultValue);
    }

    /**
     * Performs thorough validation of fully constructed {@link BidRequest} that is going to be used to hold an auction.
     */
    BidRequest validateRequest(BidRequest bidRequest) {
        final ValidationResult validationResult = requestValidator.validate(bidRequest);
        if (validationResult.hasErrors()) {
            throw new InvalidRequestException(validationResult.getErrors());
        }
        return bidRequest;
    }
}
