package org.prebid.server.privacy.gdpr.vendorlist;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
public abstract class VendorListService<T, V> {

    private static final Logger logger = LoggerFactory.getLogger(VendorListService.class);

    private static final String JSON_SUFFIX = ".json";
    private static final String VERSION_PLACEHOLDER = "{VERSION}";
    private static final Integer EXPIRE_DAY_CACHE_DURATION = 5;

    private final String cacheDir;
    private final String endpointTemplate;
    private final int defaultTimeoutMs;
    private final FileSystem fileSystem;
    private final HttpClient httpClient;
    private final Metrics metrics;
    protected final JacksonMapper mapper;

    protected final Set<Integer> knownVendorIds;

    /**
     * This is memory/performance optimized model slice:
     * map of vendor list version -> map of vendor ID -> Vendors
     */
    protected final Map<Integer, Map<Integer, V>> cache;

    public VendorListService(String cacheDir,
                             String endpointTemplate,
                             int defaultTimeoutMs,
                             Integer gdprHostVendorId,
                             BidderCatalog bidderCatalog,
                             FileSystem fileSystem,
                             HttpClient httpClient,
                             Metrics metrics,
                             JacksonMapper mapper) {

        this.cacheDir = Objects.requireNonNull(cacheDir);
        this.endpointTemplate = Objects.requireNonNull(endpointTemplate);
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.fileSystem = Objects.requireNonNull(fileSystem);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.metrics = Objects.requireNonNull(metrics);
        this.mapper = Objects.requireNonNull(mapper);

        knownVendorIds = knownVendorIds(gdprHostVendorId, bidderCatalog);

        createAndCheckWritePermissionsFor(fileSystem, cacheDir);
        cache = Objects.requireNonNull(createCache(fileSystem, cacheDir));
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
                throw new PreBidException(String.format("Cannot create directory: %s", dir), e);
            }
        } else if (!Files.isWritable(Paths.get(dir))) {
            throw new PreBidException(String.format("No write permissions for directory: %s", dir));
        }
    }

    /**
     * Creates vendorList object from string content or throw {@link PreBidException}.
     */
    protected abstract T toVendorList(String content);

    /**
     * Returns a Map of vendor id to Vendors.
     */
    protected abstract Map<Integer, V> filterVendorIdToVendors(T vendorList);

    /**
     * Verifies all significant fields of given {@link T} object.
     */
    protected abstract boolean isValid(T vendorList);

    /**
     * Returns the version of TCF which {@link VendorListService} implementation deals with.
     */
    protected abstract int getTcfVersion();

    private static Set<Integer> knownVendorIds(Integer gdprHostVendorId, BidderCatalog bidderCatalog) {
        final Set<Integer> knownVendorIds = bidderCatalog.knownVendorIds();

        // add host vendor ID (used in /setuid and /cookie_sync endpoint handlers)
        if (gdprHostVendorId != null) {
            knownVendorIds.add(gdprHostVendorId);
        }

        return Collections.unmodifiableSet(knownVendorIds);
    }

    /**
     * Creates the cache from previously downloaded vendor lists.
     */
    private Map<Integer, Map<Integer, V>> createCache(FileSystem fileSystem, String cacheDir) {
        final Map<String, String> versionToFileContent = readFileSystemCache(fileSystem, cacheDir);

        final Map<Integer, Map<Integer, V>> cache = Caffeine.newBuilder()
                .expireAfterWrite(EXPIRE_DAY_CACHE_DURATION, TimeUnit.DAYS)
                .<Integer, Map<Integer, V>>build()
                .asMap();

        for (Map.Entry<String, String> versionAndFileContent : versionToFileContent.entrySet()) {
            final T vendorList = toVendorList(versionAndFileContent.getValue());
            final Map<Integer, V> vendorIdToVendors = filterVendorIdToVendors(vendorList);

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

    /**
     * Returns a map with vendor ID as a key and a set of purposes as a value for given vendor list version.
     */
    public Future<Map<Integer, V>> forVersion(int version) {
        if (version <= 0) {
            return Future.failedFuture(
                    String.format("TCF %d vendor list for version %d not valid.", getTcfVersion(), version));
        }

        final Map<Integer, V> idToVendor = cache.get(version);
        if (idToVendor != null) {
            return Future.succeededFuture(idToVendor);
        } else {
            final int tcf = getTcfVersion();
            metrics.updatePrivacyTcfVendorListMissingMetric(tcf, version);

            logger.info("TCF {0} vendor list for version {1} not found, started downloading.", tcf, version);
            fetchNewVendorListFor(version);

            return Future.failedFuture(
                    String.format("TCF %d vendor list for version %d not fetched yet, try again later.", tcf, version));
        }
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
    private VendorListResult<T> processResponse(HttpClientResponse response, int version) {
        final int statusCode = response.getStatusCode();
        if (statusCode != 200) {
            throw new PreBidException(String.format("HTTP status code %d", statusCode));
        }

        final String body = response.getBody();
        final T vendorList = toVendorList(body);

        // we should care on obtained vendor list, because it'll be saved and never be downloaded again
        // while application is running
        if (!isValid(vendorList)) {
            throw new PreBidException(String.format("Fetched vendor list parsed but has invalid data: %s", body));
        }

        return VendorListResult.of(version, body, vendorList);
    }

    /**
     * Saves given vendor list on file system.
     */
    private Future<VendorListResult<T>> saveToFile(VendorListResult<T> vendorListResult) {
        final Promise<VendorListResult<T>> promise = Promise.promise();
        final int version = vendorListResult.getVersion();
        final String filepath = new File(cacheDir, version + JSON_SUFFIX).getPath();

        fileSystem.writeFile(filepath, Buffer.buffer(vendorListResult.getVendorListAsString()), result -> {
            if (result.succeeded()) {
                promise.complete(vendorListResult);
            } else {
                logger.error("Could not create new vendor list for version {0}, file: {1}", result.cause(), version,
                        filepath);
                promise.fail(result.cause());
            }
        });

        return promise.future();
    }

    private Void updateCache(VendorListResult<T> vendorListResult) {
        final int version = vendorListResult.getVersion();

        cache.put(version, filterVendorIdToVendors(vendorListResult.getVendorList()));

        final int tcf = getTcfVersion();
        metrics.updatePrivacyTcfVendorListOkMetric(tcf, version);

        logger.info("Created new TCF {0} vendor list for version {0}", tcf, version);
        return null;
    }

    /**
     * Handles errors occurred while HTTP or File System processing.
     */
    private Void handleError(Throwable exception, int version) {
        final int tcf = getTcfVersion();
        metrics.updatePrivacyTcfVendorListErrorMetric(tcf, version);

        if (logger.isDebugEnabled()) {
            logger.debug("Error while obtaining TCF {0} vendor list for version {1}",
                    exception, tcf, version);
        } else {
            logger.warn("Error while obtaining TCF {0} vendor list for version {1}: {2}",
                    tcf, version, exception.getMessage());
        }

        return null;
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class VendorListResult<T> {

        int version;

        String vendorListAsString;

        T vendorList;
    }
}
