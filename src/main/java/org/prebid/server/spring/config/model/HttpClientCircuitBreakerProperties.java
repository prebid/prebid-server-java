package org.prebid.server.spring.config.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Validated
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HttpClientCircuitBreakerProperties extends CircuitBreakerProperties {

    @NotNull
    @Min(1)
    private Integer idleExpireHours;
}
