package org.prebid.server.gdpr.vendorlist;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.gdpr.vendorlist.proto.Vendor;
import org.prebid.server.gdpr.vendorlist.proto.VendorList;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
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

    private static final String JSON_SUFFIX = ".json";
    private static final String VERSION_PLACEHOLDER = "{VERSION}";

    private final FileSystem fileSystem;
    private final String cacheDir;

    private final HttpClient httpClient;
    private final String endpointTemplate;
    private final int defaultTimeoutMs;

    private final Set<Integer> knownVendorIds;
    /**
     * This is memory/performance optimized {@link VendorList} model slice:
     * map of vendor list version -> map of vendor ID -> set of purposes
     */
    private final Map<Integer, Map<Integer, Set<Integer>>> cache;

    private VendorListService(FileSystem fileSystem, String cacheDir,
                              HttpClient httpClient, String endpointTemplate, int defaultTimeoutMs,
                              Set<Integer> knownVendorIds, Map<Integer, Map<Integer, Set<Integer>>> cache) {
        this.fileSystem = fileSystem;
        this.cacheDir = cacheDir;

        this.httpClient = httpClient;
        this.endpointTemplate = endpointTemplate;
        this.defaultTimeoutMs = defaultTimeoutMs;

        this.knownVendorIds = knownVendorIds;
        this.cache = cache;
    }

    public static VendorListService create(FileSystem fileSystem, String cacheDir, HttpClient httpClient,
                                           String endpointTemplate, int defaultTimeoutMs, Integer gdprHostVendorId,
                                           BidderCatalog bidderCatalog) {
        Objects.requireNonNull(fileSystem);
        Objects.requireNonNull(cacheDir);
        Objects.requireNonNull(httpClient);
        Objects.requireNonNull(endpointTemplate);
        Objects.requireNonNull(bidderCatalog);

        createAndCheckWritePermissionsFor(fileSystem, cacheDir);

        final Set<Integer> knownVendorIds = knownVendorIds(gdprHostVendorId, bidderCatalog);
        final Map<Integer, Map<Integer, Set<Integer>>> cache = createCache(fileSystem, cacheDir, knownVendorIds);

        return new VendorListService(fileSystem, cacheDir, httpClient, endpointTemplate, defaultTimeoutMs,
                knownVendorIds, cache);
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
     * Fetches Vendor IDs from all known bidders
     */
    private static Set<Integer> knownVendorIds(Integer gdprHostVendorId, BidderCatalog bidderCatalog) {
        final Set<Integer> vendorIds = bidderCatalog.names().stream()
                .map(bidderCatalog::metaInfoByName)
                .map(metaInfo -> metaInfo.info().getGdpr().getVendorId())
                .collect(Collectors.toSet());

        // add host vendor ID (used in /setuid and /cookie_sync endpoint handlers)
        if (gdprHostVendorId != null) {
            vendorIds.add(gdprHostVendorId);
        }

        return Collections.unmodifiableSet(vendorIds);
    }

    /**
     * Creates cache from previously downloaded vendor lists.
     */
    private static Map<Integer, Map<Integer, Set<Integer>>> createCache(FileSystem fileSystem, String cacheDir,
                                                                        Set<Integer> knownVendorIds) {
        final Map<String, String> versionToFileContent = readFileSystemCache(fileSystem, cacheDir);

        final Map<Integer, Map<Integer, Set<Integer>>> cache = new ConcurrentHashMap<>(versionToFileContent.size());
        for (Map.Entry<String, String> entry : versionToFileContent.entrySet()) {
            final VendorList vendorList = toVendorList(entry.getValue());
            final Map<Integer, Set<Integer>> vendorIdToPurposes = mapVendorIdToPurposes(vendorList.getVendors(),
                    knownVendorIds);

            cache.put(Integer.valueOf(entry.getKey()), vendorIdToPurposes);
        }
        return cache;
    }

    /**
     * Reads files with .json extension in configured directory and
     * returns a {@link Map} where key is a file name without .json extension and value is file content.
     */
    private static Map<String, String> readFileSystemCache(FileSystem fileSystem, String dir) {
        return fileSystem.readDirBlocking(dir).stream()
                .filter(filepath -> filepath.endsWith(JSON_SUFFIX))
                .collect(Collectors.toMap(filepath -> StringUtils.removeEnd(new File(filepath).getName(), JSON_SUFFIX),
                        filename -> fileSystem.readFileBlocking(filename).toString()));
    }

    /**
     * Creates {@link VendorList} object from string content or throw {@link PreBidException}.
     */
    private static VendorList toVendorList(String content) {
        try {
            return Json.mapper.readValue(content, VendorList.class);
        } catch (IOException e) {
            final String message = String.format("Cannot parse vendor list from: %s", content);

            logger.error(message, e);
            throw new PreBidException(message, e);
        }
    }

    /**
     * Returns a map with vendor ID as a key and set of purposes as a value from given list of {@link Vendor}s.
     */
    private static Map<Integer, Set<Integer>> mapVendorIdToPurposes(List<Vendor> vendors, Set<Integer> knownVendorIds) {
        return vendors.stream()
                .filter(vendor -> knownVendorIds.contains(vendor.getId())) // optimize cache to use only known vendors
                .collect(Collectors.toMap(Vendor::getId, Vendor::combinedPurposes));
    }

    /**
     * Returns a map with vendor ID as a key and a set of purposes as a value for given vendor list version.
     */
    public Future<Map<Integer, Set<Integer>>> forVersion(int version) {
        final Map<Integer, Set<Integer>> vendorIdToPurposes = cache.get(version);
        if (vendorIdToPurposes != null) {
            return Future.succeededFuture(vendorIdToPurposes);
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
                .compose(this::saveToFileAndUpdateCache)
                .recover(exception -> failResponse(exception, version));
    }

    /**
     * Handles {@link HttpClientResponse}, analyzes response status
     * and creates {@link Future} with {@link VendorListResult} from body content
     * or throws {@link PreBidException} in case of errors.
     */
    private static Future<VendorListResult> processResponse(HttpClientResponse response, int version) {
        final int statusCode = response.getStatusCode();
        if (statusCode != 200) {
            throw new PreBidException(String.format("HTTP status code %d", statusCode));
        }

        final String body = response.getBody();
        final VendorList vendorList;
        try {
            vendorList = Json.decodeValue(body, VendorList.class);
        } catch (DecodeException e) {
            throw new PreBidException(String.format("Cannot parse response: %s", body), e);
        }

        // we should care on obtained vendor list, because it'll be saved and never be downloaded again
        // while application is running
        if (!isValid(vendorList)) {
            throw new PreBidException(String.format("Fetched vendor list parsed but has invalid data: %s", body));
        }

        return Future.succeededFuture(VendorListResult.of(version, body, vendorList));
    }

    /**
     * Verifies all significant fields of given {@link VendorList} object.
     */
    private static boolean isValid(VendorList vendorList) {
        return vendorList.getVendorListVersion() != null
                && vendorList.getLastUpdated() != null
                && CollectionUtils.isNotEmpty(vendorList.getVendors())
                && vendorList.getVendors().stream()
                .allMatch(vendor -> vendor != null && vendor.getId() != null
                        && (vendor.getPurposeIds() != null || vendor.getLegIntPurposeIds() != null));
    }

    private Future<Void> saveToFileAndUpdateCache(VendorListResult vendorListResult) {
        final VendorList vendorList = vendorListResult.getVendorList();
        final int version = vendorListResult.getVersion();

        saveToFile(vendorListResult.getVendorListAsString(), version)
                // add new entry to in-memory cache
                .map(r -> cache.put(version, mapVendorIdToPurposes(vendorList.getVendors(), knownVendorIds)));

        return Future.succeededFuture();
    }

    /**
     * Saves on file system given content as vendor list of specified version.
     */
    private Future<Void> saveToFile(String content, int version) {
        final Future<Void> future = Future.future();
        final String filepath = new File(cacheDir, version + JSON_SUFFIX).getPath();

        fileSystem.writeFile(filepath, Buffer.buffer(content), result -> {
            if (result.succeeded()) {
                logger.info("Created new vendor list for version {0}, file: {1}", version, filepath);
                future.complete();
            } else {
                logger.warn("Could not create new vendor list for version {0}, file: {1}", result.cause(), version,
                        filepath);
                future.fail(result.cause());
            }
        });

        return future;
    }

    /**
     * Handles errors occurred while HTTP request or response processing.
     */
    private static Future<Void> failResponse(Throwable exception, int version) {
        logger.warn("Error fetching vendor list via HTTP for version {0}", exception, version);
        return Future.failedFuture(exception);
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class VendorListResult {

        int version;

        String vendorListAsString;

        VendorList vendorList;
    }
}
