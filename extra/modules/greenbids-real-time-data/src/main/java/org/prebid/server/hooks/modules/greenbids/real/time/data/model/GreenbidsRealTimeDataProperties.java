package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.Value;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;

@Value(staticConstructor = "of")
public class GreenbidsRealTimeDataProperties {

    @JsonProperty(value = "modelCacheWithExpiration", required = true)
    Cache<String, OnnxModelRunner> modelCacheWithExpiration;

    @JsonProperty(value = "thresholdsCacheWithExpiration", required = true)
    Cache<String, ThrottlingThresholds> thresholdsCacheWithExpiration;

    @JsonProperty(value = "geoLiteCountryPath", required = true)
    String geoLiteCountryPath;

    @JsonProperty(value = "googleCloudGreenbidsProject", required = true)
    String googleCloudGreenbidsProject;

    @JsonProperty(value = "gcsBucketName", required = true)
    String gcsBucketName;

    @JsonProperty(value = "cacheExpirationMinutes", required = true)
    Integer cacheExpirationMinutes;
}
