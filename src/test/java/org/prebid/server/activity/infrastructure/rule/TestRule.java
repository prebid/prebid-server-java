package org.prebid.server.activity.infrastructure.rule;

import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;

import java.util.Objects;
import java.util.function.Predicate;

public class TestRule extends AbstractMatchRule {

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
}
