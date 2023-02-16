package org.prebid.server.auction.gpp.processor.uspv1;

import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.processor.GppContextProcessor;
import org.prebid.server.model.UpdateResult;

public class UspV1ContextProcessor implements GppContextProcessor {

    @Override
    public GppContext process(GppContext gppContext) {
        final GppContext.Scope scope = gppContext.getScope();
        final GppContext.Regions regions = gppContext.getRegions();
        final GppContext.Regions.UspV1Privacy uspV1Privacy = regions.getUspV1Privacy();

        final UspV1Context uspV1Context = UspV1Context.of(scope.getGppModel(), scope.getSectionsIds());

        final String usPrivacy = uspV1Privacy.getUsPrivacy();
        final UpdateResult<String> resolvedUsPrivacy = uspV1Context.resolveUsPrivacy(usPrivacy);

        gppContext.getErrors().addAll(uspV1Context.getErrors());

        return resolvedUsPrivacy.isUpdated()

                ? gppContext.with(regions.toBuilder()
                .uspV1Privacy(GppContext.Regions.UspV1Privacy.of(resolvedUsPrivacy.getValue()))
                .build())

                : gppContext;
    }
}
