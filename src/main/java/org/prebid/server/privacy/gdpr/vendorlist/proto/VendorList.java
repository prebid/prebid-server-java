package org.prebid.server.privacy.gdpr.vendorlist.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Date;
import java.util.Map;

@Value(staticConstructor = "of")
public class VendorList {

    @JsonProperty("vendorListVersion")
    Integer vendorListVersion;

    @JsonProperty("lastUpdated")
    Date lastUpdated;

    Map<Integer, Vendor> vendors;
}
