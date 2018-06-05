package org.prebid.server.gdpr.vendorlist;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.execution.Timeout;
import org.prebid.server.gdpr.vendorlist.proto.VendorListInfo;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class FileCacheVendorList implements VendorList {

    private static final Logger logger = LoggerFactory.getLogger(FileCacheVendorList.class);
    private static final String JSON_SUFFIX = ".json";

    private final FileSystem fileSystem;
    private final String cacheDir;
    private final VendorList delegate;

    public FileCacheVendorList(FileSystem fileSystem, String cacheDir, VendorList delegate) {
        this.fileSystem = Objects.requireNonNull(fileSystem);
        this.cacheDir = Objects.requireNonNull(cacheDir);
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public Future<VendorListInfo> forVersion(int version, Timeout timeout) {
        final String content = getFromCache(version);
        final VendorListInfo vendorListInfo = content != null ? toVendorListInfo(content) : null;

        return vendorListInfo != null
                ? Future.succeededFuture(vendorListInfo)
                : delegate.forVersion(version, timeout)
                .compose(foundVendorListInfo -> saveToCache(version, foundVendorListInfo));
    }

    private String getFromCache(int version) {
        final String filepath = filepath(version);
        return fileSystem.existsBlocking(filepath) ? fileSystem.readFileBlocking(filepath).toString() : null;
    }

    private Future<VendorListInfo> saveToCache(int version, VendorListInfo vendorListInfo) {
        try {
            final String content = Json.mapper.writeValueAsString(vendorListInfo);
            fileSystem.writeFileBlocking(filepath(version), Buffer.buffer(content));
        } catch (JsonProcessingException e) {
            final String message = String.format("Cannot encode vendor list: %s", e.getMessage());

            logger.warn(message, e);
            return Future.failedFuture(message);
        }
        return Future.succeededFuture(vendorListInfo);
    }

    private String filepath(int version) {
        return new File(cacheDir, version + JSON_SUFFIX).getPath();
    }

    private VendorListInfo toVendorListInfo(String content) {
        try {
            return Json.mapper.readValue(content, VendorListInfo.class);
        } catch (IOException e) {
            logger.warn(String.format("Cannot parse vendor list from: %s", content), e);
            return null; // just suppress any errors, we'll rewrite this vendor list file from delegate
        }
    }
}
