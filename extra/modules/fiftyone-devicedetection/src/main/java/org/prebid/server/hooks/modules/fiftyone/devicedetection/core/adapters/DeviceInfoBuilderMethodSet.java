package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.adapters;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.WritableDeviceInfo;

import java.math.BigDecimal;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A set of methods for an adapter of {@link Box}
 * (compatible with {@link DeviceInfo})
 * to {@link WritableDeviceInfo}
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
     * @return Adapter to {@link WritableDeviceInfo}
     *         that can be used to set new properties and recreate a new instance of {@link Box}.
     */
    public Adapter makeAdapter(Box box) {
        return new Adapter(box);
    }

    public final class Adapter implements WritableDeviceInfo {
        private final BoxBuilder boxBuilder;
        private final BiConsumer<BoxBuilder, String> deviceIdSetter;

        public Adapter(Box box) {
            this.boxBuilder = builderFactory().apply(box);
            this.deviceIdSetter = deviceIdSetterFactory().apply(box);
        }

        public void setDeviceType(Integer deviceType) {
            deviceTypeSetter().accept(boxBuilder, deviceType);
        }
        public void setMake(String make) {
            makeSetter().accept(boxBuilder, make);
        }
        public void setModel(String model) {
            modelSetter().accept(boxBuilder, model);
        }
        public void setOs(String os) {
            osSetter().accept(boxBuilder, os);
        }
        public void setOsv(String osv) {
            osvSetter().accept(boxBuilder, osv);
        }
        public void setH(Integer h) {
            hSetter().accept(boxBuilder, h);
        }
        public void setW(Integer w) {
            wSetter().accept(boxBuilder, w);
        }
        public void setPpi(Integer ppi) {
            ppiSetter().accept(boxBuilder, ppi);
        }
        public void setPixelRatio(BigDecimal pixelRatio) {
            pixelRatioSetter().accept(boxBuilder, pixelRatio);
        }
        public void setDeviceId(String deviceId) {
            deviceIdSetter.accept(boxBuilder, deviceId);
        }

        public Box rebuildBox() {
            return builderMethod().apply(boxBuilder);
        }
    }
}
