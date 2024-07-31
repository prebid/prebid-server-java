package org.prebid.server.privacy.gdpr.vendorlist;

import com.iabtcf.decoder.TCString;
import io.vertx.core.Future;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;

import java.util.Map;
import java.util.Objects;

public class VersionedVendorListService {

    private final VendorListService vendorListServiceV2;
    private final VendorListService vendorListServiceV3;

    public VersionedVendorListService(VendorListService vendorListServiceV2, VendorListService vendorListServiceV3) {
        this.vendorListServiceV2 = Objects.requireNonNull(vendorListServiceV2);
        this.vendorListServiceV3 = Objects.requireNonNull(vendorListServiceV3);
    }

    public Future<Map<Integer, Vendor>> forConsent(TCString consent) {
        final int tcfPolicyVersion = consent.getTcfPolicyVersion();
        final int vendorListVersion = consent.getVendorListVersion();
        if (tcfPolicyVersion > 5) {
            return Future.failedFuture(new PreBidException(
                    "Invalid tcf policy version: %d".formatted(tcfPolicyVersion)));
        }

        return tcfPolicyVersion < 4
                ? vendorListServiceV2.forVersion(vendorListVersion)
                : vendorListServiceV3.forVersion(vendorListVersion);
    }
}
