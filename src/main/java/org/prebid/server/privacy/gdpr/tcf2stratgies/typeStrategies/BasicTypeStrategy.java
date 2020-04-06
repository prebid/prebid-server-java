package org.prebid.server.privacy.gdpr.tcf2stratgies.typeStrategies;

import com.iabtcf.decoder.TCString;
import com.iabtcf.utils.IntIterable;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.privacy.gdpr.model.VendorPermission;

import java.util.Collection;
import java.util.stream.Collectors;

public class BasicTypeStrategy extends PurposeTypeStrategy {

    private static final Logger logger = LoggerFactory.getLogger(BasicTypeStrategy.class);

    public Collection<VendorPermission> allowedByTypeStrategy(
            int purposeId,
            TCString vendorConsent,
            Collection<VendorPermission> vendorsForPurpose,
            boolean isEnforceVendors) {

        logger.debug("Basic strategy used fo purpose {0}", purposeId);
        return vendorsForPurpose.stream()
                .filter(vendorPermission -> isAllowedByConsentOrLegitimateInterest(purposeId, vendorPermission,
                        isEnforceVendors,
                        vendorConsent))
                .collect(Collectors.toList());

    }

    private boolean isAllowedByConsentOrLegitimateInterest(int purposeId,
                                                           VendorPermission vendorPermission,
                                                           boolean isEnforceVendor,
                                                           TCString tcString) {
        final Integer vendorId = vendorPermission.getVendorId();
        final IntIterable purposesConsent = tcString.getPurposesConsent();
        final IntIterable vendorConsent = tcString.getVendorConsent();

        final IntIterable purposesLIConsent = tcString.getPurposesLITransparency();
        final IntIterable vendorLIConsent = tcString.getVendorLegitimateInterest();

        return isAllowedByConsents(purposeId, vendorId, isEnforceVendor, purposesConsent, vendorConsent)
                || isAllowedByConsents(purposeId, vendorId, isEnforceVendor, purposesLIConsent, vendorLIConsent);
    }
}
