package org.prebid.server.hooks.modules.greenbids.real.time.data.config;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import com.maxmind.db.Reader;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import com.maxmind.geoip2.DatabaseReader;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.vertx.Initializable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

public class DatabaseReaderFactory implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseReaderFactory.class);

    private final GreenbidsRealTimeDataProperties properties;

    private final Vertx vertx;

    private final AtomicReference<DatabaseReader> databaseReaderRef = new AtomicReference<>();

    private final FileSystem fileSystem;

    public DatabaseReaderFactory(
            GreenbidsRealTimeDataProperties properties, Vertx vertx) {
        this.properties = properties;
        this.vertx = vertx;
        this.fileSystem = vertx.fileSystem();
    }

    @Override
    public void initialize(Promise<Void> initializePromise) {
        vertx.executeBlocking(() -> downloadAndExtract().onSuccess(databaseReaderRef::set))
                .<Void>mapEmpty()
                .onComplete(initializePromise);
    }

    private Future<DatabaseReader> downloadAndExtract() {
        final String downloadUrl = properties.getGeoLiteCountryPath();
        final String tmpPath = properties.getTmpPath();
        return downloadFile(downloadUrl, tmpPath)
                .map(unused -> extractMMDB(tmpPath))
                .onComplete(ar -> removeFile(tmpPath));
    }

    private Future<Void> downloadFile(String downloadUrl, String tmpPath) {
        return fileSystem.open(tmpPath, new OpenOptions())
                .compose(tmpFile -> sendHttpRequest(downloadUrl)
                        .compose(response -> response.pipeTo(tmpFile))
                        .onFailure(error -> logger.error(
                                "Failed to download file from {} to {}.", downloadUrl, tmpPath, error)));
    }

    private Future<HttpClientResponse> sendHttpRequest(String url) {
        final RequestOptions options = new RequestOptions()
                .setFollowRedirects(true)
                .setMethod(HttpMethod.GET)
                .setTimeout(properties.getTimeoutMs())
                .setAbsoluteURI(url);

        return vertx.createHttpClient().request(options)
                .compose(HttpClientRequest::send)
                .map(this::validateResponse);
    }

    private HttpClientResponse validateResponse(HttpClientResponse response) {
        final int statusCode = response.statusCode();
        if (statusCode != HttpResponseStatus.OK.code()) {
            throw new PreBidException("Got unexpected response from server with status code %s and message %s"
                    .formatted(statusCode, response.statusMessage()));
        }
        return response;
    }

    private DatabaseReader extractMMDB(String tarGzPath) {
        try (GZIPInputStream gis = new GZIPInputStream(Files.newInputStream(Path.of(tarGzPath)));
                TarArchiveInputStream tarInput = new TarArchiveInputStream(gis)) {

            TarArchiveEntry currentEntry;
            boolean hasDatabaseFile = false;
            while ((currentEntry = tarInput.getNextTarEntry()) != null) {
                if (currentEntry.getName().contains("GeoLite2-Country.mmdb")) {
                    hasDatabaseFile = true;
                    break;
                }
            }

            if (!hasDatabaseFile) {
                throw new PreBidException("GeoLite2-Country.mmdb not found in the archive");
            }

            return new DatabaseReader.Builder(tarInput)
                    .fileMode(Reader.FileMode.MEMORY).build();
        } catch (IOException e) {
            throw new PreBidException("Failed to extract MMDB file", e);
        }
    }

    private void removeFile(String filePath) {
        fileSystem.delete(filePath)
                .onFailure(err -> logger.error("Failed to remove file {}", filePath, err));
    }

    public DatabaseReader getDatabaseReader() {
        return databaseReaderRef.get();
    }
}
