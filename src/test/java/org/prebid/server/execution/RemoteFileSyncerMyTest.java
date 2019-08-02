package org.prebid.server.execution;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(VertxUnitRunner.class)
public class RemoteFileSyncerMyTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private final String exampleUrl = "https://example.com";
    private final String url30 = "https://geolite.maxmind.com/download/geoip/database/GeoLite2-City.tar.gz";
    private final String url20 = "https://www.hq.nasa.gov/alsj/a17/A17_FlightPlan.pdf";
    private final String notExistingFilePath = "./src/test/resources/org/prebid/server/geolocation/test.pdf";


    private Vertx vertx = Vertx.vertx();

    private RemoteFileSyncerMy remoteFileSyncerMy;


    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Before
    public void setUp() throws MalformedURLException {
        final URL downloadUrl = new URL(url20);
        final File file = new File(notExistingFilePath);
        remoteFileSyncerMy = new RemoteFileSyncerMy(downloadUrl, file, 5, 100, 100, 2000, vertx);
    }

    @Test
    public void syncForFilepathShouldConsumerAcceptWhenFileIsExist() throws MalformedURLException {
        // given
        final File file = mock(File.class);
        //Not possible
//        final URL url = mock(URL.class);
        final Vertx vertx = mock(Vertx.class);
        final URL downloadUrl = new URL("http://test");
        RemoteFileSyncerMy remoteFileSyncerMy = new RemoteFileSyncerMy(downloadUrl, file, 5, 100, 100, 2000, vertx);
        final Consumer consumer = mock(Consumer.class);

        when(file.exists()).thenReturn(true);

        remoteFileSyncerMy.syncForFilepath(consumer);
        // when and then
        verify(consumer).accept(any());
        verifyZeroInteractions(vertx);
    }

//
//    @Test
//    public void name(TestContext context) throws IOException {
//        final Async async = context.async();
//
//        final Future<Void> future = remoteFileSyncerMy.syncForFilepath(System.out::println);;
//        future.setHandler(ar -> async.complete());
//        async.await();
//
//        final Path path = Paths.get(notExistingFilePath);
//        System.out.println(Files.size(path));
//        Files.delete(path);

    // given

//        doAnswer(invocation -> {
//            final Future future = invocation.getArgument(3);
//            future.complete();
//            return future;
//        }).when(vertx).executeBlocking(any(), any(), any());
//    }
}

