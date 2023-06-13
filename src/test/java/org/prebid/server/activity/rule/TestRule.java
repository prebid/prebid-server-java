package org.prebid.server.activity.rule;

import org.prebid.server.activity.ActivityPayload;

import java.util.Objects;
import java.util.function.Predicate;

public class TestRule implements Rule {

    private final Predicate<ActivityPayload> predicate;
    private final boolean allowed;

    private TestRule(Predicate<ActivityPayload> predicate, boolean allowed) {
        this.predicate = Objects.requireNonNull(predicate);
        this.allowed = allowed;
    }

    public static TestRule allowIfMatches(Predicate<ActivityPayload> predicate) {
        return new TestRule(predicate, true);
    }

    public static TestRule disallowIfMatches(Predicate<ActivityPayload> predicate) {
        return new TestRule(predicate, false);
    }

    @Override
    public boolean matches(ActivityPayload activityPayload) {
        return predicate.test(activityPayload);
    }

    @Override
    public boolean allowed() {
        return allowed;
    }
}
