package org.prebid.server.privacy.gdpr.vendorlist;

import lombok.AllArgsConstructor;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

// The purpose of this wrapper class is to avoid filtering the whole vendor list for deleted vendors
// TODO: move filtering logic to vendor list service
@AllArgsConstructor(staticName = "of")
public class VendorListWrapper {

    public static final VendorListWrapper EMPTY = VendorListWrapper.of(
            Collections.emptyMap(), Collections.emptySet(), Instant.EPOCH);

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
}
