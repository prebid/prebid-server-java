package org.prebid.server.hooks.modules.greenbids.real.time.data.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hooks.modules." + GreenbidsRealTimeDataModule.CODE)
@Data
public class GreenbidsRealTimeDataProperties {

    String googleCloudGreenbidsProject;

    String geoLiteCountryPath;

    String tmpPath;

    String gcsBucketName;

    Integer cacheExpirationMinutes;

    String onnxModelCacheKeyPrefix;

    String thresholdsCacheKeyPrefix;

    Long timeoutMs;

    Integer maxRedirects;
}
