package org.prebid.server.privacy.gdpr.model;

import lombok.Value;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorV2;

@Value(staticConstructor = "of")
public class VendorPermissionWithGvl {

    VendorPermission vendorPermission;

    VendorV2 vendorV2;
}
