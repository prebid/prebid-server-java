package org.prebid.server.auction.gpp.processor.uspv1;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.EncodingException;
import com.iab.gpp.encoder.section.UspV1;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.privacy.UspV1Privacy;
import org.prebid.server.auction.gpp.processor.GppContextProcessor;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.model.UpdateResult;

import java.util.List;
import java.util.Set;

public class UspV1ContextProcessor implements GppContextProcessor {

    @Override
    public GppContext process(GppContext gppContext) {
        final GppContext.Scope scope = gppContext.scope();
        final UspV1Privacy uspV1Privacy = gppContext.regions().getUspV1Privacy();

        final UpdateResult<String> resolvedUsPrivacy = resolveUsPrivacy(
                uspV1Privacy.getUsPrivacy(),
                scope.getGppModel(),
                scope.getSectionsIds(),
                gppContext.errors());

        return resolvedUsPrivacy.isUpdated()
                ? gppContext.with(UspV1Privacy.of(resolvedUsPrivacy.getValue()))
                : gppContext;
    }

    private static UpdateResult<String> resolveUsPrivacy(String usPrivacy,
                                                         GppModel gppModel,
                                                         Set<Integer> sectionsIds,
                                                         List<String> errors) {

        if (!isValidScope(gppModel, sectionsIds)) {
            return UpdateResult.unaltered(usPrivacy);
        }

        if (usPrivacy == null) {
            return UpdateResult.updated(usPrivacyFromGpp(gppModel));
        }

        try {
            validateExistingUsPrivacy(usPrivacy, gppModel);
        } catch (PreBidException e) {
            errors.add(e.getMessage());
        }

        return UpdateResult.unaltered(usPrivacy);
    }

    private static boolean isValidScope(GppModel gppModel, Set<Integer> sectionsIds) {
        return sectionsIds != null && sectionsIds.contains(UspV1.ID)
                && gppModel != null && gppModel.hasSection(UspV1.ID);
    }

    private static String usPrivacyFromGpp(GppModel gppModel) {
        try {
            return gppModel.encodeSection(UspV1.ID);
        } catch (EncodingException e) {
            return null;
        }
    }

    private static void validateExistingUsPrivacy(String usPrivacy, GppModel gppModel) {
        if (!usPrivacy.equals(usPrivacyFromGpp(gppModel))) {
            throw new PreBidException("USP string does not match regs.us_privacy");
        }
    }
}
