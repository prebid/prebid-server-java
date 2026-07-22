package org.prebid.server.privacy.gdpr.vendorlist;

import lombok.RequiredArgsConstructor;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

// The purpose of this wrapper class is to avoid filtering the whole vendor list for deleted vendors
// TODO: move filtering logic to vendor list service
@RequiredArgsConstructor(staticName = "of")
public class VendorListWrapper {

    private final Map<Integer, Vendor> vendorList;
    private final Set<Integer> deletedVendorIds;
    private final Instant timestamp;

    public Vendor get(Integer key) {
        final Vendor vendor = vendorList.get(key);
        return isRetained(key, vendor) ? vendor : null;
    }

    private boolean isRetained(Integer id, Vendor vendor) {
        return vendor != null
                && !VendorListUtil.vendorIsDeletedAt(vendor, timestamp)
                && !deletedVendorIds.contains(id);
    }

    public static VendorListWrapper empty() {
        return VendorListWrapper.of(Collections.emptyMap(), Collections.emptySet(), null);
    }
}
