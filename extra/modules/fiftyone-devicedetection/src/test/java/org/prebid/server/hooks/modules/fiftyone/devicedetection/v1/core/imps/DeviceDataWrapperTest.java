package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import fiftyone.devicedetection.shared.DeviceData;
import fiftyone.pipeline.engines.data.AspectPropertyValueDefault;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceInfo;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DeviceDataWrapperTest {
    private static final Integer TEST_VALUE_INT = 29;
    private static final String TEST_VALUE_STRING = "dummy";

    @Test
    public void shouldReturnAllNullWhenDataIsNull() {
        // given
        final DeviceInfo dataWrapper = new DeviceDataWrapper(null, null);

        // when and then
        assertThat(dataWrapper.getDeviceType()).isNull();
        assertThat(dataWrapper.getMake()).isNull();
        assertThat(dataWrapper.getModel()).isNull();
        assertThat(dataWrapper.getOs()).isNull();
        assertThat(dataWrapper.getOsv()).isNull();
        assertThat(dataWrapper.getH()).isNull();
        assertThat(dataWrapper.getW()).isNull();
        assertThat(dataWrapper.getPpi()).isNull();
        assertThat(dataWrapper.getPixelRatio()).isNull();
        assertThat(dataWrapper.getDeviceId()).isNull();
    }

    @Test
    public void shouldReturnAllNullWhenDataReturnsNoProperty() {
        // given
        final DeviceInfo dataWrapper = new DeviceDataWrapper(mock(DeviceData.class), null);

        // when and then
        assertThat(dataWrapper.getDeviceType()).isNull();
        assertThat(dataWrapper.getMake()).isNull();
        assertThat(dataWrapper.getModel()).isNull();
        assertThat(dataWrapper.getOs()).isNull();
        assertThat(dataWrapper.getOsv()).isNull();
        assertThat(dataWrapper.getH()).isNull();
        assertThat(dataWrapper.getW()).isNull();
        assertThat(dataWrapper.getPpi()).isNull();
        assertThat(dataWrapper.getPixelRatio()).isNull();
        assertThat(dataWrapper.getDeviceId()).isNull();
    }

    @Test
    public void shouldReturnNullDeviceTypeWhenDataThrows() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getDeviceType()).thenThrow(new RuntimeException());
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getDeviceType()).isNull();
    }

    @Test
    public void shouldReturnNullDeviceTypeWhenPropertyReturnsNull() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getDeviceType()).thenReturn(new AspectPropertyValueDefault<>(null));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getDeviceType()).isNull();
    }

    @Test
    public void shouldReturnDeviceTypeWhenPropertyReturnsValue() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getDeviceType()).thenReturn(new AspectPropertyValueDefault<>(TEST_VALUE_STRING));

        // when
        final boolean[] converterCalled = { false };
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, s -> {
            assertThat(s).isEqualTo(TEST_VALUE_STRING);
            converterCalled[0] = true;
            return TEST_VALUE_INT;
        });

        // then
        assertThat(dataWrapper.getDeviceType()).isEqualTo(TEST_VALUE_INT);
        assertThat(converterCalled).containsExactly(true);
    }
    

    @Test
    public void shouldReturnNullMakeWhenDataThrows() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getHardwareVendor()).thenThrow(new RuntimeException());
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getMake()).isNull();
    }

    @Test
    public void shouldReturnNullMakeWhenPropertyReturnsNull() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getHardwareVendor()).thenReturn(new AspectPropertyValueDefault<>(null));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getMake()).isNull();
    }

    @Test
    public void shouldReturnMakeWhenPropertyReturnsValue() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getHardwareVendor()).thenReturn(new AspectPropertyValueDefault<>(TEST_VALUE_STRING));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getMake()).isEqualTo(TEST_VALUE_STRING);
    }
    

    @Test
    public void shouldReturnNullModelWhenDataThrows() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getHardwareModel()).thenThrow(new RuntimeException());
        when(deviceData.getHardwareName()).thenThrow(new RuntimeException());
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getModel()).isNull();
    }

    @Test
    public void shouldReturnModelWhenHWModelReturnsValue() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getHardwareModel()).thenReturn(new AspectPropertyValueDefault<>(TEST_VALUE_STRING));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getModel()).isEqualTo(TEST_VALUE_STRING);
    }

    @Test
    public void shouldReturnNullModelWhenHWModelAndNameReturnNull() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getHardwareModel()).thenReturn(new AspectPropertyValueDefault<>(null));
        when(deviceData.getHardwareName()).thenReturn(new AspectPropertyValueDefault<>(null));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getModel()).isNull();
    }

    @Test
    public void shouldReturnNullIfDataReturnsEmptyValues() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getHardwareModel()).thenReturn(new AspectPropertyValueDefault<>(""));
        when(deviceData.getHardwareName()).thenReturn(new AspectPropertyValueDefault<>(Collections.emptyList()));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getModel()).isNull();
    }

    @Test
    public void shouldReturnModelFromNameIfHWModelIsUnknown() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getHardwareModel()).thenReturn(new AspectPropertyValueDefault<>("Unknown"));
        when(deviceData.getHardwareName()).thenReturn(new AspectPropertyValueDefault<>(List.of("zoom", "broom")));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getModel()).isEqualTo("zoom,broom");
    }
    

    @Test
    public void shouldReturnNullOsWhenDataThrows() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getPlatformName()).thenThrow(new RuntimeException());
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getOs()).isNull();
    }

    @Test
    public void shouldReturnNullOsWhenPropertyReturnsNull() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getPlatformName()).thenReturn(new AspectPropertyValueDefault<>(null));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getOs()).isNull();
    }

    @Test
    public void shouldReturnOsWhenPropertyReturnsValue() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getPlatformName()).thenReturn(new AspectPropertyValueDefault<>(TEST_VALUE_STRING));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getOs()).isEqualTo(TEST_VALUE_STRING);
    }
    

    @Test
    public void shouldReturnNullOsvWhenDataThrows() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getPlatformVersion()).thenThrow(new RuntimeException());
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getOsv()).isNull();
    }

    @Test
    public void shouldReturnNullOsvWhenPropertyReturnsNull() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getPlatformVersion()).thenReturn(new AspectPropertyValueDefault<>(null));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getOsv()).isNull();
    }

    @Test
    public void shouldReturnOsvWhenPropertyReturnsValue() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getPlatformVersion()).thenReturn(new AspectPropertyValueDefault<>(TEST_VALUE_STRING));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getOsv()).isEqualTo(TEST_VALUE_STRING);
    }
    

    @Test
    public void shouldReturnNullHWhenDataThrows() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getScreenPixelsHeight()).thenThrow(new RuntimeException());
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getH()).isNull();
    }

    @Test
    public void shouldReturnNullHWhenPropertyReturnsNull() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getScreenPixelsHeight()).thenReturn(new AspectPropertyValueDefault<>(null));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getH()).isNull();
    }

    @Test
    public void shouldReturnHWhenPropertyReturnsValue() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getScreenPixelsHeight()).thenReturn(new AspectPropertyValueDefault<>(TEST_VALUE_INT));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getH()).isEqualTo(TEST_VALUE_INT);
    }
    

    @Test
    public void shouldReturnNullWWhenDataThrows() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getScreenPixelsWidth()).thenThrow(new RuntimeException());
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getW()).isNull();
    }

    @Test
    public void shouldReturnNullWWhenPropertyReturnsNull() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getScreenPixelsWidth()).thenReturn(new AspectPropertyValueDefault<>(null));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getW()).isNull();
    }

    @Test
    public void shouldReturnWWhenPropertyReturnsValue() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getScreenPixelsWidth()).thenReturn(new AspectPropertyValueDefault<>(TEST_VALUE_INT));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getW()).isEqualTo(TEST_VALUE_INT);
    }
    

    @Test
    public void shouldReturnNullPpiWhenScreenPixelsHeightThrows() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getScreenPixelsHeight()).thenThrow(new RuntimeException());
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getPpi()).isNull();
    }

    @Test
    public void shouldReturnNullPpiWhenScreenPixelsHeightReturnsNull() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getScreenPixelsHeight()).thenReturn(new AspectPropertyValueDefault<>(null));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getPpi()).isNull();
    }


    @Test
    public void shouldReturnNullPpiWhenScreenInchesHeightThrows() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getScreenPixelsHeight()).thenReturn(new AspectPropertyValueDefault<>(TEST_VALUE_INT));
        when(deviceData.getScreenInchesHeight()).thenThrow(new RuntimeException());
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getPpi()).isNull();
    }

    @Test
    public void shouldReturnNullPpiWhenScreenInchesHeightReturnsNull() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getScreenPixelsHeight()).thenReturn(new AspectPropertyValueDefault<>(TEST_VALUE_INT));
        when(deviceData.getScreenInchesHeight()).thenReturn(new AspectPropertyValueDefault<>(null));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getPpi()).isNull();
    }

    @Test
    public void shouldReturnNullPpiWhenScreenInchesHeightReturnsZero() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getScreenPixelsHeight()).thenReturn(new AspectPropertyValueDefault<>(TEST_VALUE_INT));
        when(deviceData.getScreenInchesHeight()).thenReturn(new AspectPropertyValueDefault<>(0.0));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getPpi()).isNull();
    }

    @Test
    public void shouldReturnPpiWhenPropertiesReturnsValue() {
        // given
        final int testFactor = 13;
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getScreenPixelsHeight())
                .thenReturn(new AspectPropertyValueDefault<>(TEST_VALUE_INT * testFactor));
        when(deviceData.getScreenInchesHeight())
                .thenReturn(new AspectPropertyValueDefault<>((double)TEST_VALUE_INT));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getPpi()).isEqualTo(testFactor);
    }
    

    @Test
    public void shouldReturnNullPXRatioWhenDataThrows() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getPixelRatio()).thenThrow(new RuntimeException());
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getPixelRatio()).isNull();
    }

    @Test
    public void shouldReturnNullPXRatioWhenPropertyReturnsNull() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getPixelRatio()).thenReturn(new AspectPropertyValueDefault<>(null));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getPixelRatio()).isNull();
    }

    @Test
    public void shouldReturnPXRatioWhenPropertyReturnsValue() {
        // given
        final double testValue = 4.2;
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getPixelRatio()).thenReturn(new AspectPropertyValueDefault<>(testValue));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getPixelRatio()).isEqualTo(BigDecimal.valueOf(testValue));
    }
    

    @Test
    public void shouldReturnNullDeviceIdWhenDataThrows() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getDeviceId()).thenThrow(new RuntimeException());
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getDeviceId()).isNull();
    }

    @Test
    public void shouldReturnNullDeviceIdWhenPropertyReturnsNull() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getDeviceId()).thenReturn(new AspectPropertyValueDefault<>(null));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getDeviceId()).isNull();
    }

    @Test
    public void shouldReturnDeviceIdWhenPropertyReturnsValue() {
        // given
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getDeviceId()).thenReturn(new AspectPropertyValueDefault<>(TEST_VALUE_STRING));
        final DeviceInfo dataWrapper = new DeviceDataWrapper(deviceData, null);

        // when and then
        assertThat(dataWrapper.getDeviceId()).isEqualTo(TEST_VALUE_STRING);
    }
}
