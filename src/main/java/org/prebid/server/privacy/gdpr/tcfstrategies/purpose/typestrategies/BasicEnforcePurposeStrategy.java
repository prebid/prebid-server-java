package org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies;

import com.iabtcf.decoder.TCString;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class BasicEnforcePurposeStrategy extends EnforcePurposeStrategy {

    private static final Logger logger = LoggerFactory.getLogger(BasicEnforcePurposeStrategy.class);

    public Collection<VendorPermission> allowedByTypeStrategy(PurposeCode purpose,
                                                              TCString vendorConsent,
                                                              Collection<VendorPermissionWithGvl> vendorsForPurpose,
                                                              Collection<VendorPermissionWithGvl> excludedVendors,
                                                              boolean isEnforceVendors) {

        logger.debug("Basic strategy used for purpose {0}", purpose);

        final List<VendorPermission> allowedVendorPermissions = vendorsForPurpose.stream()
                .map(VendorPermissionWithGvl::getVendorPermission)
                .filter(vendorPermission -> vendorPermission.getVendorId() != null)
                .filter(vendorPermission -> isAllowedBySimpleConsent(
                        purpose, vendorPermission.getVendorId(), isEnforceVendors, vendorConsent))
                .collect(Collectors.toList());

        return CollectionUtils.union(allowedVendorPermissions, toVendorPermissions(excludedVendors));
    }
}
