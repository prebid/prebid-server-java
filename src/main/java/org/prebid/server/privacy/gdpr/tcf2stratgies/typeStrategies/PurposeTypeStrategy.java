package org.prebid.server.privacy.gdpr.tcf2stratgies.typeStrategies;

import com.iabtcf.decoder.TCString;
import com.iabtcf.utils.IntIterable;
import org.prebid.server.privacy.gdpr.model.VendorPermission;

import java.util.ArrayList;
import java.util.Collection;

public abstract class PurposeTypeStrategy {

    public abstract Collection<VendorPermission> allowedByTypeStrategy(
            int purposeId,
            TCString vendorConsent,
            Collection<VendorPermission> vendorsForPurpose,
            boolean isEnforceVendors);

    protected Collection<VendorPermission> allowedByVendor(TCString vendorConsent,
                                                           Collection<VendorPermission> vendorForCheck) {
        final Collection<VendorPermission> allowedByVendorAllowed = new ArrayList<>();

        final IntIterable allowedVendors = vendorConsent.getVendorConsent();
        final IntIterable allowedVendorsLI = vendorConsent.getVendorLegitimateInterest();
        for (VendorPermission vendorPermission : vendorForCheck) {
            final Integer vendorId = vendorPermission.getVendorId();
            if (vendorId != null && (allowedVendors.contains(vendorId) || allowedVendorsLI.contains(vendorId))) {
                allowedByVendorAllowed.add(vendorPermission);
            }
        }
        return allowedByVendorAllowed;
    }

    protected Collection<VendorPermission> allowedByPurpose(int purposeId,
                                                            TCString vendorConsent,
                                                            Collection<VendorPermission> vendorForCheck) {
        final Collection<VendorPermission> allowedByPurposeAndVendorAllowed = new ArrayList<>();

        final IntIterable purposesConsent = vendorConsent.getPurposesConsent();
        final IntIterable purposesLITransparency = vendorConsent.getPurposesLITransparency();
        for (VendorPermission vendorPermission : vendorForCheck) {
            final Integer vendorId = vendorPermission.getVendorId();
            if (vendorId != null && (purposesConsent.contains(purposeId)
                    || purposesLITransparency.contains(purposeId))) {
                allowedByPurposeAndVendorAllowed.add(vendorPermission);
            }
        }
        return allowedByPurposeAndVendorAllowed;
    }

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
