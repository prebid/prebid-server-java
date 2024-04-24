package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import com.iab.openrtb.request.Device;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class DevicePropertyMergeTest {

    // --- `shouldReplacePropertyIn` method

    @Test
    public void shouldReturnTrueOnNullProperty() {
        // given
        final DeviceInfo deviceData = mock(DeviceInfo.class);
        final boolean[] getterCalled = { false };
        final DevicePropertyMergeCondition propertyMergeCondition = new DevicePropertyMerge<>(
                null,
                data -> {
                    assertThat(data).isEqualTo(deviceData);
                    getterCalled[0] = true;
                    return null;
                },
                null);

        // when and then
        assertThat(propertyMergeCondition.shouldReplacePropertyIn(deviceData)).isTrue();
        assertThat(getterCalled).containsExactly(true);
    }

    @Test
    public void shouldReturnTrueOnUnusableProperty() {
        // given
        final String extractedValue = "dummy";
        final boolean[] validatorCalled = { false };
        final DevicePropertyMergeCondition propertyMergeCondition = new DevicePropertyMerge<>(
                null,
                data -> extractedValue,
                value -> {
                    assertThat(value).isEqualTo(extractedValue);
                    validatorCalled[0] = true;
                    return false;
                });

        // when and then
        assertThat(propertyMergeCondition.shouldReplacePropertyIn(null)).isTrue();
        assertThat(validatorCalled).containsExactly(true);
    }

    @Test
    public void shouldReturnFalseOnUsableProperty() {
        // given
        final String extractedValue = "dummy";
        final boolean[] validatorCalled = { false };
        final DevicePropertyMergeCondition propertyMergeCondition = new DevicePropertyMerge<>(
                null,
                data -> extractedValue,
                value -> {
                    assertThat(value).isEqualTo(extractedValue);
                    validatorCalled[0] = true;
                    return true;
                });

        // when and then
        assertThat(propertyMergeCondition.shouldReplacePropertyIn(null)).isFalse();
        assertThat(validatorCalled).containsExactly(true);
    }

    // --- `patch` method

    @Test
    public void shouldIgnoreNullPropertyAndReturnFalse() {
        // given
        final DeviceInfo deviceData = mock(DeviceInfo.class);
        final boolean[] getterCalled = { false };

        // when
        final DevicePatch propertyMerge = new DevicePropertyMerge<>(
                null,
                data -> {
                    assertThat(data).isEqualTo(deviceData);
                    getterCalled[0] = true;
                    return null;
                },
                null);
        final boolean patched = propertyMerge.patch(null, null, deviceData);

        // then
        assertThat(patched).isFalse();
        assertThat(getterCalled).containsExactly(true);
    }

    @Test
    public void shouldIgnoreUnusablePropertyAndReturnFalse() {
        // given
        final String extractedValue = "dummy";
        final boolean[] validatorCalled = { false };

        // when
        final DevicePatch propertyMerge = new DevicePropertyMerge<>(
                null,
                data -> extractedValue,
                value -> {
                    assertThat(value).isEqualTo(extractedValue);
                    validatorCalled[0] = true;
                    return false;
                });
        final boolean patched = propertyMerge.patch(null, null, null);

        // then
        assertThat(patched).isFalse();
        assertThat(validatorCalled).containsExactly(true);
    }

    @Test
    public void shouldApplyUsablePropertyAndReturnTrue() {
        // given
        final Device oldDevice = Device.builder().build();
        final String extractedValue = "dummy";
        final boolean[] validatorCalled = { false };
        final boolean[] setterSupplierCalled = { false };
        final boolean[] setterCalled = { false };

        // when
        final DevicePatch propertyMerge = new DevicePropertyMerge<>(
                device -> {
                    assertThat(device).isEqualTo(oldDevice);
                    setterSupplierCalled[0] = true;
                    return (builder, s) -> {
                        assertThat(s).isEqualTo(extractedValue);
                        setterCalled[0] = true;
                    };
                },
                data -> extractedValue,
                value -> {
                    assertThat(value).isEqualTo(extractedValue);
                    validatorCalled[0] = true;
                    return true;
                });
        final boolean patched = propertyMerge.patch(null, oldDevice, null);

        // when and then
        assertThat(patched).isTrue();
        assertThat(validatorCalled).containsExactly(true);
        assertThat(setterSupplierCalled).containsExactly(true);
        assertThat(setterCalled).containsExactly(true);
    }
}
