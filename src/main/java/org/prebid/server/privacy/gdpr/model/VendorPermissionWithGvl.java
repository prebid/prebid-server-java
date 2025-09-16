package org.prebid.server.privacy.gdpr.model;

import lombok.Value;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;

@Value(staticConstructor = "of")
public class VendorPermissionWithGvl {

    VendorPermission vendorPermission;

    Vendor vendor;
}
