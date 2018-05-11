package org.prebid.server.bidder;

import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.proto.response.UsersyncInfo;

/**
 * Describes the behavior for {@link Usersyncer} implementations.
 */
public interface Usersyncer {

    /**
     * Provides a family name by which user ids within {@link Adapter}/{@link Bidder}'s
     * realm are stored in {@link UidsCookie}.
     */
    String cookieFamilyName();

    /**
     * Determines GDPR vendor ID in the IAB Global Vendor List which refers to this Bidder.
     * <p>
     * The Global Vendor list can be found here: https://vendorlist.consensu.org/vendorlist.json
     * Bidders can register for the list here: https://register.consensu.org/
     * <p>
     * If you're not on the list, this should return 0. If cookie sync requests have GDPR consent info,
     * or the Prebid Server host company configures its deploy to be "cautious" when no GDPR info exists
     * in the request, it will _not_ sync user IDs with you.
     */
    int gdprVendorId();

    /**
     * Returns basic info the browser needs in order to run a user sync.
     * The returned object must not be mutated by callers.
     * <p>
     * For more information about user syncs, see http://clearcode.cc/2015/12/cookie-syncing/
     */
    UsersyncInfo usersyncInfo();
}
