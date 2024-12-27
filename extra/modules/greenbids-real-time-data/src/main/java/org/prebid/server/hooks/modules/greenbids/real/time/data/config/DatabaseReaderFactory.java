package org.prebid.server.hooks.modules.greenbids.real.time.data.config;

import io.netty.handler.codec.http.HttpResponseStatus;
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

    private final GreenbidsRealTimeDataProperties properties;

    private final Vertx vertx;

    private final AtomicReference<DatabaseReader> databaseReaderRef = new AtomicReference<>();

    private static final Logger logger = LoggerFactory.getLogger(DatabaseReaderFactory.class);

    public DatabaseReaderFactory(
            GreenbidsRealTimeDataProperties properties, Vertx vertx) {
        this.properties = properties;
        this.vertx = vertx;
    }

    @Override
    public void initialize(Promise<Void> initializePromise) {

        System.out.println(
                "DatabaseReaderFactory/initialize/ \n" +
                        "properties: " + properties + "\n" +
                        "vertx: " + vertx + "\n"
        );

        vertx.executeBlocking(() -> downloadAndExtract().onSuccess(databaseReaderRef::set))
                .<Void>mapEmpty()
                .onComplete(initializePromise);
    }

    private Future<DatabaseReader> downloadAndExtract() {
        final String downloadUrl = properties.geoLiteCountryPath;
        final String tmpPath = "/var/tmp/prebid/tmp/GeoLite2-Country.tar.gz";
        return downloadFile(downloadUrl, tmpPath)
                .map(v -> extractMMDB(tmpPath));
    }

    private Future<Void> downloadFile(String downloadUrl, String tmpPath) {
        logger.info("downloadFile(): URL={}, tmpPath={}", downloadUrl, tmpPath);

        // Open the file for writing
        return vertx.fileSystem().mkdirs("/var/tmp/prebid/tmp")
                .compose(v -> vertx.fileSystem().open(tmpPath, new OpenOptions()))
                .compose(tmpFile -> sendHttpRequest(downloadUrl)
                        .compose(response -> response.pipeTo(tmpFile))
                        .onSuccess(ignored -> logger.info("File downloaded successfully to {}", tmpPath))
                        .onFailure(error -> logger.error("Failed to download file from {} to {}.", downloadUrl, tmpPath, error)));
    }

    private Future<HttpClientResponse> sendHttpRequest(String url) {
        final RequestOptions options = new RequestOptions()
                .setFollowRedirects(true)
                .setMethod(HttpMethod.GET)
                .setAbsoluteURI(url);

        return vertx.createHttpClient().request(options)
                .compose(request -> {
                    System.out.println(
                            "DatabaseReaderFactory/sendHttpRequest/ before send \n" +
                                    "request: " + request + "\n"
                    );

                    Future<HttpClientResponse> responseFuture = request.send();

                    System.out.println(
                            "DatabaseReaderFactory/sendHttpRequest/ after sent \n" +
                                    "response: " + responseFuture + "\n"
                    );

                    return responseFuture;
                })
                .map(this::validateResponse);
    }

    private HttpClientResponse validateResponse(HttpClientResponse response) {

        System.out.println(
                "DatabaseReaderFactory/validateResponse/ \n" +
                        "response: " + response + "\n"
        );

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

            System.out.println(
                    "DatabaseReaderFactory/extractMMDB/ \n" +
                            "tarGzPath: " + tarGzPath + "\n" +
                            "gis: " + gis + "\n" +
                            "tarInput: " + tarInput + "\n"
            );

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

            System.out.println(
                    "DatabaseReaderFactory/extractMMDB/ \n" +
                            "databaseReader: " + databaseReader + "\n"
            );

            return databaseReader;
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract MMDB file", e);
        }
    }

    public DatabaseReader getDatabaseReader() {
        return databaseReaderRef.get();
    }
}
