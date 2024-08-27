package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.Data;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.GreenbidsRealTimeDataModule;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "hooks.modules." + GreenbidsRealTimeDataModule.CODE)
@Validated
@Data
public class GreenbidsRealTimeDataProperties {

    @JsonProperty(value = "googleCloudGreenbidsProject", required = true)
    String googleCloudGreenbidsProject;

    @JsonProperty(value = "geoLiteCountryPath", required = true)
    String geoLiteCountryPath;

    @JsonProperty(value = "modelCacheWithExpiration", required = true)
    Cache<String, OnnxModelRunner> modelCacheWithExpiration;

    @JsonProperty(value = "thresholdsCacheWithExpiration", required = true)
    Cache<String, ThrottlingThresholds> thresholdsCacheWithExpiration;

    @JsonProperty(value = "gcsBucketName", required = true)
    String gcsBucketName;

    @JsonProperty(value = "cacheExpirationMinutes", required = true)
    Integer cacheExpirationMinutes;

    @JsonProperty(value = "onnxModelCacheKeyPrefix", required = true)
    String onnxModelCacheKeyPrefix;

    @JsonProperty(value = "thresholdsCacheKeyPrefix", required = true)
    String thresholdsCacheKeyPrefix;
}
