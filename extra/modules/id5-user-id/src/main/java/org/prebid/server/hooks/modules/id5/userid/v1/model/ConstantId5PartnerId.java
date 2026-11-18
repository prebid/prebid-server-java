package org.prebid.server.hooks.modules.id5.userid.v1.model;

import org.prebid.server.auction.model.AuctionContext;

import java.util.Optional;

public class ConstantId5PartnerId implements Id5PartnerIdProvider {

    private final long partnerId;

    public ConstantId5PartnerId(long partnerId) {
        this.partnerId = partnerId;
    }

    @Override
    public Optional<Long> getPartnerId(AuctionContext ignore) {
        return Optional.of(partnerId);
    }
}
