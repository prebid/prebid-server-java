package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.scientiamobile.wurfl.core.Device;
import com.scientiamobile.wurfl.core.GeneralWURFLEngine;
import com.scientiamobile.wurfl.core.WURFLEngine;
import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.prebid.server.execution.file.FileProcessor;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config.WURFLDeviceDetectionConfigProperties;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.model.WURFLEngineInitializer;

@Slf4j
public class WURFLService implements FileProcessor {

    private WURFLEngine wurflEngine;
    private WURFLDeviceDetectionConfigProperties configProperties;

    public WURFLService(WURFLEngine wurflEngine, WURFLDeviceDetectionConfigProperties configProperties) {
        this.wurflEngine = wurflEngine;
        this.configProperties = configProperties;
    }

    public Future<?> setDataPath(String dataFilePath) {

        log.info("setDataPath invoked");
        try {
            final WURFLEngine engine = new GeneralWURFLEngine(dataFilePath);
            engine.load();
            final String fileName = WURFLEngineInitializer.extractWURFLFileName(configProperties.getWurflSnapshotUrl());
            final Path dir = Paths.get(configProperties.getWurflFileDirPath());
            final Path file = dir.resolve(fileName);
            Files.move(Paths.get(dataFilePath), file, StandardCopyOption.REPLACE_EXISTING);
            wurflEngine.reload(file.toAbsolutePath().toString());
        } catch (Exception e) {
            return Future.failedFuture(e);
        }

        return Future.succeededFuture();
    }

    public Optional<Device> lookupDevice(Map<String, String> headers) {
        return Optional.ofNullable(wurflEngine)
                .map(engine -> engine.getDeviceForRequest(headers));
    }

    public Set<String> getAllCapabilities() {
        return Optional.ofNullable(wurflEngine)
                .map(WURFLEngine::getAllCapabilities)
                .orElse(Set.of());
    }

    public Set<String> getAllVirtualCapabilities() {
        return Optional.ofNullable(wurflEngine)
                .map(WURFLEngine::getAllVirtualCapabilities)
                .orElse(Set.of());
    }
}
