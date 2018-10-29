package org.prebid.server.bidder;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

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

    List<String> deprecatedNames;

    /**
     * Bidder's meta information is used in {@link org.prebid.server.handler.info.BidderDetailsHandler} handler
     */
    MetaInfo metaInfo;

    /**
     * Bidder's user syncer is used in {@link org.prebid.server.handler.CookieSyncHandler} handler and holds cookie
     * family name.
     */
    Usersyncer usersyncer;

    /**
     * Bidder implementation is used in auction handling.
     */
    Bidder<?> bidder;

    /**
     * Bidder's adapter is used in legacy auction handling.
     */
    Adapter<?, ?> adapter;

    /**
     * Bidder's requester is used in OpenRTB 2.5 auction handling.
     */
    BidderRequester bidderRequester;
}
