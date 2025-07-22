package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import com.scientiamobile.wurfl.core.Device;
import com.scientiamobile.wurfl.core.WURFLEngine;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config.WURFLDeviceDetectionConfigProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
public class WURFLServiceTest {

    @Mock(strictness = LENIENT)
    private WURFLEngine wurflEngine;

    @Mock(strictness = LENIENT)
    private WURFLDeviceDetectionConfigProperties configProperties;

    private WURFLService wurflService;

    @BeforeEach
    public void setUp() {
        wurflService = new WURFLService(wurflEngine, configProperties);
    }

    @Test
    public void setDataPathShouldReturnSucceededFutureWhenProcessingSucceeds() throws Exception {
        // given
        final String dataFilePath = "test-data-path";
        final String wurflSnapshotUrl = "http://example.com/wurfl-snapshot.zip";
        final String wurflFileDirPath = System.getProperty("java.io.tmpdir");

        given(configProperties.getFileSnapshotUrl()).willReturn(wurflSnapshotUrl);
        given(configProperties.getFileDirPath()).willReturn(wurflFileDirPath);

        final WURFLService spyWurflService = spy(wurflService);
        doReturn(wurflEngine).when(spyWurflService).createEngine(dataFilePath);

        // when
        final Future<?> future = spyWurflService.setDataPath(dataFilePath);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();
    }

    @Test
    public void setDataPathShouldReturnFailedFutureWhenExceptionOccurs() {
        // given
        final String dataFilePath = "test-data-path";
        final WURFLService spyWurflService = spy(wurflService);
        doThrow(new RuntimeException("Simulated load() failure"))
                .when(spyWurflService).createEngine(dataFilePath);

        // when
        final Future<?> result = spyWurflService.setDataPath(dataFilePath);

        // then
        assertThat(result).isNotNull();
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(RuntimeException.class)
                .hasMessage("Simulated load() failure");
    }

    @Test
    public void lookupDeviceShouldReturnDeviceWhenEngineIsNotNull() {
        // given
        final Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "test-user-agent");

        final Device expectedDevice = mock(Device.class);
        when(wurflEngine.getDeviceForRequest(headers)).thenReturn(expectedDevice);

        // when
        final Optional<Device> result = wurflService.lookupDevice(headers);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(expectedDevice);
        verify(wurflEngine).getDeviceForRequest(headers);
    }

    @Test
    public void lookupDeviceShouldReturnEmptyWhenEngineIsNull() {
        // given
        wurflService = new WURFLService(null, configProperties);
        final Map<String, String> headers = new HashMap<>();

        // when
        final Optional<Device> result = wurflService.lookupDevice(headers);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void getAllCapabilitiesShouldReturnCapabilitiesWhenEngineIsNotNull() {
        // given
        final Set<String> expectedCapabilities = Set.of("capability1", "capability2");
        when(wurflEngine.getAllCapabilities()).thenReturn(expectedCapabilities);

        // when
        final Set<String> result = wurflService.getAllCapabilities();

        // then
        assertThat(result).isEqualTo(expectedCapabilities);
        verify(wurflEngine).getAllCapabilities();
    }

    @Test
    public void getAllCapabilitiesShouldReturnEmptySetWhenEngineIsNull() {
        // given
        wurflService = new WURFLService(null, configProperties);

        // when
        final Set<String> result = wurflService.getAllCapabilities();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void getAllVirtualCapabilitiesShouldReturnCapabilitiesWhenEngineIsNotNull() {
        // given
        final Set<String> expectedCapabilities = Set.of("virtualCapability1", "virtualCapability2");
        when(wurflEngine.getAllVirtualCapabilities()).thenReturn(expectedCapabilities);

        // when
        final Set<String> result = wurflService.getAllVirtualCapabilities();

        // then
        assertThat(result).isEqualTo(expectedCapabilities);
        verify(wurflEngine).getAllVirtualCapabilities();
    }

    @Test
    public void getAllVirtualCapabilitiesShouldReturnEmptySetWhenEngineIsNull() {
        // given
        wurflService = new WURFLService(null, configProperties);

        // when
        final Set<String> result = wurflService.getAllVirtualCapabilities();

        // then
        assertThat(result).isEmpty();
    }
}
