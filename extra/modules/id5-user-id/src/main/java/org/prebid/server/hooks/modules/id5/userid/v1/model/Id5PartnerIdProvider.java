package org.prebid.server.hooks.modules.id5.userid.v1.model;

import org.prebid.server.auction.model.AuctionContext;

import javax.validation.constraints.NotNull;
import java.util.Optional;

/**
 * Provider for ID5 Partner ID that can return different values based on the auction context.
 * <p>
 * Implement this interface to provide dynamic Partner IDs based on account ID, channel, or other criteria.
 * Register your implementation as a Spring bean with @Component or in a @Configuration class.
 * <p>
 * The default implementation {@link ConstantId5PartnerId} returns a constant value from configuration.
 * <p>
 * If {@link #getPartnerId(AuctionContext)} returns {@link Optional#empty()}, the ID5 fetch will be skipped
 * for that request.
 */
public interface Id5PartnerIdProvider {

    /**
     * Returns the ID5 Partner ID for the given auction context.
     *
     * @param auctionContext the auction context containing account, request, and privacy information
     * @return Optional containing the Partner ID, or empty to skip the ID5 fetch for this request
     */
    @NotNull
    Optional<Long> getPartnerId(AuctionContext auctionContext);

}
