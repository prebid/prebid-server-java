package org.prebid.server.privacy.gdpr.tcfstrategies.typestrategies;

import com.iabtcf.decoder.TCString;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.privacy.gdpr.model.VendorPermission;

import java.util.Collection;

public class NoEnforcePurposeStrategy extends EnforcePurposeStrategy {

    private static final Logger logger = LoggerFactory.getLogger(NoEnforcePurposeStrategy.class);

    public Collection<VendorPermission> allowedByTypeStrategy(
            int purposeId,
            TCString vendorConsent,
            Collection<VendorPermission> vendorsForPurpose,
            boolean isEnforceVendors) {

        logger.debug("Basic strategy used fo purpose {0}", purposeId);
        return isEnforceVendors
                ? allowedByVendor(vendorConsent, vendorsForPurpose)
                : vendorsForPurpose;
    }
}
