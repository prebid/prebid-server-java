package org.prebid.server.privacy.gdpr.vendorlist;

import lombok.Value;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorList;

@Value(staticConstructor = "of")
class VendorListResult {

    int version;

    String vendorListAsString;

    VendorList vendorList;
}
