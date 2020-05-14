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

    /**
     * This is memory/performance optimized model slice:
     * map of vendor list version -> map of vendor ID -> Vendors
     */
    protected final Map<Integer, Map<Integer, V>> cache;
    private final FileSystem fileSystem;
    private final HttpClient httpClient;
    private final String endpointTemplate;
    private final int defaultTimeoutMs;
    private final String cacheDir;
    protected Set<Integer> knownVendorIds;
    protected JacksonMapper mapper;

    public VendorListService(String cacheDir,
                             String endpointTemplate,
                             int defaultTimeoutMs,
                             Integer gdprHostVendorId,
                             BidderCatalog bidderCatalog,
                             FileSystem fileSystem,
                             HttpClient httpClient,
                             JacksonMapper mapper) {

        this.cacheDir = Objects.requireNonNull(cacheDir);
        this.endpointTemplate = Objects.requireNonNull(endpointTemplate);
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.fileSystem = Objects.requireNonNull(fileSystem);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.mapper = Objects.requireNonNull(mapper);

        createAndCheckWritePermissionsFor(fileSystem, cacheDir);

        this.knownVendorIds = knownVendorIds(gdprHostVendorId, bidderCatalog);
        this.cache = Objects.requireNonNull(createCache(fileSystem, cacheDir));
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
    protected Map<String, String> readFileSystemCache(FileSystem fileSystem, String dir) {
        return fileSystem.readDirBlocking(dir).stream()
                .filter(filepath -> filepath.endsWith(JSON_SUFFIX))
                .collect(Collectors.toMap(filepath -> StringUtils.removeEnd(new File(filepath).getName(), JSON_SUFFIX),
                        filename -> fileSystem.readFileBlocking(filename).toString()));
    }

    /**
     * Returns a map with vendor ID as a key and a set of purposes as a value for given vendor list version.
     */
    public Future<Map<Integer, V>> forVersion(int version) {
        final Map<Integer, V> idToVendor = cache.get(version);
        if (idToVendor != null) {
            return Future.succeededFuture(idToVendor);
        } else {
            logger.info("Vendor list for version {0} not found, started downloading.", version);
            fetchNewVendorListFor(version);

            return Future.failedFuture(
                    String.format("Vendor list for version %d not fetched yet, try again later.", version));
        }
    }

    /**
     * Proceeds obtaining new vendor list from HTTP resource.
     */
    private void fetchNewVendorListFor(int version) {
        final String url = endpointTemplate.replace(VERSION_PLACEHOLDER, String.valueOf(version));

        httpClient.get(url, defaultTimeoutMs)
                .compose(response -> processResponse(response, version))
                .compose(vendorListResult -> saveToFileAndUpdateCache(vendorListResult, version))
                .recover(exception -> failResponse(exception, version));
    }

    /**
     * Handles {@link HttpClientResponse}, analyzes response status
     * and creates {@link Future} with {@link VendorListResult} from body content
     * or throws {@link PreBidException} in case of errors.
     */
    private Future<VendorListResult<T>> processResponse(HttpClientResponse response, int version) {
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

        return Future.succeededFuture(VendorListResult.of(version, body, vendorList));
    }

    private Future<Void> saveToFileAndUpdateCache(VendorListResult<T> vendorListResult, int version) {
        final T vendorList = vendorListResult.getVendorList();

        saveToFile(vendorListResult.getVendorListAsString(), version)
                // add new entry to in-memory cache
                .map(ignored -> cache.put(version, filterVendorIdToVendors(vendorList)));

        return Future.succeededFuture();
    }

    /**
     * Saves on file system given content as vendor list of specified version.
     */
    private Future<Void> saveToFile(String content, int version) {
        final Promise<Void> promise = Promise.promise();
        final String filepath = new File(cacheDir, version + JSON_SUFFIX).getPath();

        fileSystem.writeFile(filepath, Buffer.buffer(content), result -> {
            if (result.succeeded()) {
                logger.info("Created new vendor list for version {0}, file: {1}", version, filepath);
                promise.complete();
            } else {
                logger.warn("Could not create new vendor list for version {0}, file: {1}", result.cause(), version,
                        filepath);
                promise.fail(result.cause());
            }
        });

        return promise.future();
    }

    /**
     * Handles errors occurred while HTTP request or response processing.
     */
    private static Future<Void> failResponse(Throwable exception, int version) {
        logger.warn("Error fetching vendor list via HTTP for version {0} with message: {1}",
                version, exception.getMessage());
        logger.debug("Error fetching vendor list via HTTP for version {0}", exception, version);
        return Future.failedFuture(exception);
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class VendorListResult<T> {

        int version;

        String vendorListAsString;

        T vendorList;
    }
}
