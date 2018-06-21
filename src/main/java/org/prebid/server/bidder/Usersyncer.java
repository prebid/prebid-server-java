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
     * Returns basic info the browser needs in order to run a user sync.
     * The returned object must not be mutated by callers.
     * <p>
     * For more information about user syncs, see http://clearcode.cc/2015/12/cookie-syncing/
     */
    UsersyncInfo usersyncInfo();
}
