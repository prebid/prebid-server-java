package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps;

import java.math.BigDecimal;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A set of methods for an adapter of {@link Box}
 * (compatible with {@link org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DeviceInfo})
 * to {@link org.prebid.server.hooks.modules.fiftyone.devicedetection.core.WritableDeviceInfo}
 * and back via temporary {@link BoxBuilder} instance.
 *
 * @param <Box> Type of Input/Output object to be patched with new data.
 * @param <BoxBuilder> Type of builder object to use for actually patching new data in.
 */
public record DeviceInfoBuilderMethodSet<Box, BoxBuilder>(
        Function<Box, BoxBuilder> builderFactory,
        Function<BoxBuilder, Box> builderMethod,
        BiConsumer<BoxBuilder, Integer> deviceTypeSetter,
        BiConsumer<BoxBuilder, String> makeSetter,
        BiConsumer<BoxBuilder, String> modelSetter,
        BiConsumer<BoxBuilder, String> osSetter,
        BiConsumer<BoxBuilder, String> osvSetter,
        BiConsumer<BoxBuilder, Integer> hSetter,
        BiConsumer<BoxBuilder, Integer> wSetter,
        BiConsumer<BoxBuilder, Integer> ppiSetter,
        BiConsumer<BoxBuilder, BigDecimal> pixelRatioSetter,
        Function<Box, BiConsumer<BoxBuilder, String>> deviceIdSetterFactory
) {
    /**
     * @param box Raw immutable object to get patched with new data.
     * @return Adapter to {@link org.prebid.server.hooks.modules.fiftyone.devicedetection.core.WritableDeviceInfo}
     *         that can be used to set new properties and recreate a new instance of {@link Box}.
     */
    public DeviceInfoBuilderAdapter<Box, BoxBuilder> makeAdapter(Box box) {
        return new DeviceInfoBuilderAdapter<>(box, this);
    }
}
