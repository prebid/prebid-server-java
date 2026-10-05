package org.prebid.server.privacy.gdpr.vendorlist;

import com.iabtcf.decoder.TCString;
import io.vertx.core.Future;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

public class VersionedVendorListService {

    private final VendorListService vendorListServiceV2;
    private final VendorListService vendorListServiceV3;
    private final LiveVendorListService liveVendorListService;
    private final Clock clock;

    public VersionedVendorListService(VendorListService vendorListServiceV2,
                                      VendorListService vendorListServiceV3,
                                      LiveVendorListService liveVendorListService,
                                      Clock clock) {

        this.vendorListServiceV2 = Objects.requireNonNull(vendorListServiceV2);
        this.vendorListServiceV3 = Objects.requireNonNull(vendorListServiceV3);
        this.liveVendorListService = Objects.requireNonNull(liveVendorListService);
        this.clock = Objects.requireNonNull(clock);
    }

    public Future<VendorListWrapper> forConsent(TCString consent) {
        final int tcfPolicyVersion = consent.getTcfPolicyVersion();
        final int vendorListVersion = consent.getVendorListVersion();

        final Future<Map<Integer, Vendor>> vendorListFuture = tcfPolicyVersion < 4
                ? vendorListServiceV2.forVersion(vendorListVersion)
                : vendorListServiceV3.forVersion(vendorListVersion);

        return vendorListFuture.map(vendors ->
                VendorListWrapper.of(vendors, liveVendorListService.getDeletedVendorIds(), clock.instant()));
    }
}
