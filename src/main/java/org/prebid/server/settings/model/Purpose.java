package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class Purpose {

    @JsonProperty(value = "enforce-purpose", defaultValue = "full")
    EnforcePurpose enforcePurpose;

    @JsonProperty(value = "enforce-vendors", defaultValue = "true")
    Boolean enforceVendors;

    @JsonProperty("vendor-exceptions")
    List<String> vendorExceptions;
}
