package org.prebid.server.activity.infrastructure.rule;

import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;

import java.util.Objects;
import java.util.function.Predicate;

public class TestRule implements Rule {

    private final Predicate<ActivityCallPayload> predicate;
    private final boolean allowed;

    private TestRule(Predicate<ActivityCallPayload> predicate, boolean allowed) {
        this.predicate = Objects.requireNonNull(predicate);
        this.allowed = allowed;
    }

    public static TestRule allowIfMatches(Predicate<ActivityCallPayload> predicate) {
        return new TestRule(predicate, true);
    }

    public static TestRule disallowIfMatches(Predicate<ActivityCallPayload> predicate) {
        return new TestRule(predicate, false);
    }

    @Override
    public boolean matches(ActivityCallPayload activityCallPayload) {
        return predicate.test(activityCallPayload);
    }

    @Override
    public boolean allowed() {
        return allowed;
    }
}
