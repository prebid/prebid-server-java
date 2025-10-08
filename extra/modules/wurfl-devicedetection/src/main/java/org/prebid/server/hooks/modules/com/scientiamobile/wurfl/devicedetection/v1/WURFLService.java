package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import com.scientiamobile.wurfl.core.Device;
import com.scientiamobile.wurfl.core.WURFLEngine;
import io.vertx.core.Future;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.execution.file.FileProcessor;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config.WURFLDeviceDetectionConfigProperties;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.model.WURFLEngineUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class WURFLService implements FileProcessor {

    private static final Logger logger = LoggerFactory.getLogger(WURFLService.class);

    private final AtomicReference<WURFLEngine> wurflEngine;
    private final WURFLDeviceDetectionConfigProperties configProperties;

    public WURFLService(WURFLEngine wurflEngine, WURFLDeviceDetectionConfigProperties configProperties) {
        this.wurflEngine = new AtomicReference<>(wurflEngine);
        this.configProperties = Objects.requireNonNull(configProperties);
    }

    public Future<?> setDataPath(String dataFilePath) {
        try {
            final WURFLEngine engine = createEngine(dataFilePath);
            this.wurflEngine.set(engine);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }

        return Future.succeededFuture();
    }

    protected WURFLEngine createEngine(String dataFilePath) {
        final WURFLEngine wurflEngine = WURFLEngineUtils.initializeEngine(configProperties, dataFilePath);
        wurflEngine.load();
        logger.info("WURFL Engine initialized");
        return wurflEngine;
    }

    public Optional<Device> lookupDevice(Map<String, String> headers) {
        return Optional.ofNullable(wurflEngine.get())
                .map(engine -> engine.getDeviceForRequest(headers));
    }

    public Set<String> getAllCapabilities() {
        return Optional.ofNullable(wurflEngine.get())
                .map(WURFLEngine::getAllCapabilities)
                .orElse(Collections.emptySet());
    }

    public Set<String> getAllVirtualCapabilities() {
        return Optional.ofNullable(wurflEngine.get())
                .map(WURFLEngine::getAllVirtualCapabilities)
                .orElse(Collections.emptySet());
    }
}

