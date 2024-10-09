package org.prebid.server.hooks.modules.greenbids.real.time.data.model.config;

import lombok.Data;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.GreenbidsRealTimeDataModule;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hooks.modules." + GreenbidsRealTimeDataModule.CODE)
@Data
public class GreenbidsRealTimeDataProperties {

    String googleCloudGreenbidsProject;

    String geoLiteCountryPath;

    String gcsBucketName;

    Integer cacheExpirationMinutes;

    String onnxModelCacheKeyPrefix;

    String thresholdsCacheKeyPrefix;
}
