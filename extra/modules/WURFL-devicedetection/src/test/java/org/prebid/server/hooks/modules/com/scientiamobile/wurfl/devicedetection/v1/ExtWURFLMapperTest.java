package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.Device;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ExtWURFLMapperTest {

    @Mock
    private com.scientiamobile.wurfl.core.Device wurflDevice;

    @Mock
    private Device device;

    private ExtWURFLMapper target;
    private List<String> staticCaps;
    private List<String> virtualCaps;

    @BeforeEach
    public void setUp() {
        staticCaps = Arrays.asList("brand_name", "model_name");
        virtualCaps = Arrays.asList("is_mobile", "form_factor");

        target = ExtWURFLMapper.builder()
                .staticCaps(staticCaps)
                .virtualCaps(virtualCaps)
                .wurflDevice(wurflDevice)
                .addExtCaps(true)
                .build();
    }

    @Test
    public void shouldMapStaticCapabilities() {
        // given
        when(wurflDevice.getCapability("brand_name")).thenReturn("Apple");
        when(wurflDevice.getCapability("model_name")).thenReturn("iPhone");

        // when
        final JsonNode result = target.mapExtProperties();

        // then
        assertThat(result.get("brand_name").asText()).isEqualTo("Apple");
        assertThat(result.get("model_name").asText()).isEqualTo("iPhone");
    }

    @Test
    public void shouldMapVirtualCapabilities() {
        // given
        when(wurflDevice.getVirtualCapability("is_mobile")).thenReturn("true");
        when(wurflDevice.getVirtualCapability("form_factor")).thenReturn("smartphone");

        // when
        final JsonNode result = target.mapExtProperties();

        // then
        assertThat(result.get("is_mobile").asText()).isEqualTo("true");
        assertThat(result.get("form_factor").asText()).isEqualTo("smartphone");
    }

    @Test
    public void shouldMapWURFLId() {
        // given
        when(wurflDevice.getId()).thenReturn("test_wurfl_id");

        // when
        final JsonNode result = target.mapExtProperties();

        // then
        assertThat(result.get("wurfl_id").asText()).isEqualTo("test_wurfl_id");
    }

    @Test
    public void shouldSkipNullCapabilities() {
        // given
        when(wurflDevice.getCapability("brand_name")).thenReturn(null);
        when(wurflDevice.getCapability("model_name")).thenReturn("iPhone");
        when(wurflDevice.getVirtualCapability("is_mobile")).thenReturn(null);

        // when
        final JsonNode result = target.mapExtProperties();

        // then
        assertThat(result.has("brand_name")).isFalse();
        assertThat(result.get("model_name").asText()).isEqualTo("iPhone");
        assertThat(result.has("is_mobile")).isFalse();
    }

    @Test
    public void shouldHandleExceptionsGracefully() {
        // given
        when(wurflDevice.getCapability("brand_name")).thenThrow(new RuntimeException("Test exception"));
        when(wurflDevice.getCapability("model_name")).thenReturn("iPhone");

        // when
        final JsonNode result = target.mapExtProperties();

        // then
        assertThat(result.has("brand_name")).isFalse();
        assertThat(result.get("model_name")).isNotNull();
        assertThat(result.get("model_name").asText()).isEqualTo("iPhone");
    }

    @Test
    public void shouldNotAddExtCapsIfDisabled() {
        // given
        target = ExtWURFLMapper.builder()
                .staticCaps(staticCaps)
                .virtualCaps(virtualCaps)
                .wurflDevice(wurflDevice)
                .addExtCaps(false)
                .build();

        // when
        final JsonNode result = target.mapExtProperties();

        // then
        assertThat(result.has("brand_name")).isFalse();
        assertThat(result.has("model_name")).isFalse();
    }
}
