package org.rtb.vexing.spring.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stored-requests")
@Data
@NoArgsConstructor
public class StoredRequestProperties {
    private String type;
    private String configpath;
    private Integer maxPoolSize;
    private String query;
    private String host;
    private String dbname;
    private String user;
    private String password;
}
