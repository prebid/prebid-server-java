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
public class MedianetGeoServiceProperties {

    @NotBlank
    private String endpoint;

    @NotNull
    @Min(1)
    private long timeout;
}
