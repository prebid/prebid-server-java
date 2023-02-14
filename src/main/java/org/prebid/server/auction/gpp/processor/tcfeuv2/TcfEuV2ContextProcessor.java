package org.prebid.server.auction.gpp.processor.tcfeuv2;

import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.processor.GppContextProcessor;

public class TcfEuV2ContextProcessor implements GppContextProcessor {

    @Override
    public GppContext process(GppContext gppContext) {
        final GppContext.Scope scope = gppContext.getScope();
        final GppContext.Regions regions = gppContext.getRegions();
        final GppContext.Regions.TcfEuV2Privacy tcfEuV2Privacy = regions.getTcfEuV2Privacy();

        final TcfEuV2Context tcfEuV2Context = TcfEuV2Context.of(scope.getGppModel(), scope.getSectionsIds());

        final Integer gdpr = tcfEuV2Privacy.getGdpr();
        final Integer resolvedGdpr = tcfEuV2Context.resolveGdpr(gdpr);

        final String consent = tcfEuV2Privacy.getConsent();
        final String resolvedConsent = tcfEuV2Context.resolveConsent(consent);

        gppContext.getErrors().addAll(tcfEuV2Context.getErrors());

        return ObjectUtils.anyNotNull(resolvedGdpr, resolvedConsent)

                ? gppContext.with(regions.toBuilder()
                .tcfEuV2Privacy(GppContext.Regions.TcfEuV2Privacy.of(
                        ObjectUtils.defaultIfNull(resolvedGdpr, gdpr),
                        ObjectUtils.defaultIfNull(resolvedConsent, consent)))
                .build())

                : gppContext;
    }
}
