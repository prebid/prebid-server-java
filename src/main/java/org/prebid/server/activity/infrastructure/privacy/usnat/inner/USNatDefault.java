package org.prebid.server.activity.infrastructure.privacy.usnat.inner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.prebid.server.activity.infrastructure.debug.Loggable;
import org.prebid.server.activity.infrastructure.payload.CompositeActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.usnat.debug.USNatModuleLogEntry;

public class USNatDefault implements PrivacyModule, Loggable {

    private static final USNatDefault INSTANCE = new USNatDefault();

    private USNatDefault() {
    }

    public static USNatDefault instance() {
        return INSTANCE;
    }

    @Override
    public Result proceed(CompositeActivityInvocationPayload payload) {
        return Result.ABSTAIN;
    }

    @Override
    public JsonNode asLogEntry(ObjectMapper mapper) {
        return USNatModuleLogEntry.from(this, Result.ABSTAIN);
    }
}
