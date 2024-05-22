package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.mergers;

import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.WritableDeviceInfo;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class DevicePropertyMergeTest {
    private static <T> PropertyMerge<WritableDeviceInfo, DeviceInfo, T> buildPropertyMerge(
        BiConsumer<WritableDeviceInfo, T> setter,
        Function<DeviceInfo, T> getter,
        Predicate<T> isUsable
    ) {
        return new PropertyMerge<>(getter, isUsable, setter);
    }

    // --- `shouldReplacePropertyIn` method

    @Test
    public void shouldReturnTrueOnNullProperty() {
        // given
        final DeviceInfo deviceData = mock(DeviceInfo.class);
        final boolean[] getterCalled = { false };
        final Predicate<DeviceInfo> propertyMergeCondition = buildPropertyMerge(
                null,
                data -> {
                    assertThat(data).isEqualTo(deviceData);
                    getterCalled[0] = true;
                    return null;
                },
                null)::shouldReplacePropertyIn;

        // when and then
        assertThat(propertyMergeCondition.test(deviceData)).isTrue();
        assertThat(getterCalled).containsExactly(true);
    }

    @Test
    public void shouldReturnTrueOnUnusableProperty() {
        // given
        final String extractedValue = "dummy";
        final boolean[] validatorCalled = { false };
        final Predicate<DeviceInfo> propertyMergeCondition = buildPropertyMerge(
                null,
                data -> extractedValue,
                value -> {
                    assertThat(value).isEqualTo(extractedValue);
                    validatorCalled[0] = true;
                    return false;
                })::shouldReplacePropertyIn;

        // when and then
        assertThat(propertyMergeCondition.test(null)).isTrue();
        assertThat(validatorCalled).containsExactly(true);
    }

    @Test
    public void shouldReturnFalseOnUsableProperty() {
        // given
        final String extractedValue = "dummy";
        final boolean[] validatorCalled = { false };
        final Predicate<DeviceInfo> propertyMergeCondition = buildPropertyMerge(
                null,
                data -> extractedValue,
                value -> {
                    assertThat(value).isEqualTo(extractedValue);
                    validatorCalled[0] = true;
                    return true;
                })::shouldReplacePropertyIn;

        // when and then
        assertThat(propertyMergeCondition.test(null)).isFalse();
        assertThat(validatorCalled).containsExactly(true);
    }

    // --- `patch` method

    @Test
    public void shouldIgnoreNullPropertyAndReturnFalse() {
        // given
        final DeviceInfo deviceData = mock(DeviceInfo.class);
        final boolean[] getterCalled = { false };

        // when
        final BiPredicate<WritableDeviceInfo, DeviceInfo> propertyMerge = buildPropertyMerge(
                null,
                data -> {
                    assertThat(data).isEqualTo(deviceData);
                    getterCalled[0] = true;
                    return null;
                },
                null)::copySingleValue;
        final boolean patched = propertyMerge.test(null, deviceData);

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
        final BiPredicate<WritableDeviceInfo, DeviceInfo> propertyMerge = buildPropertyMerge(
                null,
                data -> extractedValue,
                value -> {
                    assertThat(value).isEqualTo(extractedValue);
                    validatorCalled[0] = true;
                    return false;
                })::copySingleValue;
        final boolean patched = propertyMerge.test(null, null);

        // then
        assertThat(patched).isFalse();
        assertThat(validatorCalled).containsExactly(true);
    }

    @Test
    public void shouldApplyUsablePropertyAndReturnTrue() {
        // given
        final String extractedValue = "dummy";
        final boolean[] validatorCalled = { false };
        final boolean[] setterCalled = { false };

        // when
        final BiPredicate<WritableDeviceInfo, DeviceInfo> propertyMerge = buildPropertyMerge(
                (builder, s) -> {
                    assertThat(s).isEqualTo(extractedValue);
                    setterCalled[0] = true;
                },
                data -> extractedValue,
                value -> {
                    assertThat(value).isEqualTo(extractedValue);
                    validatorCalled[0] = true;
                    return true;
                })::copySingleValue;
        final boolean patched = propertyMerge.test(null, null);

        // when and then
        assertThat(patched).isTrue();
        assertThat(validatorCalled).containsExactly(true);
        assertThat(setterCalled).containsExactly(true);
    }
}
