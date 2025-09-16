package org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies;

import com.iabtcf.decoder.TCString;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;

import java.util.Collection;
import java.util.stream.Stream;

public class BasicEnforcePurposeStrategy extends EnforcePurposeStrategy {

    private static final Logger logger = LoggerFactory.getLogger(BasicEnforcePurposeStrategy.class);

    public Stream<VendorPermission> allowedByTypeStrategy(PurposeCode purpose,
                                                          TCString vendorConsent,
                                                          Collection<VendorPermissionWithGvl> vendorsForPurpose,
                                                          Collection<VendorPermissionWithGvl> excludedVendors,
                                                          boolean isEnforceVendors) {

        logger.debug("Basic strategy used for purpose {}", purpose);

        final Stream<VendorPermission> allowedVendorPermissions = toVendorPermissions(vendorsForPurpose)
                .filter(vendorPermission -> vendorPermission.getVendorId() != null)
                .filter(vendorPermission -> isAllowedBySimpleConsent(
                        purpose, vendorPermission.getVendorId(), isEnforceVendors, vendorConsent));

        return Stream.concat(allowedVendorPermissions, toVendorPermissions(excludedVendors));
    }
}
