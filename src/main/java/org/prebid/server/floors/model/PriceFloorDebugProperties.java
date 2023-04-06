package org.prebid.server.floors.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;

@Validated
@Data
@NoArgsConstructor
public class PriceFloorDebugProperties {

    @Min(1)
    Long minMaxAgeSec;

    @Min(1)
    Long minPeriodSec;

    @Min(1)
    Long minTimeoutMs;

    @Min(1)
    Long maxTimeoutMs;
}
