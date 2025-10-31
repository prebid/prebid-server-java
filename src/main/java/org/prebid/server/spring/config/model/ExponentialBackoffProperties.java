package org.prebid.server.spring.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
@Validated
@NoArgsConstructor
public class ExponentialBackoffProperties {

    @NotNull
    @Min(1)
    private Integer delayMillis;

    @NotNull
    @Min(1)
    private Integer maxDelayMillis;

    @NotNull
    @Min(0)
    private Double factor;

    @NotNull
    @Min(0)
    private Double jitter;
}
