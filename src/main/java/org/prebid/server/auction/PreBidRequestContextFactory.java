package org.prebid.server.auction;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.request.AdUnit;
import org.prebid.server.proto.request.Bid;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.settings.ApplicationSettings;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Used in legacy request processing.
 */
public class PreBidRequestContextFactory {

    private static final Logger logger = LoggerFactory.getLogger(PreBidRequestContextFactory.class);

    private static final TypeReference<List<Bid>> LIST_OF_BIDS_TYPE_REFERENCE = new TypeReference<List<Bid>>() {
    };

    private final TimeoutResolver timeoutResolver;
    private final ImplicitParametersExtractor paramsExtractor;
    private final ApplicationSettings applicationSettings;
    private final UidsCookieService uidsCookieService;
    private final TimeoutFactory timeoutFactory;
    private final JacksonMapper mapper;

    private final Random rand = new Random();

    public PreBidRequestContextFactory(TimeoutResolver timeoutResolver,
                                       ImplicitParametersExtractor paramsExtractor,
                                       ApplicationSettings applicationSettings,
                                       UidsCookieService uidsCookieService,
                                       TimeoutFactory timeoutFactory,
                                       JacksonMapper mapper) {

        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.paramsExtractor = Objects.requireNonNull(paramsExtractor);
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Creates a new instances of {@link PreBidRequestContext} wrapped into {@link Future} which
     * can be be eventually completed with success or error result.
     */
    public Future<PreBidRequestContext> fromRequest(RoutingContext context) {
        final Buffer body = context.getBody();

        if (body == null) {
            return Future.failedFuture(new PreBidException("Incoming request has no body"));
        }

        final PreBidRequest preBidRequest;
        try {
            preBidRequest = mapper.decodeValue(body, PreBidRequest.class);
        } catch (DecodeException e) {
            return Future.failedFuture(new PreBidException(e.getMessage(), e.getCause()));
        }

        final List<AdUnit> adUnits = preBidRequest.getAdUnits();
        if (adUnits == null || adUnits.isEmpty()) {
            return Future.failedFuture(new PreBidException("No ad units specified"));
        }

        final PreBidRequest adjustedRequest = adjustRequestTimeout(preBidRequest);
        final Timeout timeout = timeout(adjustedRequest);

        return extractBidders(adjustedRequest, timeout)
                .map(bidders -> PreBidRequestContext.builder().adapterRequests(bidders))
                .map(builder ->
                        populatePreBidRequestContextBuilder(context, adjustedRequest, context.request(), builder))
                .map(builder -> builder.timeout(timeout))
                .map(PreBidRequestContext.PreBidRequestContextBuilder::build);
    }

    private PreBidRequestContext.PreBidRequestContextBuilder populatePreBidRequestContextBuilder(
            RoutingContext context, PreBidRequest preBidRequest, HttpServerRequest httpRequest,
            PreBidRequestContext.PreBidRequestContextBuilder builder) {

        builder
                .preBidRequest(preBidRequest)
                .ip(paramsExtractor.ipFrom(httpRequest))
                .secure(paramsExtractor.secureFrom(httpRequest))
                .isDebug(isDebug(preBidRequest, httpRequest))
                .noLiveUids(false);

        if (preBidRequest.getApp() == null) {
            final String referer = paramsExtractor.refererFrom(httpRequest);
            if (StringUtils.isBlank(referer)) {
                throw new PreBidException("Referer cannot be null or empty");
            }

            final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(context);

            builder.uidsCookie(uidsCookie)
                    .noLiveUids(!uidsCookie.hasLiveUids())
                    .ua(paramsExtractor.uaFrom(httpRequest))
                    .referer(referer)
                    // next method could throw exception which will cause future to become failed
                    .domain(paramsExtractor.domainFrom(referer))
                    .build();
        }
        return builder;
    }

    private Future<List<AdapterRequest>> extractBidders(PreBidRequest preBidRequest, Timeout timeout) {
        // this is a List<Future<Stream<AdUnitBid>>> actually
        final List<Future> adUnitBidFutures = preBidRequest.getAdUnits().stream()
                .filter(PreBidRequestContextFactory::isValidAdUnit)
                .map(unit -> resolveUnitBids(unit, timeout)
                        .map(bids -> bids.stream().map(bid -> toAdUnitBid(unit, bid))))
                .collect(Collectors.toList());

        return CompositeFuture.join(adUnitBidFutures)
                .map(future -> future.<Stream<AdUnitBid>>list().stream()
                        .flatMap(Function.identity())
                        .collect(Collectors.groupingBy(AdUnitBid::getBidderCode))
                        .entrySet().stream()
                        .map(e -> AdapterRequest.of(e.getKey(), e.getValue()))
                        .collect(Collectors.toList()));
    }

    private static boolean isValidAdUnit(AdUnit adUnit) {
        return Objects.nonNull(adUnit.getCode()) && CollectionUtils.isNotEmpty(adUnit.getSizes());
    }

    private Future<List<Bid>> resolveUnitBids(AdUnit unit, Timeout timeout) {
        final Future<List<Bid>> result;

        final String configId = unit.getConfigId();
        if (StringUtils.isNotBlank(configId)) {
            result = applicationSettings.getAdUnitConfigById(configId, timeout)
                    .map(config -> toBids(config, configId))
                    .otherwise(exception -> fallbackResult(exception, configId));
        } else {
            result = Future.succeededFuture(unit.getBids());
        }

        return result;
    }

    private List<Bid> fallbackResult(Throwable exception, String configId) {
        if (exception instanceof PreBidException) {
            logger.warn("AdUnit config not found by id ''{0}'' from cache", configId);
        } else {
            logger.warn("Failed to load config ''{0}'' from cache", exception, configId);
        }
        return Collections.emptyList();
    }

    private List<Bid> toBids(String config, String configId) {
        try {
            return mapper.decodeValue(config, LIST_OF_BIDS_TYPE_REFERENCE);
        } catch (DecodeException e) {
            throw new PreBidException(String.format("Cannot parse AdUnit config for id: %s", configId), e);
        }
    }

    private AdUnitBid toAdUnitBid(AdUnit unit, Bid bid) {
        return AdUnitBid.builder()
                .bidderCode(bid.getBidder())
                .sizes(unit.getSizes())
                .topframe(unit.getTopframe())
                .instl(unit.getInstl())
                .adUnitCode(unit.getCode())
                .bidId(StringUtils.defaultIfBlank(bid.getBidId(), Long.toUnsignedString(rand.nextLong())))
                .params(bid.getParams())
                .video(unit.getVideo())
                .mediaTypes(makeBidMediaTypes(unit.getMediaTypes()))
                .build();
    }

    private Set<MediaType> makeBidMediaTypes(List<String> mediaTypes) {
        final Set<MediaType> result;

        if (mediaTypes != null && !mediaTypes.isEmpty()) {
            result = new HashSet<>();
            for (String mediaType : mediaTypes) {
                try {
                    result.add(MediaType.valueOf(mediaType.toLowerCase()));
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid mediaType: {0}", mediaType);
                }
            }
            if (result.isEmpty()) {
                result.add(MediaType.banner);
            }
        } else {
            result = Collections.singleton(MediaType.banner);
        }

        return result;
    }

    private PreBidRequest adjustRequestTimeout(PreBidRequest preBidRequest) {
        final Long requestTimeout = preBidRequest.getTimeoutMillis();
        final long resolvedTimeout = timeoutResolver.resolve(requestTimeout);
        final long timeout = timeoutResolver.adjustTimeout(resolvedTimeout);

        // check, do we really need to update request?
        return !Objects.equals(requestTimeout, timeout)
                ? preBidRequest.toBuilder().timeoutMillis(timeout).build()
                : preBidRequest;
    }

    private Timeout timeout(PreBidRequest preBidRequest) {
        return timeoutFactory.create(preBidRequest.getTimeoutMillis());
    }

    private static boolean isDebug(PreBidRequest preBidRequest, HttpServerRequest httpRequest) {
        return Objects.equals(preBidRequest.getIsDebug(), Boolean.TRUE)
                || Objects.equals(httpRequest.getParam("debug"), "1");
    }
}
