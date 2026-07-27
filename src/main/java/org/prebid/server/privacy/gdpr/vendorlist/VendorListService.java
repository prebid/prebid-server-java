package org.prebid.server.privacy.gdpr.vendorlist;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorList;
import org.prebid.server.util.Uri;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Works with GDPR Vendor List.
 * <p>
 * There are three main places for the vendor list:
 * - in-memory cache;
 * - file system (persistent cache);
 * - remote web resource (original source);
 * <p>
 * So, on service creation we initialize in-memory cache from previously loaded vendor list on file system.
 * If request asks version that is absent in cache, we respond with failed result but start background process
 * to download new version and then put it to cache.
 */
public class VendorListService {

    private static final Logger logger = LoggerFactory.getLogger(VendorListService.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private static final int TCF_VERSION = 2;

    private static final String VERSION_PLACEHOLDER = "VERSION";

    private final double logSamplingRate;
    private final String cacheDir;
    private final Uri endpoint;
    private final int defaultTimeoutMs;
    private final long refreshMissingListPeriodMs;
    private final boolean deprecated;
    private final Vertx vertx;
    private final HttpClient httpClient;
    private final Metrics metrics;
    private final String generationVersion;
    private final VendorListFetchThrottler fetchThrottler;
    private final VendorListFileStore vendorListFileStore;
    private final JacksonMapper mapper;

    // Map of vendor list version -> map of vendor ID -> Vendors
    private final Map<Integer, Map<Integer, Vendor>> cache;

    private final Map<Integer, Vendor> fallbackVendorList;
    private final Set<Integer> versionsToFallback;

    public VendorListService(double logSamplingRate,
                             String cacheDir,
                             String endpoint,
                             int defaultTimeoutMs,
                             long refreshMissingListPeriodMs,
                             boolean deprecated,
                             String fallbackVendorListPath,
                             Vertx vertx,
                             HttpClient httpClient,
                             Metrics metrics,
                             String generationVersion,
                             VendorListFetchThrottler fetchThrottler,
                             VendorListFileStore vendorListFileStore,
                             JacksonMapper mapper) {

        this.logSamplingRate = logSamplingRate;
        this.cacheDir = Objects.requireNonNull(cacheDir);
        this.endpoint = Uri.of(endpoint);
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.refreshMissingListPeriodMs = refreshMissingListPeriodMs;
        this.deprecated = deprecated;
        this.generationVersion = generationVersion;
        this.vertx = Objects.requireNonNull(vertx);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.metrics = Objects.requireNonNull(metrics);
        this.fetchThrottler = Objects.requireNonNull(fetchThrottler);
        this.vendorListFileStore = Objects.requireNonNull(vendorListFileStore);
        this.mapper = Objects.requireNonNull(mapper);

        cache = Objects.requireNonNull(vendorListFileStore.createCacheFromDisk(cacheDir));

        fallbackVendorList = vendorListFileStore.readFallbackVendorList(fallbackVendorListPath);
        if (deprecated) {
            validateFallbackVendorListIfDeprecatedVersion();
        }
        versionsToFallback = fallbackVendorList != null
                ? ConcurrentHashMap.newKeySet() : null;
    }

    private void validateFallbackVendorListIfDeprecatedVersion() {
        if (Objects.isNull(fallbackVendorList)) {
            throw new PreBidException("No fallback vendorList for deprecated version present");
        }
    }

    /**
     * Returns a map with vendor ID as a key and a set of purposes as a value for given vendor list version.
     */
    public Future<Map<Integer, Vendor>> forVersion(int version) {
        if (version <= 0) {
            return Future.failedFuture("TCF %d vendor list for version %s.%d not valid."
                    .formatted(getTcfVersion(), generationVersion, version));
        }

        final Map<Integer, Vendor> idToVendor = cache.get(version);
        if (idToVendor != null) {
            return Future.succeededFuture(idToVendor);
        }

        final int tcf = getTcfVersion();

        if (shouldFallback(version)) {
            metrics.updatePrivacyTcfVendorListFallbackMetric(tcf);

            return Future.succeededFuture(fallbackVendorList);
        }

        metrics.updatePrivacyTcfVendorListMissingMetric(tcf);

        if (fetchThrottler.registerFetchAttempt(version)) {
            logger.info("TCF {} vendor list for version {}.{} not found, started downloading.",
                    tcf, generationVersion, version);
            fetchNewVendorListFor(version);
        }

        return Future.failedFuture("TCF %d vendor list for version %s.%d not fetched yet, try again later."
                .formatted(tcf, generationVersion, version));
    }

    /**
     * Returns the version of TCF which {@link VendorListService} implementation deals with.
     */
    private int getTcfVersion() {
        return TCF_VERSION;
    }

    private boolean shouldFallback(int version) {
        return deprecated || (versionsToFallback != null && versionsToFallback.contains(version));
    }

    /**
     * Proceeds obtaining new vendor list from HTTP resource.
     */
    private void fetchNewVendorListFor(int version) {
        final String url = endpoint.replaceMacro(VERSION_PLACEHOLDER, String.valueOf(version)).expand();

        httpClient.get(url, defaultTimeoutMs)
                .map(response -> processResponse(response, version))
                .compose(this::saveToFile)
                .map(this::updateCache)
                .otherwise(exception -> handleError(exception, version));
    }

    /**
     * Handles {@link HttpClientResponse}, analyzes response status
     * and creates {@link Future} with {@link VendorListResult} from body content
     * or throws {@link PreBidException} in case of errors.
     */
    private VendorListResult processResponse(HttpClientResponse response, int version) {
        final int statusCode = response.getStatusCode();

        if (statusCode == HttpResponseStatus.NOT_FOUND.code()) {
            throw new MissingVendorListException(
                    "Remote server could not found vendor list with version " + generationVersion + "." + version);
        } else if (statusCode != HttpResponseStatus.OK.code()) {
            throw new PreBidException("HTTP status code " + statusCode);
        }

        final String body = response.getBody();
        final VendorList vendorList = VendorListUtil.parseVendorList(body, mapper);

        // we should care on obtained vendor list, because it'll be saved and never be downloaded again
        // while application is running
        if (!VendorListUtil.vendorListIsValid(vendorList)) {
            throw new PreBidException("Fetched vendor list parsed but has invalid data: " + body);
        }

        fetchThrottler.succeedFetchAttempt(version);
        return VendorListResult.of(version, body, vendorList);
    }

    private Future<VendorListResult> saveToFile(VendorListResult vendorListResult) {
        return vendorListFileStore.saveToFile(vendorListResult, cacheDir, generationVersion);
    }

    private Void updateCache(VendorListResult vendorListResult) {
        final int version = vendorListResult.getVersion();

        cache.put(version, vendorListResult.getVendorList().getVendors());

        final int tcf = getTcfVersion();

        metrics.updatePrivacyTcfVendorListOkMetric(tcf);

        logger.info("Created new TCF {} vendor list for version {}.{}", tcf, generationVersion, version);

        stopUsingFallbackForVersion(version);

        return null;
    }

    /**
     * Handles errors occurred while HTTP or File System processing.
     */
    private Void handleError(Throwable exception, int version) {
        final int tcf = getTcfVersion();

        metrics.updatePrivacyTcfVendorListErrorMetric(tcf);

        if (logger.isDebugEnabled()) {
            conditionalLogger.debug(
                    "Error while obtaining TCF %s vendor list for version %s.%s, trace: %s"
                            .formatted(tcf, generationVersion, version, ExceptionUtils.getStackTrace(exception)),
                    logSamplingRate);
        } else {
            conditionalLogger.warn(
                    "Error while obtaining TCF %s vendor list for version %s.%s: %s"
                            .formatted(tcf, generationVersion, version, exception.getMessage()),
                    logSamplingRate);
        }

        startUsingFallbackForVersion(version);

        return null;
    }

    private void startUsingFallbackForVersion(int version) {
        if (versionsToFallback == null) {
            return;
        }

        versionsToFallback.add(version);

        vertx.setTimer(refreshMissingListPeriodMs, ignored -> fetchNewVendorListFor(version));
    }

    private void stopUsingFallbackForVersion(int version) {
        if (versionsToFallback == null) {
            return;
        }

        versionsToFallback.remove(version);
    }

    private static class MissingVendorListException extends RuntimeException {

        MissingVendorListException(String message) {
            super(message);
        }
    }
}
