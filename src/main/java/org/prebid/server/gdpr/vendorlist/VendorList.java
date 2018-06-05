package org.prebid.server.gdpr.vendorlist;

import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;
import org.prebid.server.gdpr.vendorlist.proto.VendorListInfo;

/**
 * Describes a contract for working with GDPR Vendor List.
 */
public interface VendorList {

    /**
     * Fetches GDPR Vendor List by the given version.
     */
    Future<VendorListInfo> forVersion(int version, Timeout timeout);
}
