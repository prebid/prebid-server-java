package org.prebid.server.privacy.gdpr.vendorlist;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorList;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class VendorListFileStore {

    private static final Logger logger = LoggerFactory.getLogger(VendorListFileStore.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private static final String JSON_SUFFIX = ".json";

    private final double logSamplingRate;
    private final FileSystem fileSystem;
    private final JacksonMapper mapper;

    public VendorListFileStore(double logSamplingRate,
                               FileSystem fileSystem,
                               JacksonMapper mapper) {

        this.logSamplingRate = logSamplingRate;
        this.fileSystem = Objects.requireNonNull(fileSystem);
        this.mapper = Objects.requireNonNull(mapper);
    }

    Map<Integer, Map<Integer, Vendor>> createCacheFromDisk(String cacheDir) {
        createAndCheckWritePermissionsForCacheDir(cacheDir);
        final Map<Integer, String> versionToFileContent = readFileSystemCache(cacheDir);

        final Map<Integer, Map<Integer, Vendor>> cache = Caffeine.newBuilder()
                .<Integer, Map<Integer, Vendor>>build()
                .asMap();

        for (Map.Entry<Integer, String> versionAndFileContent : versionToFileContent.entrySet()) {
            final VendorList vendorList = VendorListUtil.parseVendorList(versionAndFileContent.getValue(), mapper);

            cache.put(versionAndFileContent.getKey(), vendorList.getVendors());
        }
        return cache;
    }

    private void createAndCheckWritePermissionsForCacheDir(String cacheDir) {
        final FileProps props = fileSystem.existsBlocking(cacheDir) ? fileSystem.propsBlocking(cacheDir) : null;
        if (props == null || !props.isDirectory()) {
            try {
                fileSystem.mkdirsBlocking(cacheDir);
            } catch (FileSystemException e) {
                throw new PreBidException("Cannot create directory: " + cacheDir, e);
            }
        } else if (!Files.isWritable(Paths.get(cacheDir))) {
            throw new PreBidException("No write permissions for directory: " + cacheDir);
        }
    }

    private Map<Integer, String> readFileSystemCache(String cacheDir) {
        return fileSystem.readDirBlocking(cacheDir).stream()
                .filter(filepath -> filepath.endsWith(JSON_SUFFIX))
                .collect(Collectors.toMap(VendorListFileStore::parseCachedFileVersion,
                        filename -> fileSystem.readFileBlocking(filename).toString()));
    }

    Optional<VendorList> getLatestVendorListFromCache(String cacheDir) {
        createAndCheckWritePermissionsForCacheDir(cacheDir);
        return fileSystem.readDirBlocking(cacheDir).stream()
                .filter(filepath -> filepath.endsWith(JSON_SUFFIX))
                .max(Comparator.comparing(VendorListFileStore::parseCachedFileVersion))
                .map(fileSystem::readFileBlocking)
                .map(Buffer::toString)
                .map(content -> VendorListUtil.parseVendorList(content, mapper));
    }

    private static Integer parseCachedFileVersion(String filepath) {
        final String filename = new File(filepath).getName();
        final String filenameWithoutExtension = StringUtils.removeEnd(filename, JSON_SUFFIX);
        return Integer.valueOf(filenameWithoutExtension);
    }

    Future<VendorListResult> saveToFile(VendorListResult vendorListResult, String cacheDir, String generationVersion) {
        final int version = vendorListResult.getVersion();
        final String filepath = new File(cacheDir, version + JSON_SUFFIX).getPath();

        return fileSystem.writeFile(filepath, Buffer.buffer(vendorListResult.getVendorListAsString()))
                .map(vendorListResult)
                .onFailure(error -> conditionalLogger.error(
                        "Could not create new vendor list for version %s.%s, file: %s, trace: %s".formatted(
                                generationVersion, version, filepath, ExceptionUtils.getStackTrace(error.getCause())),
                        logSamplingRate));
    }

    Map<Integer, Vendor> readFallbackVendorList(String fallbackVendorListPath) {
        if (StringUtils.isBlank(fallbackVendorListPath)) {
            return null;
        }

        final String vendorListContent = fileSystem.readFileBlocking(fallbackVendorListPath).toString();
        final VendorList vendorList = VendorListUtil.parseVendorList(vendorListContent, mapper);
        if (!VendorListUtil.vendorListIsValid(vendorList)) {
            throw new PreBidException("Fallback vendor list parsed but has invalid data: " + vendorListContent);
        }

        return vendorList.getVendors();
    }
}
