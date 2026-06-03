package org.prebid.server.privacy.gdpr.vendorlist;

import com.iabtcf.decoder.TCString;
import io.vertx.core.Future;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class VersionedVendorListService {

    private final VendorListService vendorListServiceV2;
    private final VendorListService vendorListServiceV3;
    private final LiveVendorListService liveVendorListService;

    public VersionedVendorListService(VendorListService vendorListServiceV2,
                                      VendorListService vendorListServiceV3,
                                      LiveVendorListService liveVendorListService) {

        this.vendorListServiceV2 = Objects.requireNonNull(vendorListServiceV2);
        this.vendorListServiceV3 = Objects.requireNonNull(vendorListServiceV3);
        this.liveVendorListService = Objects.requireNonNull(liveVendorListService);
    }

    public Future<Map<Integer, Vendor>> forConsent(TCString consent) {
        final int tcfPolicyVersion = consent.getTcfPolicyVersion();
        final int vendorListVersion = consent.getVendorListVersion();

        final Future<Map<Integer, Vendor>> vendorListFuture = tcfPolicyVersion < 4
                ? vendorListServiceV2.forVersion(vendorListVersion)
                : vendorListServiceV3.forVersion(vendorListVersion);

        return vendorListFuture.map(this::filterDeletedVendors);
    }

    private Map<Integer, Vendor> filterDeletedVendors(Map<Integer, Vendor> vendors) {
        return vendors.entrySet().stream()
                .filter(entry -> !liveVendorListService.isDeleted(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
