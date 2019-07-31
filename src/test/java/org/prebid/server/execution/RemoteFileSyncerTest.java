package org.prebid.server.execution;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class RemoteFileSyncerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();
    private final String exampleUrl = "http://example.com";
    private final String existingFilePath = "./src/test/resources/org/prebid/server/geolocation/GeoLite2-test.tar.gz";
    @Mock
    private CircuitBreaker circuitBreaker;
    private RemoteFileSyncer remoteFileSyncer;

    @Before
    public void setUp() throws MalformedURLException {
        final URL downloadUrl = new URL(exampleUrl);
        final File file = new File(existingFilePath);
        remoteFileSyncer = new RemoteFileSyncer(downloadUrl, file, 2, 100);
    }

    @Test
    public void syncForFilepathShouldUseCircuitBreakerAndAcceptConsumerWhenFileIsNotExist() {
        // given
        when(circuitBreaker.execute(any())).thenReturn(Future.succeededFuture());
        final Consumer mock = mock(Consumer.class);

        // when and then
        remoteFileSyncer.syncForFilepath(mock);

        verify(circuitBreaker).execute(any());
        verify(mock).accept(anyString());
    }

    @Test
    public void syncForFilepathShouldAcceptConsumerWhenFileIsExist() {
        // given
        final Consumer mock = mock(Consumer.class);

        // when and then
        remoteFileSyncer.syncForFilepath(mock);

        verifyZeroInteractions(circuitBreaker);
        verify(mock).accept(anyString());
    }
}

