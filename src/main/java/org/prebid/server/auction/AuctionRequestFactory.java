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
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.model.ValidationResult;
import org.springframework.beans.factory.annotation.Value;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AuctionRequestFactory {

    private static final Logger logger = LoggerFactory.getLogger(AuctionRequestFactory.class);

    private final long defaultTimeout;
    private final long maxRequestSize;
    private final String adServerCurrency;
    private final StoredRequestProcessor storedRequestProcessor;
    private final ImplicitParametersExtractor paramsExtractor;
    private final UidsCookieService uidsCookieService;
    private final RequestValidator requestValidator;

    public AuctionRequestFactory(long defaultTimeout, long maxRequestSize, String adServerCurrency,
                                 StoredRequestProcessor storedRequestProcessor,
                                 ImplicitParametersExtractor paramsExtractor,
                                 UidsCookieService uidsCookieService,
                                 RequestValidator requestValidator) {
        this.defaultTimeout = defaultTimeout;
        this.maxRequestSize = maxRequestSize;
        this.adServerCurrency = validateCurrency(adServerCurrency);
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.paramsExtractor = Objects.requireNonNull(paramsExtractor);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.requestValidator = Objects.requireNonNull(requestValidator);
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
                .map(resolvedBidRequest -> fillImplicitParameters(resolvedBidRequest, context))
                .map(this::validateRequest);
    }

    /**
     * If needed creates a new {@link BidRequest} which is a copy of original but with some fields set with values
     * derived from request parameters (headers, cookie etc.).
     */
    BidRequest fillImplicitParameters(BidRequest bidRequest, RoutingContext context) {
        final BidRequest result;

        final HttpServerRequest request = context.request();
        final List<Imp> imps = bidRequest.getImp();

        final Device populatedDevice = populateDevice(bidRequest.getDevice(), request);
        final Site populatedSite = bidRequest.getApp() == null ? populateSite(bidRequest.getSite(), request) : null;
        final List<Imp> populatedImps = populateImps(imps, request);
        final User populatedUser = populateUser(bidRequest.getUser(), context);
        final Integer at = bidRequest.getAt();
        final boolean setDefaultAt = at == null || at == 0;
        final ObjectNode ext = bidRequest.getExt();
        final ObjectNode populatedExt = ext != null ? populateBidRequestExtension(ext) : null;
        final boolean updateCurrency = bidRequest.getCur() == null && adServerCurrency != null;
        final boolean updateTmax = bidRequest.getTmax() == null;

        if (populatedDevice != null || populatedSite != null || populatedUser != null || populatedExt != null
                || setDefaultAt || updateCurrency || populatedImps != null || updateTmax) {
            result = bidRequest.toBuilder()
                    .device(populatedDevice != null ? populatedDevice : bidRequest.getDevice())
                    .site(populatedSite != null ? populatedSite : bidRequest.getSite())
                    .user(populatedUser != null ? populatedUser : bidRequest.getUser())
                    .imp(populatedImps != null ? populatedImps : imps)
                    // set the auction type to 1 if it wasn't on the request,
                    // since header bidding is generally a first-price auction.
                    .at(setDefaultAt ? Integer.valueOf(1) : at)
                    .ext(populatedExt != null ? populatedExt : ext)
                    .cur(updateCurrency ? Collections.singletonList(adServerCurrency) : bidRequest.getCur())
                    .tmax(updateTmax ? defaultTimeout : bidRequest.getTmax())
                    .build();
        } else {
            result = bidRequest;
        }
        return result;
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

    /**
     * Creates updated bidrequest.ext {@link ExtBidRequest} if required.
     */
    private ObjectNode populateBidRequestExtension(ObjectNode ext) {
        final ExtBidRequest extBidRequest;
        try {
            extBidRequest = Json.mapper.treeToValue(ext, ExtBidRequest.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error decoding bidRequest.ext: %s", e.getMessage()));
        }
        final ExtRequestPrebid prebid = extBidRequest.getPrebid();
        final ExtRequestTargeting targeting = prebid != null ? prebid.getTargeting() : null;

        if (targeting == null) {
            return null;
        }

        final boolean isPriceGranularityNull = targeting.getPricegranularity().isNull();
        final boolean isPriceGranularityTextual = targeting.getPricegranularity().isTextual();
        final boolean isIncludeWinnersNull = targeting.getIncludewinners() == null;
        final boolean isIncludeBidderKeysNull = targeting.getIncludebidderkeys() == null;

        if (isPriceGranularityNull || isPriceGranularityTextual || isIncludeWinnersNull || isIncludeBidderKeysNull) {
            return Json.mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                    prebid.getAliases(),
                    prebid.getBidadjustmentfactors(),
                    ExtRequestTargeting.of(
                            populatePriceGranularity(targeting.getPricegranularity(), isPriceGranularityNull,
                                    isPriceGranularityTextual),
                            targeting.getCurrency(),
                            isIncludeWinnersNull ? true : targeting.getIncludewinners(),
                            isIncludeBidderKeysNull ? true : targeting.getIncludebidderkeys()),
                    prebid.getStoredrequest(),
                    prebid.getCache())));
        }
        return null;
    }

    /**
     * Populates priceGranularity with converted value.
     * <p>
     * In case of valid string price granularity replaced it with appropriate custom view.
     * In case of invalid string value throws {@link InvalidRequestException}.
     * In case of missing Json node sets default custom value.
     */
    private JsonNode populatePriceGranularity(JsonNode priceGranularityNode, boolean isPriceGranularityNull,
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
     * Parses request body to bid request. Throws {@link InvalidRequestException} if body is empty, exceeds max
     * request size or couldn't be deserialized to {@link BidRequest}.
     */
    private BidRequest parseRequest(RoutingContext context) {
        final BidRequest result;

        final Buffer body = context.getBody();
        if (body == null) {
            throw new InvalidRequestException("Incoming request has no body");
        } else if (body.length() > maxRequestSize) {
            throw new InvalidRequestException(
                    String.format("Request size exceeded max size of %d bytes.", maxRequestSize));
        } else {
            try {
                result = Json.decodeValue(body, BidRequest.class);
            } catch (DecodeException e) {
                throw new InvalidRequestException(e.getMessage());
            }
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

        if (StringUtils.isBlank(page) || StringUtils.isBlank(domain)) {
            final String referer = paramsExtractor.refererFrom(request);
            if (StringUtils.isNotBlank(referer)) {
                try {
                    final String parsedDomain = paramsExtractor.domainFrom(referer);
                    final Site.SiteBuilder builder = site == null ? Site.builder() : site.toBuilder();
                    builder.domain(StringUtils.isNotBlank(domain) ? domain : parsedDomain);
                    builder.page(StringUtils.isNotBlank(page) ? page : referer);
                    result = builder.build();
                } catch (PreBidException e) {
                    logger.warn("Error occurred while populating bid request", e);
                }
            }
        }
        return result;
    }

    /**
     * Updates imps with security 1, when secured request was received and imp security was not defined.
     */
    private List<Imp> populateImps(List<Imp> imps, HttpServerRequest request) {
        if (Objects.equals(paramsExtractor.secureFrom(request), 1)
                && imps.stream().map(Imp::getSecure).anyMatch(Objects::isNull)) {
            return imps.stream()
                    .map(imp -> imp.getSecure() == null ? imp.toBuilder().secure(1).build() : imp)
                    .collect(Collectors.toList());
        }
        return null;
    }

    /**
     * Populates the request body's 'user' section from the incoming http request if the original is partially filled
     * and the request contains necessary info (id).
     */
    private User populateUser(User user, RoutingContext context) {
        User result = null;

        final String id = user != null ? user.getId() : null;

        if (StringUtils.isBlank(id)) {
            final String parsedId = uidsCookieService.parseHostCookie(context);
            if (StringUtils.isNotBlank(parsedId)) {
                final User.UserBuilder builder = user == null ? User.builder() : user.toBuilder();
                builder.id(parsedId);
                result = builder.build();
            }
        }

        return result;
    }

    /**
     * Validates ISO 4217 currency code
     */
    private static String validateCurrency(String code) {
        if (StringUtils.isBlank(code)) {
            return code;
        }

        try {
            java.util.Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("Currency code supplied is not valid: %s", code), e);
        }
        return code;
    }
}
