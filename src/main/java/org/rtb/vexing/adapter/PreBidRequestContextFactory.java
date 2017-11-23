package org.rtb.vexing.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.request.AdUnit;
import org.rtb.vexing.model.request.Bid;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.settings.ApplicationSettings;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

public class PreBidRequestContextFactory {

    private static final Logger logger = LoggerFactory.getLogger(PreBidRequestContextFactory.class);

    private final Long defaultHttpRequestTimeout;

    private final ApplicationSettings applicationSettings;
    private final PublicSuffixList psl;

    private final Random rand = new Random();

    private PreBidRequestContextFactory(Long defaultHttpRequestTimeout, ApplicationSettings applicationSettings,
                                        PublicSuffixList psl) {
        this.defaultHttpRequestTimeout = defaultHttpRequestTimeout;
        this.applicationSettings = applicationSettings;
        this.psl = psl;
    }

    public static PreBidRequestContextFactory create(ApplicationConfig config, PublicSuffixList psl,
                                                     ApplicationSettings applicationSettings) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(psl);
        Objects.requireNonNull(applicationSettings);

        return new PreBidRequestContextFactory(config.getLong("default-timeout-ms"), applicationSettings, psl);
    }

    public PreBidRequestContext fromRequest(RoutingContext context) {
        Objects.requireNonNull(context);

        final JsonObject json;
        try {
            json = context.getBodyAsJson();
        } catch (DecodeException e) {
            throw new PreBidRequestException(e.getMessage(), e.getCause());
        }

        if (json == null) {
            throw new PreBidRequestException("Incoming request has no body");
        }

        final PreBidRequest preBidRequest = json.mapTo(PreBidRequest.class);

        if (preBidRequest.adUnits == null || preBidRequest.adUnits.isEmpty()) {
            throw new PreBidRequestException("No ad units specified");
        }

        final HttpServerRequest httpRequest = context.request();

        final PreBidRequestContext.PreBidRequestContextBuilder builder = PreBidRequestContext.builder()
                .bidders(extractBidders(preBidRequest))
                .preBidRequest(preBidRequest)
                .timeout(timeoutOrDefault(preBidRequest))
                .ip(ip(httpRequest))
                .secure(secure(httpRequest))
                .isDebug(isDebug(preBidRequest, httpRequest));

        if (preBidRequest.app == null) {
            final String referer = referer(httpRequest);
            builder.uidsCookie(UidsCookie.parseFromRequest(context))
                    .ua(ua(httpRequest))
                    .referer(referer)
                    .domain(domain(referer))
                    .build();
        }

        return builder.build();
    }

    private List<Bidder> extractBidders(PreBidRequest preBidRequest) {
        return preBidRequest.adUnits.stream()
                .flatMap(unit -> resolveUnitBids(unit).stream().map(bid -> AdUnitBid.builder()
                        .bidderCode(bid.bidder)
                        .sizes(unit.sizes)
                        .topframe(unit.topframe)
                        .instl(unit.instl)
                        .adUnitCode(unit.code)
                        .bidId(StringUtils.defaultIfBlank(bid.bidId, Long.toUnsignedString(rand.nextLong())))
                        .params(bid.params)
                        .build()))
                .collect(Collectors.groupingBy(a -> a.bidderCode))
                .entrySet().stream()
                .map(e -> Bidder.from(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private List<Bid> resolveUnitBids(AdUnit unit) {
        List<Bid> bids = unit.bids;

        if (StringUtils.isNotBlank(unit.configId)) {
            bids = Collections.emptyList();

            final Optional<String> adUnitConfig = applicationSettings.getAdUnitConfigById(unit.configId);
            if (!adUnitConfig.isPresent()) {
                logger.warn("Failed to load config '{0}' from cache: Not found", unit.configId);
            } else {
                try {
                    bids = Json.decodeValue(adUnitConfig.get(), new TypeReference<List<Bid>>() {
                    });
                } catch (DecodeException e) {
                    logger.warn("Failed to load config '{0}' from cache: {1}", unit.configId, e);
                }
            }
        }

        return bids;
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

    private static String referer(HttpServerRequest httpRequest) {
        return httpRequest.headers().get(HttpHeaders.REFERER);
    }

    private String domain(String referer) {
        final URL url;
        try {
            url = new URL(referer);
        } catch (MalformedURLException e) {
            throw new PreBidRequestException(String.format("Invalid URL '%s': %s", referer, e.getMessage()), e);
        }

        final String host = url.getHost();
        if (StringUtils.isBlank(host)) {
            throw new PreBidRequestException(String.format("Host not found from URL '%s'", url.toString()));
        }

        final String domain = psl.getRegistrableDomain(host);

        if (domain == null) {
            // null means effective top level domain plus one couldn't be derived
            throw new PreBidRequestException(
                    String.format("Invalid URL '%s': cannot derive eTLD+1 for domain %s", host, host));
        }

        return domain;
    }

    private static String ua(HttpServerRequest httpRequest) {
        return httpRequest.headers().get(HttpHeaders.USER_AGENT);
    }

    private static String ip(HttpServerRequest httpRequest) {
        return ObjectUtils.firstNonNull(
                StringUtils.trimToNull(
                        // X-Forwarded-For: client1, proxy1, proxy2
                        StringUtils.substringBefore(httpRequest.headers().get("X-Forwarded-For"), ",")),
                StringUtils.trimToNull(httpRequest.headers().get("X-Real-IP")),
                StringUtils.trimToNull(httpRequest.remoteAddress().host()));
    }

    private static boolean isDebug(PreBidRequest preBidRequest, HttpServerRequest httpRequest) {
        return Objects.equals(preBidRequest.isDebug, Boolean.TRUE)
                || Objects.equals(httpRequest.getParam("debug"), "1");
    }
}
