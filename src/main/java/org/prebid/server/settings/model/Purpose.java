package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class Purpose {

    @JsonAlias("enforce-purpose")
    EnforcePurpose enforcePurpose;

    @JsonAlias("enforce-vendors")
    Boolean enforceVendors;

    @JsonAlias("vendor-exceptions")
    List<String> vendorExceptions;

    PurposeEid eid;
}
