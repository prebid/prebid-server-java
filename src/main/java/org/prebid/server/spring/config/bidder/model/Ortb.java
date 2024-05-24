package org.prebid.server.spring.config.bidder.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

@Data
@Validated
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class Ortb {

    @JsonProperty("multiformat-supported")
    @NotNull
    Boolean multiFormatSupported;
}
