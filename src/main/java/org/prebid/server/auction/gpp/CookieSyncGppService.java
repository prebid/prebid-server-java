package org.prebid.server.auction.gpp;

import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextCreator;
import org.prebid.server.cookie.model.CookieSyncContext;
import org.prebid.server.proto.request.CookieSyncRequest;

import java.util.List;

public class CookieSyncGppService {

    private final GppService gppService;

    public CookieSyncGppService(GppService gppService) {
        this.gppService = gppService;
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

        final Integer gdpr = cookieSyncRequest.getGdpr();
        final Integer gppGdpr = tcfEuV2Privacy.getGdpr();

        final String consent = cookieSyncRequest.getGdprConsent();
        final String gppConsent = tcfEuV2Privacy.getConsent();

        final String usPrivacy = cookieSyncRequest.getUsPrivacy();
        final String gppUsPrivacy = regions.getUspV1Privacy().getUsPrivacy();

        return needUpdates(gdpr, gppGdpr) || needUpdates(consent, gppConsent) || needUpdates(usPrivacy, gppUsPrivacy)

                ? cookieSyncRequest.toBuilder()
                .gdpr(ObjectUtils.defaultIfNull(gdpr, gppGdpr))
                .gdprConsent(ObjectUtils.defaultIfNull(consent, gppConsent))
                .usPrivacy(ObjectUtils.defaultIfNull(usPrivacy, gppUsPrivacy))
                .build()

                : cookieSyncRequest;
    }

    private static <T> boolean needUpdates(T original, T gpp) {
        return original == null && gpp != null;
    }
}
