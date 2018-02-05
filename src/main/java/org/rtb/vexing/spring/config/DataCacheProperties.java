package org.rtb.vexing.spring.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "datacache")
@Data
@NoArgsConstructor
public class DataCacheProperties {
    private String type;
    private String filename;
    private Integer ttlSeconds;
    private Integer cacheSize;
}
