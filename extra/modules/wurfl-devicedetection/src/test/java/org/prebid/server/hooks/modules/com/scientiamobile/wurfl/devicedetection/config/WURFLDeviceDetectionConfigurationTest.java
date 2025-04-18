package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.execution.file.syncer.FileSyncer;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1.WURFLDeviceDetectionModule;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1.WURFLService;
import org.prebid.server.spring.config.model.FileSyncerProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class WURFLDeviceDetectionConfigurationTest {

    @Mock
    private Vertx vertx;

    @Mock
    private FileSyncer fileSyncer;

    private WURFLDeviceDetectionConfiguration configuration;

    @BeforeEach
    public void setUp() {
        configuration = spy(new WURFLDeviceDetectionConfiguration());
    }

    @Test
    public void configPropertiesShouldReturnWURFLDeviceDetectionConfigProperties() {
        // when
        final WURFLDeviceDetectionConfigProperties result = configuration.configProperties();

        // then
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(WURFLDeviceDetectionConfigProperties.class);
    }

    @Test
    public void wurflDeviceDetectionModuleShouldCreateModuleAndSyncFile() {
        // given
        final WURFLDeviceDetectionConfigProperties configProperties = new WURFLDeviceDetectionConfigProperties();
        configProperties.setFileDirPath("/tmp/wurfl");
        configProperties.setFileSnapshotUrl("https://example.com/wurfl.zip");

        doReturn(fileSyncer).when(configuration).createFileSyncer(any(), any(), any());

        // when
        final WURFLDeviceDetectionModule result = configuration.wurflDeviceDetectionModule(configProperties, vertx);

        // then
        assertThat(result).isNotNull();
        verify(fileSyncer, times(1)).sync();

        // Verify the module contains the expected hooks
        assertThat(result.hooks()).hasSize(2);
    }
}
