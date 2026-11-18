package org.prebid.server.activity.infrastructure.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.prebid.server.activity.infrastructure.debug.Loggable;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;

public class TestRule extends AbstractMatchRule implements Loggable {

    private final boolean matches;
    private final boolean allowed;

    private TestRule(boolean matches, boolean allowed) {
        this.matches = matches;
        this.allowed = allowed;
    }

    public static TestRule noMatchAndAllow() {
        return new TestRule(false, true);
    }

    public static TestRule matchAndAllow() {
        return new TestRule(true, true);
    }

    public static TestRule noMatchAndDisallow() {
        return new TestRule(false, false);
    }

    public static TestRule matchAndDisallow() {
        return new TestRule(true, false);
    }

    @Override
    public boolean matches(ActivityInvocationPayload activityInvocationPayload) {
        return matches;
    }

    @Override
    public boolean allowed() {
        return allowed;
    }

    @Override
    public JsonNode asLogEntry(ObjectMapper mapper) {
        return TextNode.valueOf(TestRule.class.getSimpleName());
    }
}
