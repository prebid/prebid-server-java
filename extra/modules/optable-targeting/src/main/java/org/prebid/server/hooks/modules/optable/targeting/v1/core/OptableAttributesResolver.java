package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.gpp.encoder.GppModel;
import lombok.AllArgsConstructor;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.privacy.model.PrivacyContext;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@AllArgsConstructor
public class OptableAttributesResolver {

    private final IpResolver ipResolver;

    public OptableAttributes resolveAttributes(AuctionContext auctionContext, Long timeout) {
        final List<String> ips = ipResolver.resolveIp(auctionContext);

        OptableAttributes optableAttributes = getGdprPrivacyAttributes(auctionContext);
        if (optableAttributes == null) {
            optableAttributes = getGppPrivacyAttributes(auctionContext);
        }

        return optableAttributes != null
                ? optableAttributes.toBuilder().ips(ips).timeout(timeout).build()
                : OptableAttributes.builder().ips(ips).timeout(timeout).build();
    }

    private OptableAttributes getGppPrivacyAttributes(AuctionContext auctionContext) {
        final Optional<GppContext> gppContextOpt = Optional.ofNullable(auctionContext)
                .map(AuctionContext::getGppContext);

        final Optional<GppContext.Scope> gppScope = gppContextOpt
                .map(GppContext::scope);

        final String gppConsent = gppScope.map(GppContext.Scope::getGppModel)
                .map(GppModel::encode)
                .orElse(null);

        if (gppConsent != null) {
            final Set<Integer> sids = gppContextOpt
                    .map(GppContext::scope)
                    .map(GppContext.Scope::getSectionsIds)
                    .orElse(Set.of());

            return OptableAttributes.builder().gpp(gppConsent).gppSid(sids).build();

        }

        return null;
    }

    private OptableAttributes getGdprPrivacyAttributes(AuctionContext auctionContext) {
        return Optional.ofNullable(auctionContext)
                        .map(AuctionContext::getPrivacyContext)
                .map(PrivacyContext::getTcfContext)
                .map(ctx -> {

                    if (ctx.isConsentValid()) {
                        return OptableAttributes.builder()
                                .gdprConsent(ctx.getConsentString())
                                .gdprApplies(ctx.isInGdprScope())
                                .build();
                    }
                    return null;
                }).orElse(null);
    }
}
