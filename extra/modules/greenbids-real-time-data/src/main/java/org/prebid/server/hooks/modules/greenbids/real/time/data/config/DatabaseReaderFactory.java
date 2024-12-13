package org.prebid.server.hooks.modules.greenbids.real.time.data.config;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class DatabaseReaderFactory implements Initializable {

    private final String gcsBucketName;

    private final String geoLiteCountryPath;

    private final Vertx vertx;

    private final Storage storage;

    private final AtomicReference<DatabaseReader> databaseReaderRef = new AtomicReference<>();

    public DatabaseReaderFactory(String gcsBucketName, String geoLiteCountryPath, Vertx vertx, Storage storage) {
        this.gcsBucketName = gcsBucketName;
        this.geoLiteCountryPath = geoLiteCountryPath;
        this.vertx = vertx;
        this.storage = storage;
    }

    @Override
    public void initialize(Promise<Void> initializePromise) {
        vertx.executeBlocking(() -> {
            try {
                final Blob blob = getBlob(geoLiteCountryPath);
                final Path databasePath = Files.createTempFile("GeoLite2-Country", ".mmdb");

                try (FileOutputStream outputStream = new FileOutputStream(databasePath.toFile())) {
                    outputStream.write(blob.getContent());
                }

                databaseReaderRef.set(new DatabaseReader.Builder(databasePath.toFile()).build());

                System.out.println(
                        "DatabaseReaderFactory/initialize: \n" +
                                "   gcsBucketName: " + gcsBucketName + "\n" +
                                "   geoLiteCountryPath: " + geoLiteCountryPath + "\n" +
                                "   blob: " + blob + "\n" +
                                "   databasePath: " + databasePath + "\n" +
                                "   gcsBucketName: " + gcsBucketName
                );

            } catch (IOException e) {
                throw new PreBidException("Failed to initialize DatabaseReader from URL", e);
            }
            return null;
        }).<Void>mapEmpty()
        .onComplete(initializePromise);
    }

    private Blob getBlob(String blobName) {
        return Optional.ofNullable(storage.get(gcsBucketName))
                .map(bucket -> bucket.get(blobName))
                .orElseThrow(() -> new PreBidException("Bucket not found: " + gcsBucketName));
    }

    public DatabaseReader getDatabaseReader() {
        return databaseReaderRef.get();
    }
}
