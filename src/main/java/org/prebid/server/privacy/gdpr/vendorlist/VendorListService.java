package org.prebid.server.privacy.gdpr.vendorlist;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorList;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

    private static final String JSON_SUFFIX = ".json";
    private static final String VERSION_PLACEHOLDER = "{VERSION}";

    private final double logSamplingRate;
    private final String cacheDir;
    private final String endpointTemplate;
    private final int defaultTimeoutMs;
    private final long refreshMissingListPeriodMs;
    private final boolean deprecated;
    private final Vertx vertx;
    private final FileSystem fileSystem;
    private final HttpClient httpClient;
    private final Metrics metrics;
    private final String generationVersion;
    protected final JacksonMapper mapper;

    /**
     * This is memory/performance optimized model slice:
     * map of vendor list version -> map of vendor ID -> Vendors
     */
    private final Map<Integer, Map<Integer, Vendor>> cache;

    private final Map<Integer, Vendor> fallbackVendorList;
    private final Set<Integer> versionsToFallback;
    private final VendorListFetchThrottler fetchThrottler;

    public VendorListService(double logSamplingRate,
                             String cacheDir,
                             String endpointTemplate,
                             int defaultTimeoutMs,
                             long refreshMissingListPeriodMs,
                             boolean deprecated,
                             String fallbackVendorListPath,
                             Vertx vertx,
                             FileSystem fileSystem,
                             HttpClient httpClient,
                             Metrics metrics,
                             String generationVersion,
                             JacksonMapper mapper,
                             VendorListFetchThrottler fetchThrottler) {

        this.logSamplingRate = logSamplingRate;
        this.cacheDir = Objects.requireNonNull(cacheDir);
        this.endpointTemplate = Objects.requireNonNull(endpointTemplate);
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.refreshMissingListPeriodMs = refreshMissingListPeriodMs;
        this.deprecated = deprecated;
        this.generationVersion = generationVersion;
        this.vertx = Objects.requireNonNull(vertx);
        this.fileSystem = Objects.requireNonNull(fileSystem);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.metrics = Objects.requireNonNull(metrics);
        this.mapper = Objects.requireNonNull(mapper);
        this.fetchThrottler = Objects.requireNonNull(fetchThrottler);

        createAndCheckWritePermissionsFor(fileSystem, cacheDir);
        cache = Objects.requireNonNull(createCache(fileSystem, cacheDir));

        fallbackVendorList = StringUtils.isNotBlank(fallbackVendorListPath)
                ? readFallbackVendorList(fallbackVendorListPath) : null;
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
     * Creates vendorList object from string content or throw {@link PreBidException}.
     */
    private VendorList toVendorList(String content) {
        try {
            return mapper.mapper().readValue(content, VendorList.class);
        } catch (IOException e) {
            final String message = "Cannot parse vendor list from: " + content;

            logger.error(message, e);
            throw new PreBidException(message, e);
        }
    }

    /**
     * Returns a Map of vendor id to Vendors.
     */
    private Map<Integer, Vendor> filterVendorIdToVendors(VendorList vendorList) {
        return vendorList.getVendors().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Verifies all significant fields of given {@link VendorList} object.
     */
    private boolean isValid(VendorList vendorList) {
        return vendorList.getVendorListVersion() != null
                && vendorList.getLastUpdated() != null
                && MapUtils.isNotEmpty(vendorList.getVendors())
                && isValidVendors(vendorList.getVendors().values());
    }

    private static boolean isValidVendors(Collection<Vendor> vendors) {
        return vendors.stream()
                .allMatch(vendor -> vendor != null
                        && vendor.getId() != null
                        && vendor.getPurposes() != null
                        && vendor.getLegIntPurposes() != null
                        && vendor.getFlexiblePurposes() != null
                        && vendor.getSpecialPurposes() != null
                        && vendor.getFeatures() != null
                        && vendor.getSpecialFeatures() != null);
    }

    /**
     * Returns the version of TCF which {@link VendorListService} implementation deals with.
     */
    private int getTcfVersion() {
        return TCF_VERSION;
    }

    /**
     * Creates if doesn't exists and checks write permissions for the given directory.
     */
    private static void createAndCheckWritePermissionsFor(FileSystem fileSystem, String dir) {
        final FileProps props = fileSystem.existsBlocking(dir) ? fileSystem.propsBlocking(dir) : null;
        if (props == null || !props.isDirectory()) {
            try {
                fileSystem.mkdirsBlocking(dir);
            } catch (FileSystemException e) {
                throw new PreBidException("Cannot create directory: " + dir, e);
            }
        } else if (!Files.isWritable(Paths.get(dir))) {
            throw new PreBidException("No write permissions for directory: " + dir);
        }
    }

    /**
     * Creates the cache from previously downloaded vendor lists.
     */
    private Map<Integer, Map<Integer, Vendor>> createCache(FileSystem fileSystem, String cacheDir) {
        final Map<String, String> versionToFileContent = readFileSystemCache(fileSystem, cacheDir);

        final Map<Integer, Map<Integer, Vendor>> cache = Caffeine.newBuilder()
                .<Integer, Map<Integer, Vendor>>build()
                .asMap();

        for (Map.Entry<String, String> versionAndFileContent : versionToFileContent.entrySet()) {
            final VendorList vendorList = toVendorList(versionAndFileContent.getValue());
            final Map<Integer, Vendor> vendorIdToVendors = filterVendorIdToVendors(vendorList);

            cache.put(Integer.valueOf(versionAndFileContent.getKey()), vendorIdToVendors);
        }
        return cache;
    }

    /**
     * Reads files with .json extension in configured directory and
     * returns a {@link Map} where key is a file name without .json extension and value is file content.
     */
    private Map<String, String> readFileSystemCache(FileSystem fileSystem, String dir) {
        return fileSystem.readDirBlocking(dir).stream()
                .filter(filepath -> filepath.endsWith(JSON_SUFFIX))
                .collect(Collectors.toMap(filepath -> StringUtils.removeEnd(new File(filepath).getName(), JSON_SUFFIX),
                        filename -> fileSystem.readFileBlocking(filename).toString()));
    }

    private Map<Integer, Vendor> readFallbackVendorList(String fallbackVendorListPath) {
        final String vendorListContent = fileSystem.readFileBlocking(fallbackVendorListPath).toString();
        final VendorList vendorList = toVendorList(vendorListContent);
        if (!isValid(vendorList)) {
            throw new PreBidException("Fallback vendor list parsed but has invalid data: " + vendorListContent);
        }

        return filterVendorIdToVendors(vendorList);
    }

    private boolean shouldFallback(int version) {
        return deprecated || (versionsToFallback != null && versionsToFallback.contains(version));
    }

    /**
     * Proceeds obtaining new vendor list from HTTP resource.
     */
    private void fetchNewVendorListFor(int version) {
        final String url = endpointTemplate.replace(VERSION_PLACEHOLDER, String.valueOf(version));

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
    private VendorListResult<VendorList> processResponse(HttpClientResponse response, int version) {
        final int statusCode = response.getStatusCode();

        if (statusCode == HttpResponseStatus.NOT_FOUND.code()) {
            throw new MissingVendorListException(
                    "Remote server could not found vendor list with version " + generationVersion + "." + version);
        } else if (statusCode != HttpResponseStatus.OK.code()) {
            throw new PreBidException("HTTP status code " + statusCode);
        }

        final String body = response.getBody();
        final VendorList vendorList = toVendorList(body);

        // we should care on obtained vendor list, because it'll be saved and never be downloaded again
        // while application is running
        if (!isValid(vendorList)) {
            throw new PreBidException("Fetched vendor list parsed but has invalid data: " + body);
        }

        fetchThrottler.succeedFetchAttempt(version);
        return VendorListResult.of(version, body, vendorList);
    }

    /**
     * Saves given vendor list on file system.
     */
    private Future<VendorListResult<VendorList>> saveToFile(VendorListResult<VendorList> vendorListResult) {
        final Promise<VendorListResult<VendorList>> promise = Promise.promise();
        final int version = vendorListResult.getVersion();
        final String filepath = new File(cacheDir, version + JSON_SUFFIX).getPath();

        fileSystem.writeFile(filepath, Buffer.buffer(vendorListResult.getVendorListAsString()), result -> {
            if (result.succeeded()) {
                promise.complete(vendorListResult);
            } else {
                conditionalLogger.error(
                        "Could not create new vendor list for version %s.%s, file: %s, trace: %s".formatted(
                                generationVersion, version, filepath, ExceptionUtils.getStackTrace(result.cause())),
                        logSamplingRate);
                promise.fail(result.cause());
            }
        });

        return promise.future();
    }

    private Void updateCache(VendorListResult<VendorList> vendorListResult) {
        final int version = vendorListResult.getVersion();

        cache.put(version, filterVendorIdToVendors(vendorListResult.getVendorList()));

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

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class VendorListResult<T> {

        int version;

        String vendorListAsString;

        T vendorList;
    }

    private static class MissingVendorListException extends RuntimeException {

        MissingVendorListException(String message) {
            super(message);
        }
    }
}
