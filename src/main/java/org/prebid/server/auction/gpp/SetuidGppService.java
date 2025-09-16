package org.prebid.server.auction.gpp;

import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextCreator;
import org.prebid.server.auction.gpp.model.GppContextWrapper;
import org.prebid.server.auction.gpp.model.privacy.TcfEuV2Privacy;
import org.prebid.server.auction.model.SetuidContext;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;

import java.util.List;
import java.util.Objects;

public class SetuidGppService {

    private final GppService gppService;

    public SetuidGppService(GppService gppService) {
        this.gppService = Objects.requireNonNull(gppService);
    }

    public Future<GppContext> contextFrom(SetuidContext setuidContext) {
        final GppContextWrapper initialGppContextWrapper = contextFrom(setuidContext.getPrivacyContext());
        final GppContextWrapper gppContextWrapper = gppService.processContext(initialGppContextWrapper);

        return Future.succeededFuture(gppContextWrapper.getGppContext());
    }

    private static GppContextWrapper contextFrom(PrivacyContext privacyContext) {
        final Privacy privacy = privacyContext.getPrivacy();

        final String gpp = privacy.getGpp();
        final List<Integer> gppSid = privacy.getGppSid();

        final Integer gdpr = toInt(privacy.getGdpr());
        final String consent = privacy.getConsentString();

        return GppContextCreator.from(gpp, gppSid)
                .with(TcfEuV2Privacy.of(gdpr, consent))
                .build();
    }

    private static Integer toInt(String string) {
        try {
            return StringUtils.isNotBlank(string) ? Integer.parseInt(string) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public SetuidContext updateSetuidContext(SetuidContext setuidContext) {
        final GppContext gppContext = setuidContext.getGppContext();
        final PrivacyContext privacyContext = setuidContext.getPrivacyContext();
        final Privacy privacy = privacyContext.getPrivacy();

        final GppContext.Regions regions = gppContext.regions();
        final TcfEuV2Privacy tcfEuV2Privacy = regions.getTcfEuV2Privacy();

        final UpdateResult<Integer> updatedGdpr = updateResult(
                toInt(privacy.getGdpr()),
                tcfEuV2Privacy != null ? tcfEuV2Privacy.getGdpr() : null);
        final UpdateResult<String> updatedConsent = updateResult(
                privacy.getConsentString(),
                tcfEuV2Privacy != null ? tcfEuV2Privacy.getConsent() : null);

        return updatedGdpr.isUpdated() || updatedConsent.isUpdated()
                ? setuidContext.toBuilder()
                .privacyContext(PrivacyContext.of(
                        privacy.toBuilder()
                                .gdpr(updatedGdpr.getValue().toString())
                                .consentString(updatedConsent.getValue())
                                .build(),
                        privacyContext.getTcfContext(),
                        privacyContext.getIpAddress()))
                .build()
                : setuidContext;
    }

    private static <T> UpdateResult<T> updateResult(T original, T gpp) {
        return original == null && gpp != null
                ? UpdateResult.updated(gpp)
                : UpdateResult.unaltered(original);
    }
}
