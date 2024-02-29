package org.prebid.server.activity.infrastructure.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.prebid.server.activity.infrastructure.debug.Loggable;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;

import java.util.Objects;
import java.util.function.Predicate;

public class TestRule extends AbstractMatchRule implements Loggable {

    private final Predicate<ActivityInvocationPayload> predicate;
    private final boolean allowed;

    private TestRule(Predicate<ActivityInvocationPayload> predicate, boolean allowed) {
        this.predicate = Objects.requireNonNull(predicate);
        this.allowed = allowed;
    }

    public static TestRule allowIfMatches(Predicate<ActivityInvocationPayload> predicate) {
        return new TestRule(predicate, true);
    }

    public static TestRule disallowIfMatches(Predicate<ActivityInvocationPayload> predicate) {
        return new TestRule(predicate, false);
    }

    @Override
    public boolean matches(ActivityInvocationPayload activityInvocationPayload) {
        return predicate.test(activityInvocationPayload);
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
