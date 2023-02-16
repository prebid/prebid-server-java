package org.prebid.server.auction.gpp.processor.tcfeuv2;

import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.processor.GppContextProcessor;
import org.prebid.server.model.UpdateResult;

public class TcfEuV2ContextProcessor implements GppContextProcessor {

    @Override
    public GppContext process(GppContext gppContext) {
        final GppContext.Scope scope = gppContext.getScope();
        final GppContext.Regions regions = gppContext.getRegions();
        final GppContext.Regions.TcfEuV2Privacy tcfEuV2Privacy = regions.getTcfEuV2Privacy();

        final TcfEuV2Context tcfEuV2Context = TcfEuV2Context.of(scope.getGppModel(), scope.getSectionsIds());

        final UpdateResult<Integer> resolvedGdpr = tcfEuV2Context.resolveGdpr(tcfEuV2Privacy.getGdpr());
        final UpdateResult<String> resolvedConsent = tcfEuV2Context.resolveConsent(tcfEuV2Privacy.getConsent());

        gppContext.getErrors().addAll(tcfEuV2Context.getErrors());

        return resolvedGdpr.isUpdated() || resolvedConsent.isUpdated()

                ? gppContext.with(regions.toBuilder()
                .tcfEuV2Privacy(GppContext.Regions.TcfEuV2Privacy.of(
                        resolvedGdpr.getValue(),
                        resolvedConsent.getValue()))
                .build())

                : gppContext;
    }
}
