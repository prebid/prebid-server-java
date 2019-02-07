package org.prebid.server.bidder;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.response.BidderInfo;

import java.util.List;

/**
 * Gathers all dependencies for bidder.
 */
@Builder
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
     * Bidder's deprecated names.
     * <p>
     * Appropriate error message will be added to response.ext.errors.{bidder}
     * in case of bidder determined as deprecated.
     */
    List<String> deprecatedNames;

    /**
     * Bidder's aliases.
     * <p>
     * Indicates predefined synonyms for bidder along with regular name.
     */
    List<String> aliases;

    /**
     * Bidder's meta information is used in {@link org.prebid.server.handler.info.BidderDetailsHandler} handler
     */
    BidderInfo bidderInfo;

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
}
