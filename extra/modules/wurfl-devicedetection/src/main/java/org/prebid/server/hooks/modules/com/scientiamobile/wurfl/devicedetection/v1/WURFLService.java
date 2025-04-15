package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.scientiamobile.wurfl.core.Device;
import com.scientiamobile.wurfl.core.GeneralWURFLEngine;
import com.scientiamobile.wurfl.core.WURFLEngine;
import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.prebid.server.execution.file.FileProcessor;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config.WURFLDeviceDetectionConfigProperties;

@Slf4j
public class WURFLService implements FileProcessor {

    private final AtomicReference<WURFLEngine> arWurflEngine = new AtomicReference<>();
    private final WURFLDeviceDetectionConfigProperties configProperties;

    public WURFLService(WURFLEngine wurflEngine, WURFLDeviceDetectionConfigProperties configProperties) {
        this.arWurflEngine.set(wurflEngine);
        this.configProperties = configProperties;
    }

    protected WURFLEngine createEngine(String dataFilePath) {
        final WURFLEngine engine = new GeneralWURFLEngine(dataFilePath);
        engine.load();
        return engine;
    }

    public Future<?> setDataPath(String dataFilePath) {

        try {
            final WURFLEngine engine = this.createEngine(dataFilePath);
            this.arWurflEngine.set(engine);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }

        return Future.succeededFuture();
    }

    public Optional<Device> lookupDevice(Map<String, String> headers) {
        final WURFLEngine wurflEngine = arWurflEngine.get();
        return Optional.ofNullable(wurflEngine)
                .map(engine -> engine.getDeviceForRequest(headers));
    }

    public Set<String> getAllCapabilities() {
        final WURFLEngine wurflEngine = arWurflEngine.get();
        return Optional.ofNullable(wurflEngine)
                .map(WURFLEngine::getAllCapabilities)
                .orElse(Set.of());
    }

    public Set<String> getAllVirtualCapabilities() {
        final WURFLEngine wurflEngine = arWurflEngine.get();
        return Optional.ofNullable(wurflEngine)
                .map(WURFLEngine::getAllVirtualCapabilities)
                .orElse(Set.of());
    }
}
