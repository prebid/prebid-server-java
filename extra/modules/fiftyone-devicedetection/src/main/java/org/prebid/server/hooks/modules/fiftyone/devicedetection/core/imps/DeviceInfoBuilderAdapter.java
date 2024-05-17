package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.WritableDeviceInfo;

import java.math.BigDecimal;
import java.util.function.BiConsumer;


public class DeviceInfoBuilderAdapter<Box, BoxBuilder> implements WritableDeviceInfo {
    private final DeviceInfoBuilderMethodSet<Box, BoxBuilder> methods;
    private final BoxBuilder boxBuilder;
    private final BiConsumer<BoxBuilder, String> deviceIdSetter;

    public DeviceInfoBuilderAdapter(Box box, DeviceInfoBuilderMethodSet<Box, BoxBuilder> methods) {
        this.methods = methods;
        this.boxBuilder = methods.builderFactory().apply(box);
        this.deviceIdSetter = methods.deviceIdSetterFactory().apply(box);
    }

    public void setDeviceType(Integer deviceType) {
        methods.deviceTypeSetter().accept(boxBuilder, deviceType);
    }
    public void setMake(String make) {
        methods.makeSetter().accept(boxBuilder, make);
    }
    public void setModel(String model) {
        methods.modelSetter().accept(boxBuilder, model);
    }
    public void setOs(String os) {
        methods.osSetter().accept(boxBuilder, os);
    }
    public void setOsv(String osv) {
        methods.osvSetter().accept(boxBuilder, osv);
    }
    public void setH(Integer h) {
        methods.hSetter().accept(boxBuilder, h);
    }
    public void setW(Integer w) {
        methods.wSetter().accept(boxBuilder, w);
    }
    public void setPpi(Integer ppi) {
        methods.ppiSetter().accept(boxBuilder, ppi);
    }
    public void setPixelRatio(BigDecimal pixelRatio) {
        methods.pixelRatioSetter().accept(boxBuilder, pixelRatio);
    }
    public void setDeviceId(String deviceId) {
        deviceIdSetter.accept(boxBuilder, deviceId);
    }

    public Box rebuildBox() {
        return methods.builderMethod().apply(boxBuilder);
    }
}
