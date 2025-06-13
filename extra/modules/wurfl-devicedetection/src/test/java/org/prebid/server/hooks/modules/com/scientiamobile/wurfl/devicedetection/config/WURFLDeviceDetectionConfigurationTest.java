package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
public class WURFLDeviceDetectionConfigurationTest {

    @Mock
    private Vertx vertx;

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
}
