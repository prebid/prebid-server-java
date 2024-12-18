package org.prebid.server.hooks.modules.greenbids.real.time.data.config;

import com.google.cloud.storage.Storage;
import com.maxmind.db.Reader;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import com.maxmind.geoip2.DatabaseReader;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.vertx.Initializable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

public class DatabaseReaderFactory implements Initializable {

    private final GreenbidsRealTimeDataProperties properties;

    private final Vertx vertx;

    private final AtomicReference<DatabaseReader> databaseReaderRef = new AtomicReference<>();

    public DatabaseReaderFactory(GreenbidsRealTimeDataProperties properties, Vertx vertx, Storage storage) {
        this.properties = properties;
        this.vertx = vertx;
    }

    @Override
    public void initialize(Promise<Void> initializePromise) {
        vertx.executeBlocking(() -> {
            try {
                final Path tarGzPath = downloadFile(
                        properties.geoLiteCountryPath,
                        properties.maxMindAccountId,
                        properties.maxMindLicenseKey);
                databaseReaderRef.set(extractMMDB(tarGzPath));
            } catch (IOException e) {
                throw new PreBidException("Failed to initialize DatabaseReader from URL", e);
            }
            return null;
        }).<Void>mapEmpty()
        .onComplete(initializePromise);
    }

    private Path downloadFile(String url, String accountId, String licenseKey) throws IOException {
        final URL downloadUrl = new URL(url + "&account_id=" + accountId + "&license_key=" + licenseKey);
        final HttpURLConnection connection = (HttpURLConnection) downloadUrl.openConnection();
        connection.setRequestMethod("GET");

        final String auth = accountId + ":" + licenseKey;
        final String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        connection.setRequestProperty("Authorization", "Basic " + encodedAuth);

        final Path tempFile = Files.createTempFile("geolite2", ".tar.gz");
        try (InputStream inputStream = connection.getInputStream()) {
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    private DatabaseReader extractMMDB(Path tarGzPath) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(Files.newInputStream(tarGzPath));
                TarArchiveInputStream tarInput = new TarArchiveInputStream(gis)) {

            TarArchiveEntry currentEntry;
            while ((currentEntry = tarInput.getNextTarEntry()) != null) {
                if (currentEntry.getName().contains("GeoLite2-Country.mmdb")) {
                    break;
                }
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
