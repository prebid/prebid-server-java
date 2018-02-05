package org.rtb.vexing.auction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.cookie.UidsCookieService;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.request.AdUnit;
import org.rtb.vexing.model.request.Bid;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.settings.ApplicationSettings;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PreBidRequestContextFactory {

    private static final Logger logger = LoggerFactory.getLogger(PreBidRequestContextFactory.class);

    private final Long defaultHttpRequestTimeout;

    private final PublicSuffixList psl;
    private final ApplicationSettings applicationSettings;
    private final UidsCookieService uidsCookieService;

    private final Random rand = new Random();

    public PreBidRequestContextFactory(Long defaultHttpRequestTimeout, PublicSuffixList psl,
                                        ApplicationSettings applicationSettings, UidsCookieService uidsCookieService) {
        this.defaultHttpRequestTimeout = Objects.requireNonNull(defaultHttpRequestTimeout);
        this.psl = Objects.requireNonNull(psl);
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
    }

    public Future<PreBidRequestContext> fromRequest(RoutingContext context) {
        Objects.requireNonNull(context);

        final JsonObject json;
        try {
            json = context.getBodyAsJson();
        } catch (DecodeException e) {
            return Future.failedFuture(new PreBidException(e.getMessage(), e.getCause()));
        }

        if (json == null) {
            return Future.failedFuture(new PreBidException("Incoming request has no body"));
        }

        final PreBidRequest preBidRequest = json.mapTo(PreBidRequest.class);

        if (preBidRequest.adUnits == null || preBidRequest.adUnits.isEmpty()) {
            return Future.failedFuture(new PreBidException("No ad units specified"));
        }

        final HttpServerRequest httpRequest = context.request();

        return extractBidders(preBidRequest)
                .map(bidders -> PreBidRequestContext.builder().bidders(bidders))
                .map(builder -> populatePreBidRequestContextBuilder(context, preBidRequest, httpRequest, builder))
                .map(PreBidRequestContext.PreBidRequestContextBuilder::build);
    }

    /**
     * Method determines {@link BidRequest} properties which were not set explicitly by the client, but can be
     * updated by values derived from headers and other request attributes.
     *
     * @param bidRequest an incoming request body
     * @param context a routing context
     * @return the original bidRequest passed into the method or updated instance
     */
    public BidRequest fromRequest(BidRequest bidRequest, RoutingContext context) {
        final BidRequest result;

        final HttpServerRequest request = context.request();

        final Device populatedDevice = populateDevice(bidRequest.getDevice(), request);
        final Site populatedSite = bidRequest.getApp() == null ? populateSite(bidRequest.getSite(), request) : null;
        final User populatedUser = populateUser(bidRequest.getUser(), context);

        if (populatedDevice != null || populatedSite != null || populatedUser != null) {
            result = bidRequest.toBuilder()
                    .device(populatedDevice != null ? populatedDevice : bidRequest.getDevice())
                    .site(populatedSite != null ? populatedSite : bidRequest.getSite())
                    .user(populatedUser != null ? populatedUser : bidRequest.getUser())
                    .build();
        } else {
            result = bidRequest;
        }

        return result;
    }

    /**
     * Populates the request body's 'device' section from the incoming http request if the original is partially filled
     * and the request contains necessary info (User-Agent, IP-address).
     *
     * @param device an original {@link Device} object from the request body
     * @param request incoming http request
     * @return the updated or empty(null) device entity
     */
    private Device populateDevice(Device device, HttpServerRequest request) {
        final Device result;

        final String ip = device != null ? device.getIp() : null;
        final String ua = device != null ? device.getUa() : null;

        if (StringUtils.isBlank(ip) || StringUtils.isBlank(ua)) {
            final Device.DeviceBuilder builder = device == null ? Device.builder() : device.toBuilder();
            builder.ip(StringUtils.isNotBlank(ip) ? ip : ip(request));
            builder.ua(StringUtils.isNotBlank(ua) ? ua : ua(request));

            result = builder.build();
        } else {
            result = null;
        }

        return result;
    }

    /**
     * Populates the request body's 'site' section from the incoming http request if the original is partially filled
     * and the request contains necessary info (domain, page).
     *
     * @param site an original {@link Site} object from the request body
     * @param request incoming http request
     * @return the updated or empty(null) site entity
     */
    private Site populateSite(Site site, HttpServerRequest request) {
        Site result = null;

        final String page = site != null ? site.getPage() : null;
        final String domain = site != null ? site.getDomain() : null;

        if (StringUtils.isBlank(page) || StringUtils.isBlank(domain)) {
            final String referer = referer(request);
            if (StringUtils.isNotBlank(referer)) {
                try {
                    final String parsedDomain = domain(referer);
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

    private PreBidRequestContext.PreBidRequestContextBuilder populatePreBidRequestContextBuilder(
            RoutingContext context, PreBidRequest preBidRequest, HttpServerRequest httpRequest,
            PreBidRequestContext.PreBidRequestContextBuilder builder) {

        builder
                .preBidRequest(preBidRequest)
                .timeout(timeoutOrDefault(preBidRequest))
                .ip(ip(httpRequest))
                .secure(secure(httpRequest))
                .isDebug(isDebug(preBidRequest, httpRequest))
                .noLiveUids(false);

        if (preBidRequest.app == null) {
            final String referer = referer(httpRequest);
            final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(context);

            builder.uidsCookie(uidsCookie)
                    .noLiveUids(!uidsCookie.hasLiveUids())
                    .ua(ua(httpRequest))
                    .referer(referer)
                    // next method could throw exception which will cause future to become failed
                    .domain(domain(referer))
                    .build();
        }
        return builder;
    }

    private Future<List<Bidder>> extractBidders(PreBidRequest preBidRequest) {
        // this is a List<Future<Stream<AdUnitBid>>> actually
        final List<Future> adUnitBidFutures = preBidRequest.adUnits.stream()
                .filter(PreBidRequestContextFactory::isValidAdUnit)
                .map(unit -> resolveUnitBids(unit).map(bids -> bids.stream().map(bid -> toAdUnitBid(unit, bid))))
                .collect(Collectors.toList());

        return CompositeFuture.join(adUnitBidFutures)
                .map(future -> future.<Stream<AdUnitBid>>list().stream()
                        .flatMap(Function.identity())
                        .collect(Collectors.groupingBy(a -> a.bidderCode))
                        .entrySet().stream()
                        .map(e -> Bidder.from(e.getKey(), e.getValue()))
                        .collect(Collectors.toList()));
    }

    private static boolean isValidAdUnit(AdUnit adUnit) {
        return Objects.nonNull(adUnit.code) && CollectionUtils.isNotEmpty(adUnit.sizes);
    }

    private Future<List<Bid>> resolveUnitBids(AdUnit unit) {
        final Future<List<Bid>> result;

        if (StringUtils.isNotBlank(unit.configId)) {
            result = applicationSettings.getAdUnitConfigById(unit.configId)
                    .map(config -> Json.decodeValue(config, new TypeReference<List<Bid>>() {
                    }))
                    .otherwise(exception -> {
                        logger.warn("Failed to load config ''{0}'' from cache", unit.configId, exception);
                        return Collections.emptyList();
                    });
        } else {
            result = Future.succeededFuture(unit.bids);
        }

        return result;
    }

    private AdUnitBid toAdUnitBid(AdUnit unit, Bid bid) {
        return AdUnitBid.builder()
                .bidderCode(bid.bidder)
                .sizes(unit.sizes)
                .topframe(unit.topframe)
                .instl(unit.instl)
                .adUnitCode(unit.code)
                .bidId(StringUtils.defaultIfBlank(bid.bidId, Long.toUnsignedString(rand.nextLong())))
                .params(bid.params)
                .video(unit.video)
                .mediaTypes(makeBidMediaTypes(unit.mediaTypes))
                .build();
    }

    private Set<MediaType> makeBidMediaTypes(List<String> mediaTypes) {
        final Set<MediaType> bidMediaTypes;
        if (mediaTypes != null && !mediaTypes.isEmpty()) {
            bidMediaTypes = new HashSet<>();
            for (String mediaType : mediaTypes) {
                try {
                    bidMediaTypes.add(MediaType.valueOf(mediaType.toLowerCase()));
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid mediaType: {0}", mediaType);
                }
            }
            if (bidMediaTypes.size() == 0) {
                bidMediaTypes.add(MediaType.banner);
            }
        } else {
            bidMediaTypes = Collections.singleton(MediaType.banner);
        }
        return bidMediaTypes;
    }

    private long timeoutOrDefault(PreBidRequest preBidRequest) {
        Long value = preBidRequest.timeoutMillis;
        if (value == null || value <= 0 || value > 2000L) {
            value = defaultHttpRequestTimeout;
        }
        return value;
    }

    private static Integer secure(HttpServerRequest httpRequest) {
        return StringUtils.equalsIgnoreCase(httpRequest.headers().get("X-Forwarded-Proto"), "https")
                || StringUtils.equalsIgnoreCase(httpRequest.scheme(), "https")
                ? 1 : null;
    }

    private String domain(String referer) {
        final URL url;
        try {
            url = new URL(referer);
        } catch (MalformedURLException e) {
            throw new PreBidException(String.format("Invalid URL '%s': %s", referer, e.getMessage()), e);
        }

        final String host = url.getHost();
        if (StringUtils.isBlank(host)) {
            throw new PreBidException(String.format("Host not found from URL '%s'", url.toString()));
        }

        final String domain = psl.getRegistrableDomain(host);

        if (domain == null) {
            // null means effective top level domain plus one couldn't be derived
            throw new PreBidException(
                    String.format("Invalid URL '%s': cannot derive eTLD+1 for domain %s", host, host));
        }

        return domain;
    }

    /**
     * Determines Referer by checking 'url_override' request parameter, or if it's empty 'Referer' header. Then if
     * result is not blank and missing 'http://' prefix appends it.
     *
     * @param request incoming http request
     * @return a refere value
     */
    private static String referer(HttpServerRequest request) {
        final String urlOverride = request.getParam("url_override");
        final String url = StringUtils.isNotBlank(urlOverride) ? urlOverride
                : StringUtils.trimToNull(request.headers().get(HttpHeaders.REFERER));

        return StringUtils.isNotBlank(url) && !StringUtils.startsWith(url, "http")
                ? String.format("http://%s", url)
                : url;
    }

    /**
     * Determines User-Agent by checking 'User-Agent' http header.
     *
     * @param request incoming http request
     * @return a user agent value
     */
    private static String ua(HttpServerRequest request) {
        return StringUtils.trimToNull(request.headers().get(HttpHeaders.USER_AGENT));
    }

    /**
     * Determines IP-Address by checking "X-Forwarded-For", "X-Real-IP" http headers or remote host address
     * if both are empty.
     *
     * @param request incoming http request
     * @return an ip value
     */
    private static String ip(HttpServerRequest request) {
        return ObjectUtils.firstNonNull(
                StringUtils.trimToNull(
                        // X-Forwarded-For: client1, proxy1, proxy2
                        StringUtils.substringBefore(request.headers().get("X-Forwarded-For"), ",")),
                StringUtils.trimToNull(request.headers().get("X-Real-IP")),
                StringUtils.trimToNull(request.remoteAddress().host()));
    }

    private static boolean isDebug(PreBidRequest preBidRequest, HttpServerRequest httpRequest) {
        return Objects.equals(preBidRequest.isDebug, Boolean.TRUE)
                || Objects.equals(httpRequest.getParam("debug"), "1");
    }
}
