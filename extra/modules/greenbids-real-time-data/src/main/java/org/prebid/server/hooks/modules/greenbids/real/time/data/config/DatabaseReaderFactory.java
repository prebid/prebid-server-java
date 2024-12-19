package org.prebid.server.hooks.modules.greenbids.real.time.data.config;

import com.maxmind.db.Reader;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import com.maxmind.geoip2.DatabaseReader;
import io.vertx.core.buffer.Buffer;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.vertx.Initializable;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

public class DatabaseReaderFactory implements Initializable {

    private final GreenbidsRealTimeDataProperties properties;

    private final Vertx vertx;

    private final HttpClient httpClient;

    private final AtomicReference<DatabaseReader> databaseReaderRef = new AtomicReference<>();

    public DatabaseReaderFactory(GreenbidsRealTimeDataProperties properties, Vertx vertx, HttpClient httpClient) {
        this.properties = properties;
        this.vertx = vertx;
        this.httpClient = httpClient;
    }

    @Override
    public void initialize(Promise<Void> initializePromise) {
        vertx.executeBlocking(promise -> downloadFile(properties)
                .onSuccess(tarGzPath -> {
                    try {
                        databaseReaderRef.set(extractMMDB(tarGzPath));
                        promise.complete();
                    } catch (IOException e) {
                        promise.fail(new PreBidException("Failed to extract MMDB file", e));
                    }
                }).onFailure(promise::fail));
    }

    private Future<Path> downloadFile(GreenbidsRealTimeDataProperties properties) {
        final String downloadUrl = properties.geoLiteCountryPath + "&account_id=" + properties.maxMindAccountId
                + "&license_key=" + properties.maxMindLicenseKey;

        final Future<HttpClientResponse> responseFuture = httpClient.get(downloadUrl,
                        MultiMap.caseInsensitiveMultiMap(),
                        properties.getTimeoutMs());

        return responseFuture
                .compose(response -> {
                    if (response.getStatusCode() != 200) {
                        return Future.failedFuture(
                                new PreBidException("Failed to download DB from URL: " + downloadUrl
                                        + " Status: " + response.getStatusCode()));
                    }

                    return vertx.fileSystem()
                            .createTempFile("geolite2", ".tar.gz");
                })
                .compose(tempFilePath -> vertx.fileSystem()
                        .writeFile(tempFilePath, Buffer.buffer(
                                responseFuture.result().getBody().getBytes(StandardCharsets.ISO_8859_1)))
                        .map(v -> Path.of(tempFilePath)));
    }

    private DatabaseReader extractMMDB(Path tarGzPath) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(Files.newInputStream(tarGzPath));
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
                throw new RuntimeException("GeoLite2-Country.mmdb not found in the archive");
            }

            final DatabaseReader databaseReader = new DatabaseReader.Builder(tarInput)
                    .fileMode(Reader.FileMode.MEMORY).build();
            return databaseReader;
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract MMDB file", e);
        }
    }

    public DatabaseReader getDatabaseReader() {
        return databaseReaderRef.get();
    }
}
