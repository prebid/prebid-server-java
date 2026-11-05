package org.prebid.server.hooks.modules.intentiq.identity.v1;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BrandVersion;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.UserAgent;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.intentiq.identity.cache.CacheKey;
import org.prebid.server.hooks.modules.intentiq.identity.cache.CacheResult;
import org.prebid.server.hooks.modules.intentiq.identity.cache.IdentityCache;
import org.prebid.server.hooks.modules.intentiq.identity.cache.KeyType;
import org.prebid.server.hooks.modules.intentiq.identity.metric.IntentiqIdentityMetrics;
import org.prebid.server.hooks.modules.intentiq.identity.model.IntentiqIdentityModuleContext;
import org.prebid.server.hooks.modules.intentiq.identity.model.config.IntentiqIdentityProperties;
import org.prebid.server.hooks.modules.intentiq.identity.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.intentiq.identity.v1.core.FirstPartyKeyExtractor;
import org.prebid.server.hooks.modules.intentiq.identity.v1.core.IiqParam;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.httpclient.HttpClient;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class IntentiqIdentityProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final Logger logger =
            LoggerFactory.getLogger(IntentiqIdentityProcessedAuctionRequestHook.class);

    public static final String CODE = "intentiq-identity-processed-auction-request-hook";

    private static final String IIQ_SOURCE = "intentiq.com";

    // Identifies the request source to the IntentIQ S2S API as prebid-server-java.
    private static final String SOURCE_PBJV = "pbjv";

    // Per the GDPR S2S guide, the TCF consent string is passed via the `gdpr-consent` request header,
    // not as a query parameter.
    private static final String GDPR_CONSENT_HEADER = "gdpr-consent";

    private static final long DEFAULT_TIMEOUT_MS = 1000L;

    private final ConfigResolver configResolver;
    private final HttpClient httpClient;
    private final JacksonMapper mapper;
    private final IdentityCache cache;
    private final FirstPartyKeyExtractor keyExtractor;
    private final IntentiqIdentityMetrics metrics;

    public IntentiqIdentityProcessedAuctionRequestHook(ConfigResolver configResolver,
                                                       HttpClient httpClient,
                                                       JacksonMapper mapper,
                                                       IdentityCache cache,
                                                       FirstPartyKeyExtractor keyExtractor,
                                                       IntentiqIdentityMetrics metrics) {
        this.configResolver = Objects.requireNonNull(configResolver);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.mapper = Objects.requireNonNull(mapper);
        this.cache = cache;
        this.keyExtractor = Objects.requireNonNull(keyExtractor);
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {
        final long startNanos = System.nanoTime();
        final IntentiqIdentityProperties properties = configResolver.resolve(invocationContext.accountConfig());
        final String dpi = properties.getPartnerId();
        if (StringUtils.isBlank(properties.getApiEndpoint())) {
            metrics.skipNoEndpoint(dpi);
            return Future.succeededFuture(noAction(new IntentiqIdentityModuleContext(startNanos, null, null)));
        }

        return resolveEids(properties, auctionRequestPayload.bidRequest())
                .map(resolution -> {
                    final List<Eid> eids = resolution.eids();
                    final IntentiqIdentityModuleContext context = new IntentiqIdentityModuleContext(
                            startNanos, resolution.abTestUuid(), resolution.terminationCause());
                    if (eids == null || eids.isEmpty()) {
                        metrics.eidsNone(dpi);
                        return noAction(context);
                    }
                    metrics.enriched(dpi);
                    return update(payload -> enrichUserEids(payload, eids), context);
                })
                .otherwise(throwable -> {
                    logger.warn("IntentIQ identity resolution failed, proceeding without enrichment", throwable);
                    metrics.apiError(dpi);
                    return noAction(new IntentiqIdentityModuleContext(startNanos, null, null));
                });
    }

    private Future<Resolution> resolveEids(IntentiqIdentityProperties properties, BidRequest bidRequest) {
        final boolean cacheEnabled = cache != null && properties.getCache().isEnabled();
        final List<CacheKey> keys = cacheEnabled ? keyExtractor.candidateKeys(bidRequest) : List.of();
        if (keys.isEmpty()) {
            return fetch(properties, bidRequest)
                    .map(response -> new Resolution(
                            extractEids(response), response.getAbTestUuid(), response.getTc()));
        }

        final String dpi = properties.getPartnerId();
        // Cache counters are broken down by key type: the type of the key that actually matched for
        // HIT/NEGATIVE/IN_PROGRESS, and the request's primary (highest-priority) candidate type for a
        // full MISS, where no key matched.
        final String primaryType = keyTypeToken(keys.getFirst().type());
        return cache.get(keys).compose(result -> switch (result.state()) {
            case HIT -> {
                metrics.cacheHit(layerToken(result.layer()), keyTypeToken(result.keyType()), dpi);
                yield Future.succeededFuture(new Resolution(result.eids(), null, null));
            }
            case NEGATIVE -> {
                // A negative sentinel is a cached miss (id is known-unresolvable): no identity to
                // serve, so it counts toward cache.miss, not cache.hit. The negative-specific counter
                // distinguishes it from a true miss (no API call is made here) and is tagged by layer.
                final String type = keyTypeToken(result.keyType());
                metrics.cacheMiss(type, dpi);
                metrics.cacheNegativeHit(layerToken(result.layer()), type, dpi);
                yield Future.succeededFuture(new Resolution(null, null, null));
            }
            case IN_PROGRESS -> {
                // A resolution call for this id is already in flight; skip firing a duplicate and
                // proceed without enrichment (the in-flight call will populate the cache).
                metrics.cacheInProgress(layerToken(result.layer()), keyTypeToken(result.keyType()), dpi);
                yield Future.succeededFuture(new Resolution(null, null, null));
            }
            case MISS -> {
                metrics.cacheMiss(primaryType, dpi);
                cache.putInProgress(keys);
                yield fetchAndCache(properties, bidRequest, keys);
            }
        });
    }

    // Short, stable, lowercase metric token for a cache key type (e.g. THIRD_PARTY -> "third_party").
    private static String keyTypeToken(KeyType type) {
        return type != null ? type.name().toLowerCase(Locale.ROOT) : "unknown";
    }

    // Cache layer that served the outcome: "l1" (Caffeine) or "l2" (Redis).
    private static String layerToken(CacheResult.Layer layer) {
        return layer != null ? layer.name().toLowerCase(Locale.ROOT) : "unknown";
    }

    private Future<Resolution> fetchAndCache(IntentiqIdentityProperties properties,
                                             BidRequest bidRequest,
                                             List<CacheKey> keys) {
        return fetch(properties, bidRequest).map(response -> {
            final List<Eid> eids = extractEids(response);
            if (eids != null && !eids.isEmpty()) {
                cache.put(keys, eids, response.getCttl() != null ? response.getCttl() : 0L);
            } else {
                cache.putNegative(keys, response.getCttl() != null ? response.getCttl() : 0L);
            }
            return new Resolution(eids, response.getAbTestUuid(), response.getTc());
        });
    }

    private Future<IiqResponse> fetch(IntentiqIdentityProperties properties, BidRequest bidRequest) {
        final long timeout = properties.getTimeout() != null ? properties.getTimeout() : DEFAULT_TIMEOUT_MS;
        final long startNanos = System.nanoTime();
        return httpClient.get(resolveUrl(properties, bidRequest), resolveHeaders(bidRequest), timeout)
                .map(response -> {
                    final IiqResponse parsed = mapper.decodeValue(response.getBody(), IiqResponse.class);
                    metrics.apiSuccess(properties.getPartnerId());
                    if (parsed != null && parsed.getTc() != null) {
                        metrics.terminationCause(parsed.getTc(), properties.getPartnerId());
                    }
                    return parsed;
                })
                .onComplete(ignored -> metrics.apiLatency(System.nanoTime() - startNanos, properties.getPartnerId()));
    }

    private static List<Eid> extractEids(IiqResponse response) {
        return response != null && response.getData() != null ? response.getData().getEids() : null;
    }

    private String resolveUrl(IntentiqIdentityProperties properties, BidRequest bidRequest) {
        final String apiEndpoint = properties.getApiEndpoint();
        final StringBuilder url = new StringBuilder(apiEndpoint);
        url.append(apiEndpoint.contains("?") ? '&' : '?').append(IiqParam.AT.key()).append("=39");
        appendIfPresent(url, IiqParam.MI, "10");
        appendIfPresent(url, IiqParam.DPI, properties.getPartnerId());
        appendIfPresent(url, IiqParam.PT, "17");
        appendIfPresent(url, IiqParam.DPN, "1");
        appendIfPresent(url, IiqParam.SRVR_REQ, "true");
        appendIfPresent(url, IiqParam.SOURCE, SOURCE_PBJV);

        final Device device = bidRequest.getDevice();
        if (device != null) {
            appendIfPresent(url, IiqParam.IP, device.getIp());
            appendIfPresent(url, IiqParam.IPV6, device.getIpv6());
            appendIfPresent(url, IiqParam.UAS, device.getUa());
            appendIfPresent(url, IiqParam.UH, buildUaHints(device.getSua()));
            appendDeviceId(url, device);
        }
        appendIfPresent(url, IiqParam.REF, resolveRef(bidRequest));
        appendIfPresent(url, IiqParam.IIQUID, resolveIiqUid(bidRequest.getUser()));
        appendConsent(url, bidRequest);

        return url.toString();
    }

    private static void appendConsent(StringBuilder url, BidRequest bidRequest) {
        final Regs regs = bidRequest.getRegs();
        if (regs != null) {
            appendIfPresent(url, IiqParam.GDPR, resolveGdpr(regs));
            appendIfPresent(url, IiqParam.US_PRIVACY, resolveUsPrivacy(regs));
            appendIfPresent(url, IiqParam.GPP, regs.getGpp());
            // Forwarded ahead of backend support so the GPP section ids are available if the backend adds gpp_sid.
            appendIfPresent(url, IiqParam.GPP_SID, StringUtils.join(regs.getGppSid(), ','));
        }
    }

    private static MultiMap resolveHeaders(BidRequest bidRequest) {
        final MultiMap headers = HttpUtil.headers();
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, GDPR_CONSENT_HEADER, resolveConsent(bidRequest.getUser()));
        return headers;
    }

    private static String resolveGdpr(Regs regs) {
        final Integer gdpr = regs.getGdpr() != null ? regs.getGdpr()
                : regs.getExt() != null ? regs.getExt().getGdpr() : null;
        return gdpr != null ? String.valueOf(gdpr) : null;
    }

    private static String resolveUsPrivacy(Regs regs) {
        if (StringUtils.isNotBlank(regs.getUsPrivacy())) {
            return regs.getUsPrivacy();
        }
        return regs.getExt() != null ? regs.getExt().getUsPrivacy() : null;
    }

    private static String resolveConsent(User user) {
        if (user == null) {
            return null;
        }
        if (StringUtils.isNotBlank(user.getConsent())) {
            return user.getConsent();
        }
        return user.getExt() != null ? user.getExt().getConsent() : null;
    }

    private static void appendIfPresent(StringBuilder url, IiqParam param, String value) {
        if (StringUtils.isNotBlank(value)) {
            url.append('&').append(param.key()).append('=').append(encodeComponent(value));
        }
    }

    // Builds the `uh` UA client-hints JSON from OpenRTB device.sua, matching the IntentIQ backend's
    // UA-CH hint format: numeric-keyed (0-8) UA-CH values, brands sorted, major vs full version.
    private String buildUaHints(UserAgent sua) {
        // The IntentIQ backend consumes hints only for high-entropy client hints (sua.source == 2).
        if (sua == null || !Integer.valueOf(2).equals(sua.getSource())) {
            return null;
        }
        final Map<String, String> hints = new LinkedHashMap<>();
        appendBrowserHints(hints, sua.getBrowsers());
        if (sua.getMobile() != null) {
            hints.put("1", "?" + sua.getMobile());
        }
        appendPlatformHints(hints, sua.getPlatform());
        putQuoted(hints, "3", sua.getArchitecture());
        putQuoted(hints, "4", sua.getBitness());
        putQuoted(hints, "5", sua.getModel());
        return hints.isEmpty() ? null : mapper.encodeToString(hints);
    }

    private static void appendBrowserHints(Map<String, String> hints, List<BrandVersion> browsers) {
        if (browsers == null) {
            return;
        }
        // Sorted by brand to match the IntentIQ backend's hint formatting (deterministic hint string).
        final TreeMap<String, String> majorByBrand = new TreeMap<>();
        final TreeMap<String, String> fullByBrand = new TreeMap<>();
        for (BrandVersion browser : browsers) {
            if (browser == null || StringUtils.isBlank(browser.getBrand())
                    || browser.getVersion() == null || browser.getVersion().isEmpty()) {
                continue;
            }
            final String fullVersion = String.join(".", browser.getVersion());
            final int dot = fullVersion.indexOf('.');
            majorByBrand.put(browser.getBrand(), dot > 0 ? fullVersion.substring(0, dot) : fullVersion);
            fullByBrand.put(browser.getBrand(), fullVersion);
        }
        putBrandList(hints, "0", majorByBrand);
        putBrandList(hints, "8", fullByBrand);
    }

    private static void putBrandList(Map<String, String> hints, String key, TreeMap<String, String> brandToVersion) {
        if (brandToVersion.isEmpty()) {
            return;
        }
        hints.put(key, brandToVersion.entrySet().stream()
                .map(entry -> quote(entry.getKey()) + ";v=" + quote(entry.getValue()))
                .collect(Collectors.joining(", ")));
    }

    private static void appendPlatformHints(Map<String, String> hints, BrandVersion platform) {
        if (platform == null || StringUtils.isBlank(platform.getBrand())) {
            return;
        }
        hints.put("2", quote(platform.getBrand()));
        if (platform.getVersion() != null && !platform.getVersion().isEmpty()) {
            hints.put("6", quote(String.join(".", platform.getVersion())));
        }
    }

    private static void putQuoted(Map<String, String> hints, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            hints.put(key, quote(value));
        }
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }

    private static void appendDeviceId(StringBuilder url, Device device) {
        final String ifa = device.getIfa();
        if (StringUtils.isBlank(ifa) || Integer.valueOf(1).equals(device.getLmt())) {
            return;
        }

        final Integer deviceType = device.getDevicetype();
        final boolean ctv = deviceType != null && (deviceType == 3 || deviceType == 7);
        // CTV ids (idtype 8) must be uppercase; MAID/AAID (idtype 4) is case-insensitive.
        final String pcid = ctv ? ifa.toUpperCase(Locale.ROOT) : ifa;
        url.append('&').append(IiqParam.PCID.key()).append('=').append(encodeComponent(pcid))
                .append('&').append(IiqParam.IDTYPE.key()).append('=').append(ctv ? 8 : 4);
    }

    private static String resolveRef(BidRequest bidRequest) {
        final Site site = bidRequest.getSite();
        if (site != null) {
            return StringUtils.isNotBlank(site.getDomain()) ? site.getDomain() : site.getPage();
        }
        final App app = bidRequest.getApp();
        if (app != null) {
            return StringUtils.isNotBlank(app.getBundle()) ? app.getBundle() : app.getName();
        }
        return null;
    }

    private static String resolveIiqUid(User user) {
        if (user == null || user.getEids() == null) {
            return null;
        }
        return user.getEids().stream()
                .filter(eid -> eid != null && IIQ_SOURCE.equals(eid.getSource()))
                .map(Eid::getUids)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(Uid::getId)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private static String encodeComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static AuctionRequestPayload enrichUserEids(AuctionRequestPayload payload, List<Eid> resolvedEids) {
        final BidRequest bidRequest = payload.bidRequest();
        final User existingUser = bidRequest.getUser() != null ? bidRequest.getUser() : User.builder().build();

        final List<Eid> mergedEids = new ArrayList<>();
        if (existingUser.getEids() != null) {
            mergedEids.addAll(existingUser.getEids());
        }
        mergedEids.addAll(resolvedEids);

        final User enrichedUser = existingUser.toBuilder().eids(mergedEids).build();
        return AuctionRequestPayloadImpl.of(bidRequest.toBuilder().user(enrichedUser).build());
    }

    private static InvocationResult<AuctionRequestPayload> update(PayloadUpdate<AuctionRequestPayload> payloadUpdate,
                                                                  IntentiqIdentityModuleContext context) {
        return InvocationResultImpl.<AuctionRequestPayload>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.update)
                .payloadUpdate(payloadUpdate)
                .moduleContext(context)
                .build();
    }

    private static InvocationResult<AuctionRequestPayload> noAction(IntentiqIdentityModuleContext context) {
        return InvocationResultImpl.<AuctionRequestPayload>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.no_action)
                .moduleContext(context)
                .build();
    }

    @Override
    public String code() {
        return CODE;
    }

    private record Resolution(List<Eid> eids, String abTestUuid, Long terminationCause) {
    }

    @Data
    @NoArgsConstructor
    static class IiqResponse {

        @JsonDeserialize(using = LenientIiqDataDeserializer.class)
        IiqData data;

        Long cttl;

        String abTestUuid;

        Long tc;
    }

    @Data
    @NoArgsConstructor
    static class IiqData {

        List<Eid> eids;
    }

    /**
     * IntentIQ returns {@code data} as an object on a hit but as an empty string ({@code ""}) on an
     * empty or invalid response. Tolerate the non-object form by treating it as absent rather than
     * failing the whole parse (which would mask a valid empty response as an API error).
     */
    static class LenientIiqDataDeserializer extends JsonDeserializer<IiqData> {

        @Override
        public IiqData deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            final JsonNode node = parser.readValueAsTree();
            if (node == null || !node.isObject()) {
                return null;
            }
            return parser.getCodec().treeToValue(node, IiqData.class);
        }
    }
}
