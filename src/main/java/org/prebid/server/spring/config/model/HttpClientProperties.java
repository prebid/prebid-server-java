package org.prebid.server.spring.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Validated
@Data
@NoArgsConstructor
public class HttpClientProperties {

    @Min(1)
    private Integer maxPoolSize;

    private Integer idleTimeoutMs;

    private Integer poolCleanerPeriodMs;

    @NotNull
    @Min(1)
    private Integer connectTimeoutMs;

    private Boolean useCompression;

    @NotNull
    private Integer maxRedirects;

    private Boolean ssl;

    private String jksPath;

    private String jksPassword;
}
