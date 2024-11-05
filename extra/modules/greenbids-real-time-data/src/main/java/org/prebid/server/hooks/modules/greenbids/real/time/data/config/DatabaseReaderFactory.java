package org.prebid.server.hooks.modules.greenbids.real.time.data.config;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import com.maxmind.geoip2.DatabaseReader;
import lombok.Getter;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.vertx.Initializable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class DatabaseReaderFactory implements Initializable {

    private final String geoLiteCountryUrl;

    private final Vertx vertx;

    @Getter
    private DatabaseReader databaseReader;

    public DatabaseReaderFactory(String geoLitCountryUrl, Vertx vertx) {
        this.geoLiteCountryUrl = geoLitCountryUrl;
        this.vertx = vertx;
    }

    @Override
    public void initialize(Promise<Void> initializePromise) {
        vertx.executeBlocking(promise -> {
            try {
                URL url = new URL(geoLiteCountryUrl);
                Path databasePath = Files.createTempFile("GeoLite2-Country", ".mmdb");

                try (InputStream inputStream = url.openStream();
                     FileOutputStream outputStream = new FileOutputStream(databasePath.toFile())) {
                    inputStream.transferTo(outputStream);
                }

                databaseReader = new DatabaseReader.Builder(databasePath.toFile()).build();
                promise.complete();
            } catch (IOException e) {
                promise.fail(new PreBidException("Failed to initialize DatabaseReader from URL", e));
            }
        }, res -> {
            if (res.succeeded()) {
                initializePromise.complete();
            } else {
                initializePromise.fail(res.cause());
            }
        });
    }
}
