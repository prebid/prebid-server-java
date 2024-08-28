package org.prebid.server.hooks.modules.greenbids.real.time.data.model.config;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.Data;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor.OnnxModelRunner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.GreenbidsRealTimeDataModule;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hooks.modules." + GreenbidsRealTimeDataModule.CODE)
@Data
public class GreenbidsRealTimeDataProperties {

    String googleCloudGreenbidsProject;

    String geoLiteCountryPath;

    Cache<String, OnnxModelRunner> modelCacheWithExpiration;

    Cache<String, ThrottlingThresholds> thresholdsCacheWithExpiration;

    String gcsBucketName;

    Integer cacheExpirationMinutes;

    String onnxModelCacheKeyPrefix;

    String thresholdsCacheKeyPrefix;
}
