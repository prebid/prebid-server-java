package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class SpecialFeature {

    @JsonProperty(defaultValue = "true")
    Boolean enforce;

    @JsonProperty("vendor-exceptions")
    List<Integer> vendorExceptions;
}

