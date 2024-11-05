package org.prebid.server.hooks.modules.greenbids.real.time.data.config;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import com.maxmind.geoip2.DatabaseReader;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.vertx.Initializable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public class DatabaseReaderFactory implements Initializable {

    private final String geoLiteCountryUrl;

    private final Vertx vertx;

    private final AtomicReference<DatabaseReader> databaseReaderRef = new AtomicReference<>();

    public DatabaseReaderFactory(String geoLitCountryUrl, Vertx vertx) {
        this.geoLiteCountryUrl = geoLitCountryUrl;
        this.vertx = vertx;
    }

    @Override
    public void initialize(Promise<Void> initializePromise) {

        vertx.executeBlocking(() -> {
            try {
                final URL url = new URL(geoLiteCountryUrl);
                final Path databasePath = Files.createTempFile("GeoLite2-Country", ".mmdb");

                try (InputStream inputStream = url.openStream();
                     final FileOutputStream outputStream = new FileOutputStream(databasePath.toFile())) {
                    inputStream.transferTo(outputStream);
                }

                databaseReaderRef.set(new DatabaseReader.Builder(databasePath.toFile()).build());
            } catch (IOException e) {
                throw new PreBidException("Failed to initialize DatabaseReader from URL", e);
            }
            return null;
        }).<Void>mapEmpty()
        .onComplete(initializePromise);
    }

    public DatabaseReader getDatabaseReader() {
        return databaseReaderRef.get();
    }
}
