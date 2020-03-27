package org.prebid.server.privacy.gdpr.tcf2stratgies.typeStrategies;

import com.iabtcf.decoder.TCString;
import com.iabtcf.utils.IntIterable;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.privacy.gdpr.model.VendorPermission;

import java.util.ArrayList;
import java.util.Collection;

public class BasicTypeStrategy {

    private static final Logger logger = LoggerFactory.getLogger(BasicTypeStrategy.class);

    public Collection<VendorPermission> allowedByTypeStrategy(
            int purposeId,
            TCString vendorConsent,
            Collection<VendorPermission> vendorsForPurpose,
            boolean isEnforceVendors) {

        logger.info("Basic strategy used fo purpose {0}", purposeId);
        return isEnforceVendors
                ? allowedByVendor(vendorConsent, vendorsForPurpose)
                : allowedByPurposeAndVendor(purposeId, vendorConsent, vendorsForPurpose);
    }

    protected Collection<VendorPermission> allowedByVendor(TCString vendorConsent,
                                                           Collection<VendorPermission> vendorForCheck) {
        final Collection<VendorPermission> allowedByVendorAllowed = new ArrayList<>();

        final IntIterable allowedVendorLI = vendorConsent.getVendorLegitimateInterest();
        final IntIterable allowedVendors = vendorConsent.getAllowedVendors();
        for (VendorPermission vendorPermission : vendorForCheck) {
            final Integer vendorId = vendorPermission.getVendorId();
            if (vendorId != null && (allowedVendors.contains(vendorId) || allowedVendorLI.contains(vendorId))) {
                allowedByVendorAllowed.add(vendorPermission);
            }
        }
        return allowedByVendorAllowed;
    }

    // Purpose + Vendor
    protected Collection<VendorPermission> allowedByPurposeAndVendor(int purposeId,
                                                                     TCString vendorConsent,
                                                                     Collection<VendorPermission> vendorForCheck) {
        final Collection<VendorPermission> allowedByPurposeAndVendorAllowed = new ArrayList<>();

        final IntIterable purposesConsent = vendorConsent.getPurposesConsent();
        final IntIterable purposesLITransparency = vendorConsent.getPurposesLITransparency();

        final Collection<VendorPermission> allowedByVendor = allowedByVendor(vendorConsent, vendorForCheck);
        for (VendorPermission vendorPermission : allowedByVendor) {
            final Integer vendorId = vendorPermission.getVendorId();
            if (vendorId != null && (purposesConsent.contains(purposeId)
                    || purposesLITransparency.contains(purposeId))) {
                allowedByPurposeAndVendorAllowed.add(vendorPermission);
            }
        }
        return allowedByPurposeAndVendorAllowed;
    }
}

