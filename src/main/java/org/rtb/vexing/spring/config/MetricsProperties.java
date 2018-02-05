package org.rtb.vexing.spring.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "metrics")
@Data
@NoArgsConstructor
public class MetricsProperties {
    private String type;
    private String host;
    private String prefix;
    private String protocol;
    private String database;
    private String auth;
    private Integer connectTimeout;
    private Integer readTimeout;
    private Integer interval;
}
