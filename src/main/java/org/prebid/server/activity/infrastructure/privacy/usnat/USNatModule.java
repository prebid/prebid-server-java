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

import java.util.Objects;

public class USNatModule implements PrivacyModule, Loggable {

    private final PrivacyModule innerModule;

    public USNatModule(Activity activity, USNatGppReader gppReader) {
        Objects.requireNonNull(activity);
        Objects.requireNonNull(gppReader);

        innerModule = innerModule(activity, gppReader);
    }

    private static PrivacyModule innerModule(Activity activity, USNatGppReader gppReader) {
        return switch (activity) {
            case SYNC_USER, MODIFY_UFDP -> new USNatSyncUser(gppReader);
            case TRANSMIT_UFPD -> new USNatTransmitUfpd(gppReader);
            case TRANSMIT_GEO -> new USNatTransmitGeo(gppReader);
            case CALL_BIDDER, REPORT_ANALYTICS -> USNatDefault.instance();
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
