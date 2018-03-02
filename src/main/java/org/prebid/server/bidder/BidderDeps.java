package org.prebid.server.bidder;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.handler.CookieSyncHandler;

/**
 * Gathers all dependencies for bidder.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class BidderDeps {

    /**
     * Bidder's name that will be used to determine if a bidder.
     * <p>
     * For example, for OpenRTB 2.5 it should participate in auction by
     * inspecting bidrequest.imp[i].ext.{bidder} fields.
     */
    String name;

    /**
     * Bidder's user syncer is used in {@link CookieSyncHandler} handler and holds cookie family name.
     */
    Usersyncer usersyncer;

    /**
     * Bidder's adapter is used in legacy auction handling.
     */
    Adapter adapter;

    /**
     * Bidder's requester is used in OpenRTB 2.5 auction handling.
     */
    BidderRequester bidderRequester;
}
