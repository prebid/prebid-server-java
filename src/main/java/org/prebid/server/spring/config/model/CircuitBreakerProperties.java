package org.prebid.server.spring.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Validated
@Data
@NoArgsConstructor
public class CircuitBreakerProperties {

    @NotNull
    @Min(1)
    private Integer openingThreshold;

    @NotNull
    @Min(1)
    private Long openingIntervalMs;

    @NotNull
    @Min(1)
    private Long closingIntervalMs;
}
