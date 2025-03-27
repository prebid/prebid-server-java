package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.gpp.encoder.GppModel;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.PrivacyContext;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class OptableAttributesResolver {

    public OptableAttributes resolveAttributes(AuctionContext auctionContext, Long timeout) {
        final List<String> ips = IpResolver.resolveIp(auctionContext);

        return Optional.ofNullable(getGdprPrivacyAttributes(auctionContext))
                .or(() -> Optional.ofNullable(getGppPrivacyAttributes(auctionContext)))
                .map(OptableAttributes::toBuilder)
                .orElseGet(OptableAttributes::builder)
                .ips(ips)
                .timeout(timeout)
                .build();
    }

    private OptableAttributes getGppPrivacyAttributes(AuctionContext auctionContext) {
        final Optional<GppContext> gppContextOpt = Optional.ofNullable(auctionContext)
                .map(AuctionContext::getGppContext);

        final Optional<GppContext.Scope> gppScope = gppContextOpt
                .map(GppContext::scope);

        final String gppConsent = gppScope.map(GppContext.Scope::getGppModel)
                .map(GppModel::encode)
                .orElse(null);

        if (gppConsent == null) {
            return null;
        }

        final Set<Integer> sids = gppContextOpt
                .map(GppContext::scope)
                .map(GppContext.Scope::getSectionsIds)
                .orElse(Collections.emptySet());

        return OptableAttributes.builder().gpp(gppConsent).gppSid(sids).build();
    }

    private OptableAttributes getGdprPrivacyAttributes(AuctionContext auctionContext) {
        return Optional.ofNullable(auctionContext)
                .map(AuctionContext::getPrivacyContext)
                .map(PrivacyContext::getTcfContext)
                .filter(TcfContext::isConsentValid)
                .map(ctx -> OptableAttributes.builder()
                        .gdprConsent(ctx.getConsentString())
                        .gdprApplies(ctx.isInGdprScope())
                        .build())
                .orElse(null);
    }
}
