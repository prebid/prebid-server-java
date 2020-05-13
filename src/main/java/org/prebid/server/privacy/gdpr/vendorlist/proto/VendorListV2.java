package org.prebid.server.privacy.gdpr.vendorlist.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Date;
import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class VendorListV2 {

    @JsonProperty("vendorListVersion")
    Integer vendorListVersion;

    @JsonProperty("lastUpdated")
    Date lastUpdated;

    Map<Integer, VendorV2> vendors;
}
