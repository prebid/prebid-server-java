package org.prebid.server.execution;

import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

public class RemoteFileSyncerMyTest extends VertxTest {

    private final String exampleUrl = "https://geolite.maxmind.com/download/geoip/database/GeoLite2-City.tar.gz";
    private final String existingFilePath = "./src/test/resources/org/prebid/server/geolocation/GeoLite2";
    private RemoteFileSyncerMy remoteFileSyncerMy;

    @Before
    public void setUp() throws MalformedURLException {
        final URL downloadUrl = new URL(exampleUrl);
        final File file = new File(existingFilePath);
        final Vertx vertx = Vertx.vertx();
        remoteFileSyncerMy = new RemoteFileSyncerMy(downloadUrl, file, 5, 100, 100, 100, vertx);
    }

    @Test
    public void syncForFilepathShouldUseCircuitBreakerAndAcceptConsumerWhenFileIsNotExist() {
        // given
        // when and then
        remoteFileSyncerMy.syncForFilepath(System.out::println);
    }
}

