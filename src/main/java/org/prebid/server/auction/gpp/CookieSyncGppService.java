package org.prebid.server.auction.gpp;

import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextCreator;
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

    public CookieSyncRequest apply(CookieSyncRequest cookieSyncRequest, CookieSyncContext cookieSyncContext) {
        final GppContext gppContext = gppService.processContext(contextFrom(cookieSyncRequest));

        updateCookieSyncContext(cookieSyncContext, gppContext);
        return updateCookieSyncRequest(cookieSyncRequest, gppContext);
    }

    private static GppContext contextFrom(CookieSyncRequest cookieSyncRequest) {
        final String gpp = cookieSyncRequest.getGpp();
        final List<Integer> gppSid = cookieSyncRequest.getGppSid();

        final Integer gdpr = cookieSyncRequest.getGdpr();
        final String consent = cookieSyncRequest.getGdprConsent();

        final String usPrivacy = cookieSyncRequest.getUsPrivacy();

        return GppContextCreator.from(gpp, gppSid)
                .withTcfEuV2(gdpr, consent)
                .withUspV1(usPrivacy)
                .build();
    }

    private static void updateCookieSyncContext(CookieSyncContext cookieSyncContext, GppContext gppContext) {
        // TODO: We need to return any error related to GPP as warning in the response
    }

    private static CookieSyncRequest updateCookieSyncRequest(CookieSyncRequest cookieSyncRequest, GppContext gppContext) {
        final GppContext.Regions regions = gppContext.getRegions();
        final GppContext.Regions.TcfEuV2Privacy tcfEuV2Privacy = regions.getTcfEuV2Privacy();

        final UpdateResult<Integer> updatedGdpr = updateResult(
                cookieSyncRequest.getGdpr(),
                tcfEuV2Privacy.getGdpr());
        final UpdateResult<String> updatedConsent = updateResult(
                cookieSyncRequest.getGdprConsent(),
                tcfEuV2Privacy.getConsent());
        final UpdateResult<String> updatedUsPrivacy = updateResult(
                cookieSyncRequest.getUsPrivacy(),
                regions.getUspV1Privacy().getUsPrivacy());

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
