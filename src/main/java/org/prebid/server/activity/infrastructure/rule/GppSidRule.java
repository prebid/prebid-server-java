package org.prebid.server.activity.infrastructure.rule;

import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.ActivityCallPayload;

import java.util.Set;

public final class GppSidRule implements Rule {

    private final Set<ComponentType> componentTypes;
    private final Set<String> componentNames;
    private final boolean sidsMatched;
    private final boolean allowed;

    public GppSidRule(Set<ComponentType> componentTypes,
                      Set<String> componentNames,
                      boolean sidsMatched,
                      boolean allowed) {

        this.componentTypes = componentTypes;
        this.componentNames = componentNames;
        this.sidsMatched = sidsMatched;
        this.allowed = allowed;
    }

    @Override
    public boolean matches(ActivityCallPayload activityCallPayload) {
        return sidsMatched
                && (componentTypes == null || componentTypes.contains(activityCallPayload.getComponentType()))
                && (componentNames == null || componentNames.contains(activityCallPayload.getComponentName()));
    }

    @Override
    public boolean allowed() {
        return allowed;
    }
}
