package org.prebid.server.auction.gpp;

import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextCreator;
import org.prebid.server.auction.gpp.model.GppContextWrapper;
import org.prebid.server.auction.gpp.model.privacy.TcfEuV2Privacy;
import org.prebid.server.auction.gpp.model.privacy.UspV1Privacy;
import org.prebid.server.cookie.model.CookieSyncContext;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.proto.request.CookieSyncRequest;

import java.util.List;
import java.util.Objects;

public class CookieSyncGppService {

    private final GppService gppService;

    public CookieSyncGppService(GppService gppService) {
        this.gppService = Objects.requireNonNull(gppService);
    }

    public GppContext contextFrom(CookieSyncContext cookieSyncContext) {
        final GppContextWrapper initialGppContextWrapper = contextFrom(cookieSyncContext.getCookieSyncRequest());
        final GppContextWrapper gppContextWrapper = gppService.processContext(initialGppContextWrapper);

        enrichWithErrors(cookieSyncContext, gppContextWrapper.getErrors());

        return gppContextWrapper.getGppContext();
    }

    private static GppContextWrapper contextFrom(CookieSyncRequest cookieSyncRequest) {
        final String gpp = cookieSyncRequest.getGpp();
        final List<Integer> gppSid = cookieSyncRequest.getGppSid();

        final Integer gdpr = cookieSyncRequest.getGdpr();
        final String consent = cookieSyncRequest.getGdprConsent();

        final String usPrivacy = cookieSyncRequest.getUsPrivacy();

        return GppContextCreator.from(gpp, gppSid)
                .with(TcfEuV2Privacy.of(gdpr, consent))
                .with(UspV1Privacy.of(usPrivacy))
                .build();
    }

    private static void enrichWithErrors(CookieSyncContext cookieSyncContext, List<String> errors) {
        if (cookieSyncContext.isDebug()) {
            cookieSyncContext.getWarnings().addAll(errors);
        }
    }

    public CookieSyncRequest updateCookieSyncRequest(CookieSyncRequest cookieSyncRequest,
                                                     CookieSyncContext cookieSyncContext) {

        final GppContext gppContext = cookieSyncContext.getGppContext();

        final GppContext.Regions regions = gppContext.regions();
        final TcfEuV2Privacy tcfEuV2Privacy = regions.getTcfEuV2Privacy();
        final UspV1Privacy uspV1Privacy = regions.getUspV1Privacy();

        final UpdateResult<Integer> updatedGdpr = updateResult(
                cookieSyncRequest.getGdpr(),
                tcfEuV2Privacy != null ? tcfEuV2Privacy.getGdpr() : null);
        final UpdateResult<String> updatedConsent = updateResult(
                cookieSyncRequest.getGdprConsent(),
                tcfEuV2Privacy != null ? tcfEuV2Privacy.getConsent() : null);
        final UpdateResult<String> updatedUsPrivacy = updateResult(
                cookieSyncRequest.getUsPrivacy(),
                uspV1Privacy != null ? uspV1Privacy.getUsPrivacy() : null);

        return updatedGdpr.isUpdated() || updatedConsent.isUpdated() || updatedUsPrivacy.isUpdated()

                ? cookieSyncRequest.toBuilder()
                .gdpr(updatedGdpr.getValue())
                .gdprConsent(updatedConsent.getValue())
                .usPrivacy(updatedUsPrivacy.getValue())
                .build()

                : cookieSyncRequest;
    }

    private static <T> UpdateResult<T> updateResult(T original, T gpp) {
        return original == null && gpp != null
                ? UpdateResult.updated(gpp)
                : UpdateResult.unaltered(original);
    }
}
