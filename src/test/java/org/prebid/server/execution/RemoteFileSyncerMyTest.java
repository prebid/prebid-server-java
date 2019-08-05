package org.prebid.server.execution;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;


public class RemoteFileSyncerMyTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private File file;
    private Path path;
    private FileSystem fileSystem;
    private FileSystemProvider provider;


    private final String exampleUrl = "https://example.com";
    private final String url30 = "https://geolite.maxmind.com/download/geoip/database/GeoLite2-City.tar.gz";
    private final String url20 = "https://www.hq.nasa.gov/alsj/a17/A17_FlightPlan.pdf";
    private final String notExistingFilePath = "./src/test/resources/org/prebid/server/geolocation/test.pdf";

    private Vertx vertx = Vertx.vertx();

    private RemoteFileSyncerMy remoteFileSyncerMy;

//    @After
//    public void tearDown(TestContext context) {
//        vertx.close(context.asyncAssertSuccess());
//    }

    @Before
    public void setUp() throws MalformedURLException {
//        final URL downloadUrl = new URL(url20);
//        final File file = new File(notExistingFilePath);
//        remoteFileSyncerMy = new RemoteFileSyncerMy(downloadUrl, file, 5, 100, 100, 2000, vertx);
    }

    @Test
    public void syncForFilepathShouldTriggerConsumerAcceptWithoutDownloadingWhenFileIsExist() throws IOException {
        // given
        givenFileExist();
        final File file = mock(File.class);
        //Not possible
        //final URL url = mock(URL.class);
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

    @Test
    public void syncForInputStreamShouldTriggerConsumerAcceptWithoutDownloadingWhenFileIsExist() throws IOException {
        givenFileExist();
        //Change input Stream
        final Vertx vertx = mock(Vertx.class);
        final URL downloadUrl = new URL("http://test");
        RemoteFileSyncerMy remoteFileSyncerMy = new RemoteFileSyncerMy(downloadUrl, file, 5, 100, 100, 2000, vertx);
        final Consumer consumer = mock(Consumer.class);

        when(file.exists()).thenReturn(true);

        remoteFileSyncerMy.syncForInputStream(consumer);
        // when and then
        verify(consumer).accept(any());
        verifyZeroInteractions(vertx);
    }

    @Test
    public void syncForInputStreamShouldTriggerConsumerAcceptAfterDownloadingWhenFileNotExist() throws IOException {
        givenFileNotExist();

        //URL mock
        final URLConnection mockUrlCon = mock(URLConnection.class);
        //FileInputStream
        FileInputStream urlFileInputStream = new FileInputStream("test/file");
        doReturn(urlFileInputStream).when(mockUrlCon).getInputStream();

        final FileChannel fileChannel = mock(FileChannel.class);
        when(urlFileInputStream.getChannel()).thenReturn(fileChannel);

        //make getLastModified() return first 10, then 11
        when(mockUrlCon.getLastModified()).thenReturn((Long)10L, (Long)11L);

        URLStreamHandler openConnection = new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) throws IOException {
                return mockUrlCon;
            }
        };
        final URL url = new URL("http://foo.bar", "foo.bar", 80, "", openConnection);


        //Change input Stream
        final Vertx vertx = mock(Vertx.class);
        final URL downloadUrl = new URL("http://test");
        final int retryInterval = 1000;
        final int timeout = 2000;
        RemoteFileSyncerMy remoteFileSyncerMy = new RemoteFileSyncerMy(downloadUrl, file, 5, retryInterval, 100, timeout, vertx);
        final Consumer consumer = mock(Consumer.class);

        remoteFileSyncerMy.syncForInputStream(consumer);


        verify(provider).deleteIfExists(eq(path));
        verify(vertx).setTimer(eq(timeout), any());
        verify(vertx).cancelTimer(eq(timeout));
        verify(vertx, never()).setTimer(eq(retryInterval), any());
        verify(vertx, never()).setTimer(retryInterval, any());
        // when and then
        verify(consumer).accept(any());
        verifyZeroInteractions(vertx);
    }

    @Test
    public void name() {

    }

    @Test
    public void givenFileNotExist() throws IOException {
        //File mock
        file = mock(File.class);
        path = mock(Path.class);
        fileSystem = mock(FileSystem.class);
        provider = mock(FileSystemProvider.class);
        when(file.toPath()).thenReturn(path);
        when(path.getFileSystem()).thenReturn(fileSystem);
        when(fileSystem.provider()).thenReturn(provider);

        doThrow(IOException.class).when(provider).checkAccess(any(), any());


        System.out.println(Files.exists(path));
    }


    @Test
    public void givenFileExist() throws IOException {
        //File mock
        file = mock(File.class);
        path = mock(Path.class);
        fileSystem = mock(FileSystem.class);
        provider = mock(FileSystemProvider.class);
        when(file.toPath()).thenReturn(path);
        when(path.getFileSystem()).thenReturn(fileSystem);
        when(fileSystem.provider()).thenReturn(provider);

//        doThrow(IOException.class).when(provider).checkAccess(any(), any());


        System.out.println(Files.exists(path));
    }
}

