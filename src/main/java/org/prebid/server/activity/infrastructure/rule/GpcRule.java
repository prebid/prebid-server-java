package org.prebid.server.activity.infrastructure.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.prebid.server.activity.infrastructure.debug.Loggable;
import org.prebid.server.activity.infrastructure.payload.CompositeActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.GpcActivityInvocationPayload;

public class GpcRule extends AbstractMatchRule implements Loggable {

    private final String gpc;
    private final boolean isAllowed;

    public GpcRule(String gpc, boolean isAllowed) {
        this.gpc = gpc;
        this.isAllowed = isAllowed;
    }

    @Override
    public boolean matches(CompositeActivityInvocationPayload payload) {
        if (!payload.hasPayload(GpcActivityInvocationPayload.class)) {
            return true;
        }

        final GpcActivityInvocationPayload gpcPayload = payload.get(GpcActivityInvocationPayload.class);
        return gpc == null || gpc.equals(gpcPayload.gpc());
    }

    @Override
    public boolean isAllowed() {
        return isAllowed;
    }

    @Override
    public JsonNode asLogEntry(ObjectMapper mapper) {
        return mapper.valueToTree(new GeoRuleLogEntry(gpc, isAllowed));
    }

    private record GeoRuleLogEntry(String gpc, boolean allow) {
    }
}
