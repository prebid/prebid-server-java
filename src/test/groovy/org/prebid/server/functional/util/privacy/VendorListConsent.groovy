package org.prebid.server.functional.util.privacy

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class VendorListConsent {

    Integer vendorListVersion
    Integer tcfPolicyVersion
    Integer gvlSpecificationVersion
}
