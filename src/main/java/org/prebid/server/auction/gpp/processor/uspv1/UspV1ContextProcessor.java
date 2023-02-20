package org.prebid.server.auction.gpp.processor.uspv1;

import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.privacy.UspV1Privacy;
import org.prebid.server.auction.gpp.processor.GppContextProcessor;
import org.prebid.server.model.UpdateResult;

public class UspV1ContextProcessor implements GppContextProcessor {

    @Override
    public GppContext process(GppContext gppContext) {
        final GppContext.Scope scope = gppContext.scope();
        final GppContext.Regions regions = gppContext.regions();
        final UspV1Privacy uspV1Privacy = regions.getUspV1Privacy();

        final UspV1Context uspV1Context = UspV1Context.of(scope.getGppModel(), scope.getSectionsIds());

        final UpdateResult<String> resolvedUsPrivacy = uspV1Context.resolveUsPrivacy(uspV1Privacy.getUsPrivacy());

        gppContext.errors().addAll(uspV1Context.getErrors());

        return resolvedUsPrivacy.isUpdated()
                ? gppContext.with(UspV1Privacy.of(resolvedUsPrivacy.getValue()))
                : gppContext;
    }
}
