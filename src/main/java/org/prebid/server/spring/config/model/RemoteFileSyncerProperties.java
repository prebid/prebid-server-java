package org.prebid.server.spring.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Validated
@Data
@NoArgsConstructor
public class RemoteFileSyncerProperties {

    @NotBlank
    private String downloadUrl;

    @NotBlank
    private String saveFilepath;

    @NotNull
    @Min(1)
    private Integer retryCount;

    @NotNull
    @Min(1)
    private Long retryIntervalMs;

    @NotNull
    @Min(1)
    private Long timeoutMs;

    @NotNull
    private HttpClientProperties httpClient;
}
