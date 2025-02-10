package org.prebid.server.activity.infrastructure.privacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.prebid.server.activity.infrastructure.debug.Loggable;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;

import java.util.Objects;

public class AbstainPrivacyModule implements PrivacyModule, Loggable {

    private final PrivacyModuleQualifier privacyModuleQualifier;

    public AbstainPrivacyModule(PrivacyModuleQualifier privacyModuleQualifier) {
        this.privacyModuleQualifier = Objects.requireNonNull(privacyModuleQualifier);
    }

    @Override
    public Result proceed(ActivityInvocationPayload activityInvocationPayload) {
        return Result.ABSTAIN;
    }

    @Override
    public JsonNode asLogEntry(ObjectMapper mapper) {
        return mapper.createObjectNode()
                .put("privacy_module", privacyModuleQualifier.moduleName())
                .put("skipped", true)
                .put("result", Result.ABSTAIN.name());
    }
}
