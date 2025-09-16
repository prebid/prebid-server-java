package org.prebid.server.functional.util.privacy

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.prebid.server.functional.model.mock.services.vendorlist.GvlSpecificationVersion

@JsonIgnoreProperties(ignoreUnknown = true)
class VendorListConsent {

    Integer vendorListVersion
    Integer tcfPolicyVersion
    GvlSpecificationVersion gvlSpecificationVersion
}
