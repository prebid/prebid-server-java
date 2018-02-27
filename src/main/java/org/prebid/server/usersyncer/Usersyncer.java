package org.prebid.server.usersyncer;

import org.prebid.server.adapter.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.model.response.UsersyncInfo;

/**
 * Describes the behavior for {@link Usersyncer} implementations.
 */
public interface Usersyncer {

    /**
     * Returns usersyncer's name that will be used to determine a corresponding {@link Adapter}/{@link Bidder}.
     */
    String name();

    /**
     * Provides a family name by which user ids within {@link Adapter}/{@link Bidder}'s realm are stored in
     * {@link UidsCookie}.
     */
    String cookieFamilyName();

    /**
     * Returns basic info the browser needs in order to run a user sync.
     * The returned object must not be mutated by callers.
     * For more information about user syncs, see http://clearcode.cc/2015/12/cookie-syncing/
     */
    UsersyncInfo usersyncInfo();
}
