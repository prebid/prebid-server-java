package org.prebid.server.activity.infrastructure.privacy.usnat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.debug.ActivityDebugUtils;
import org.prebid.server.activity.infrastructure.debug.Loggable;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.USNatDefault;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.USNatSyncUser;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.USNatTransmitGeo;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.USNatTransmitUfpd;
import org.prebid.server.settings.model.activity.privacy.AccountUSNatModuleConfig;

import java.util.Objects;

public class USNatModule implements PrivacyModule, Loggable {

    private final PrivacyModule innerModule;

    public USNatModule(Activity activity, USNatGppReader gppReader, AccountUSNatModuleConfig.Config config) {
        Objects.requireNonNull(activity);
        Objects.requireNonNull(gppReader);

        innerModule = innerModule(activity, gppReader, config);
    }

    private static PrivacyModule innerModule(Activity activity,
                                             USNatGppReader gppReader,
                                             AccountUSNatModuleConfig.Config config) {

        return switch (activity) {
            case SYNC_USER, MODIFY_UFDP -> new USNatSyncUser(gppReader, config);
            case TRANSMIT_UFPD, TRANSMIT_EIDS -> new USNatTransmitUfpd(gppReader, config);
            case TRANSMIT_GEO -> new USNatTransmitGeo(gppReader, config);
            case CALL_BIDDER, TRANSMIT_TID, REPORT_ANALYTICS -> USNatDefault.instance();
        };
    }

    @Override
    public Result proceed(ActivityInvocationPayload activityInvocationPayload) {
        return innerModule.proceed(activityInvocationPayload);
    }

    @Override
    public JsonNode asLogEntry(ObjectMapper mapper) {
        return ActivityDebugUtils.asLogEntry(innerModule, mapper);
    }
}
